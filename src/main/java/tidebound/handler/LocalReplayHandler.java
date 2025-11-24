package tidebound.handler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import tidebound.util.HttpUtil;

/**
 * Handler for processing local replay files.
 */
public class LocalReplayHandler implements HttpHandler {
    
    private static final int SERVER_PORT = 5600;
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = HttpUtil.splitQuery(exchange.getRequestURI());
            String filePath = query.get("file_path");
            
            if (filePath == null || filePath.isEmpty()) {
                System.err.println("Error: file_path parameter is required");
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
                return;
            }
            
            handleLocalReplay(exchange, filePath);
        } catch (InterruptedException e) {
            System.err.println("Interrupted while processing local replay: " + e.getMessage());
            e.printStackTrace();
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        } catch (Exception e) {
            System.err.println("Error processing local replay: " + e.getMessage());
            e.printStackTrace();
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        }
    }

    private void handleLocalReplay(HttpExchange exchange, String filePath) throws IOException, InterruptedException {
        System.err.println("Processing local replay: " + filePath);
        
        File replayFile = new File(filePath);
        
        // Validate file exists and is readable
        if (!replayFile.exists()) {
            System.err.println("Error: File not found: " + filePath);
            exchange.sendResponseHeaders(404, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        if (!replayFile.canRead()) {
            System.err.println("Error: File not readable: " + filePath);
            exchange.sendResponseHeaders(403, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        try (FileInputStream fileStream = new FileInputStream(replayFile)) {
            // Determine if we need to decompress
            boolean isBz2 = filePath.endsWith(".bz2");
            String decompressCmd = isBz2 ? "bunzip2" : "cat";
            
            // Create the processing pipeline: decompress | parse | aggregate
            String cmd = String.format("%s | curl -X POST -T - localhost:%d | node processors/createParsedDataBlob.mjs", 
                decompressCmd, SERVER_PORT);
            System.err.println("Local processing command: " + cmd);
            
            Process proc = new ProcessBuilder("bash", "-c", cmd).start();
            
            // Pipe file stream to the process
            try (OutputStream procInput = proc.getOutputStream()) {
                HttpUtil.copy(fileStream, procInput);
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
            System.err.println("Local file read error: " + e.getMessage());
            e.printStackTrace();
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        }
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
        if (errorStr.contains("bunzip2: Data integrity error when decompressing")) {
            System.err.println("Corrupted replay file");
            return 200;
        }
        if (errorStr.contains("bunzip2: Compressed file ends unexpectedly")) {
            System.err.println("Corrupted replay file");
            return 200;
        }
        if (errorStr.contains("bunzip2: (stdin) is not a bzip2 file.")) {
            System.err.println("Tried to decompress a non-bz2 file");
            return 200;
        }
        if (errorStr.contains("curl: (28) Operation timed out")) {
            System.err.println("Parse operation timed out");
            return 200;
        }
        // Default to 500 for unexpected errors
        return 500;
    }
}

