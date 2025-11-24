package tidebound.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import tidebound.S3Service;
import tidebound.util.HttpUtil;

/**
 * Handler for processing replay files from URLs (HTTP or S3).
 */
public class BlobHandler implements HttpHandler {
    
    private static final S3Service s3Service = new S3Service();
    private static final int SERVER_PORT = 5600;
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = HttpUtil.splitQuery(exchange.getRequestURI());
            String replayUrl = query.get("replay_url");
            
            if (replayUrl == null || replayUrl.isEmpty()) {
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
                return;
            }
            
            // Check if it's an S3 URL
            if (S3Service.isS3Url(replayUrl)) {
                handleS3Replay(exchange, replayUrl);
            } else {
                handleHttpReplay(exchange, replayUrl);
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while processing replay: " + e.getMessage());
            e.printStackTrace();
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        } catch (Exception e) {
            System.err.println("Error processing replay: " + e.getMessage());
            e.printStackTrace();
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        }
    }

    private void handleS3Replay(HttpExchange exchange, String s3Url) throws IOException, InterruptedException {
        System.err.println("Processing S3 replay: " + s3Url);
        
        try {
            // Download from S3
            InputStream s3Stream = s3Service.downloadFromS3(s3Url);
            
            // Determine if we need to decompress
            boolean isBz2 = s3Url.endsWith(".bz2");
            String decompressCmd = isBz2 ? "bunzip2" : "cat";
            
            // Create the processing pipeline: decompress | parse | aggregate
            String cmd = String.format("%s | curl -X POST -T - localhost:%d | node processors/createParsedDataBlob.mjs", 
                decompressCmd, SERVER_PORT);
            System.err.println("S3 processing command: " + cmd);
            
            Process proc = new ProcessBuilder("bash", "-c", cmd).start();
            
            // Pipe S3 stream to the process
            try (OutputStream procInput = proc.getOutputStream();
                 InputStream s3StreamRef = s3Stream) {
                HttpUtil.copy(s3StreamRef, procInput);
            }
            
            // Collect output and errors
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream error = new ByteArrayOutputStream();
            HttpUtil.copy(proc.getInputStream(), output);
            HttpUtil.copy(proc.getErrorStream(), error);
            System.err.println(error.toString());
            
            int exitCode = proc.waitFor();
            handleProcessResult(exchange, exitCode, output, error);
            
        } catch (IOException e) {
            System.err.println("S3 download error: " + e.getMessage());
            e.printStackTrace();
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        }
    }

    private void handleHttpReplay(HttpExchange exchange, String replayUrl) throws IOException, InterruptedException {
        System.err.println("Processing HTTP replay: " + replayUrl);
        URL url = new URL(replayUrl);
        String cmd = String.format("curl --max-time 145 --fail -L %s | %s | curl -X POST -T - localhost:%d | node processors/createParsedDataBlob.mjs",
            url, 
            url.toString().endsWith(".bz2") ? "bunzip2" : "cat",
            SERVER_PORT
        );
        System.err.println(cmd);
        
        // Download, unzip, parse, aggregate
        Process proc = new ProcessBuilder("bash", "-c", cmd).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        HttpUtil.copy(proc.getInputStream(), output);
        HttpUtil.copy(proc.getErrorStream(), error);
        System.err.println(error.toString());
        
        int exitCode = proc.waitFor();
        handleProcessResult(exchange, exitCode, output, error);
    }

    private void handleProcessResult(HttpExchange exchange, int exitCode, ByteArrayOutputStream output, 
                                     ByteArrayOutputStream error) throws IOException {
        if (exitCode != 0) {
            int status = determineErrorStatus(error.toString());
            exchange.sendResponseHeaders(status, 0);
            exchange.getResponseBody().close();
        } else {
            exchange.sendResponseHeaders(200, output.size());
            output.writeTo(exchange.getResponseBody());
            exchange.getResponseBody().close();
        }
    }
    
    private int determineErrorStatus(String errorStr) {
        // Handle various error conditions that should return 200 (expected errors)
        if (errorStr.contains("curl: (28) Operation timed out")) {
            // Parse took too long, maybe China replay?
            return 200;
        }
        if (errorStr.contains("curl: (22) The requested URL returned error: 502")) {
            // Google-Edge-Cache: origin retries exhausted Error: 2010
            // Server error, don't retry
            return 200;
        }
        if (errorStr.contains("bunzip2: Data integrity error when decompressing")) {
            // Corrupted replay, don't retry
            return 200;
        }
        if (errorStr.contains("bunzip2: Compressed file ends unexpectedly")) {
            // Corrupted replay, don't retry
            return 200;
        }
        if (errorStr.contains("bunzip2: (stdin) is not a bzip2 file.")) {
            // Tried to unzip a non-bz2 file
            return 200;
        }
        if (errorStr.contains("S3 download error")) {
            // S3 download error
            System.err.println("S3 download failed");
        }
        // Default to 500 for unexpected errors
        return 500;
    }
}

