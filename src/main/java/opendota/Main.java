package opendota;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
    
public class Main {
    
    // Initialize S3Service once for reuse
    private static final S3Service s3Service = new S3Service();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.valueOf("5600")), 0);
        server.createContext("/", new MyHandler());
        server.createContext("/healthz", new HealthHandler());
        server.createContext("/blob", new BlobHandler());
        server.createContext("/local", new LocalReplayHandler());
        server.createContext("/swagger/openapi.json", new SwaggerSpecHandler());
        server.createContext("/swagger", new SwaggerUIHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        // Re-register ourselves
        Timer timer = new Timer(); 
        TimerTask task = new RegisterTask(); 
        timer.schedule(task, 0, 5000);
    }
    
    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.sendResponseHeaders(200, 0);
            InputStream is = t.getRequestBody();
            OutputStream os = t.getResponseBody();
            try {
            	new Parse(is, os);
            }
            catch (Exception e)
            {
            	e.printStackTrace();
            }
            os.close();
        }
    }

    static class SwaggerUIHandler implements HttpHandler {
        private static final byte[] PAGE_CONTENT = (
            "<!DOCTYPE html>" +
            "<html lang=\"en\">" +
            "<head>" +
            "<meta charset=\"UTF-8\"/>" +
            "<title>Parser API Docs</title>" +
            "<link rel=\"stylesheet\" href=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui.css\" />" +
            "<style>body{margin:0;background-color:#fafafa;} #swagger-ui{box-sizing:border-box;}</style>" +
            "</head>" +
            "<body>" +
            "<div id=\"swagger-ui\"></div>" +
            "<script src=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js\"></script>" +
            "<script>" +
            "window.onload=function(){SwaggerUIBundle({url:'" + "/swagger/openapi.json" + "',dom_id:'#swagger-ui'});};" +
            "</script>" +
            "</body>" +
            "</html>"
        ).getBytes(StandardCharsets.UTF_8);

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, PAGE_CONTENT.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(PAGE_CONTENT);
            }
        }
    }

    static class SwaggerSpecHandler implements HttpHandler {
        private static final String SPEC_RESOURCE = "/swagger/openapi.json";

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            try (InputStream specStream = Main.class.getResourceAsStream(SPEC_RESOURCE)) {
                if (specStream == null) {
                    exchange.sendResponseHeaders(500, -1);
                    return;
                }
                byte[] specBytes = readAllBytes(specStream);
                exchange.sendResponseHeaders(200, specBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(specBytes);
                }
            }
        }

        private byte[] readAllBytes(InputStream inputStream) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[BUFFER_SIZE];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.sendResponseHeaders(200, 2);
            OutputStream os = t.getResponseBody();
            os.write("ok".getBytes());
            os.close();
        }
    }

    static class BlobHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                Map<String, String> query = splitQuery(t.getRequestURI());
                String replayUrl = query.get("replay_url");
                
                // Check if it's an S3 URL
                if (S3Service.isS3Url(replayUrl)) {
                    handleS3Replay(t, replayUrl);
                } else {
                    handleHttpReplay(t, replayUrl);
                }
            } 
            catch(InterruptedException e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            } catch(Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            }
        }

        private void handleS3Replay(HttpExchange t, String s3Url) throws IOException, InterruptedException {
            System.err.println("Processing S3 replay: " + s3Url);
            
            try {
                // Download from S3
                InputStream s3Stream = s3Service.downloadFromS3(s3Url);
                
                // Determine if we need to decompress
                boolean isBz2 = s3Url.endsWith(".bz2");
                String decompressCmd = isBz2 ? "bunzip2" : "cat";
                
                // Create the processing pipeline: decompress | parse | aggregate
                String cmd = String.format("%s | curl -X POST -T - localhost:5600 | node processors/createParsedDataBlob.mjs", decompressCmd);
                System.err.println("S3 processing command: " + cmd);
                
                Process proc = new ProcessBuilder(new String[] {"bash", "-c", cmd}).start();
                
                // Pipe S3 stream to the process
                OutputStream procInput = proc.getOutputStream();
                copy(s3Stream, procInput);
                procInput.close();
                s3Stream.close();
                
                // Collect output and errors
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ByteArrayOutputStream error = new ByteArrayOutputStream();
                copy(proc.getInputStream(), output);
                copy(proc.getErrorStream(), error);
                System.err.println(error.toString());
                
                int exitCode = proc.waitFor();
                handleProcessResult(t, exitCode, output, error);
                
            } catch (IOException e) {
                System.err.println("S3 download error: " + e.getMessage());
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            }
        }

        private void handleHttpReplay(HttpExchange t, String replayUrl) throws IOException, InterruptedException {
            System.err.println("Processing HTTP replay: " + replayUrl);
            URL url = new URL(replayUrl);
            String cmd = String.format("curl --max-time 145 --fail -L %s | %s | curl -X POST -T - localhost:5600 | node processors/createParsedDataBlob.mjs",
                url, 
                url.toString().endsWith(".bz2") ? "bunzip2" : "cat"
            );
            System.err.println(cmd);
            
            // Download, unzip, parse, aggregate
            Process proc = new ProcessBuilder(new String[] {"bash", "-c", cmd}).start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream error = new ByteArrayOutputStream();
            copy(proc.getInputStream(), output);
            copy(proc.getErrorStream(), error);
            System.err.println(error.toString());
            
            int exitCode = proc.waitFor();
            handleProcessResult(t, exitCode, output, error);
        }

        private void handleProcessResult(HttpExchange t, int exitCode, ByteArrayOutputStream output, ByteArrayOutputStream error) throws IOException {
            if (exitCode != 0) {
                // We can send 200 status here and no response if expected error (read the error string)
                // Maybe we can pass the specific error info in the response headers
                int status = 500;
                String errorStr = error.toString();
                if (errorStr.contains("curl: (28) Operation timed out")) {
                    // Parse took too long, maybe China replay?
                    status = 200;
                }
                if (errorStr.contains("curl: (22) The requested URL returned error: 502")) {
                    // Google-Edge-Cache: origin retries exhausted Error: 2010
                    // Server error, don't retry
                    status = 200;
                }
                if (errorStr.contains("bunzip2: Data integrity error when decompressing")) {
                    // Corrupted replay, don't retry
                    status = 200;
                }
                if (errorStr.contains("bunzip2: Compressed file ends unexpectedly")) {
                    // Corrupted replay, don't retry
                    status = 200;
                }
                if (errorStr.contains("bunzip2: (stdin) is not a bzip2 file.")) {
                    // Tried to unzip a non-bz2 file
                    status = 200;
                }
                if (errorStr.contains("S3 download error")) {
                    // S3 download error
                    System.err.println("S3 download failed");
                }
                t.sendResponseHeaders(status, 0);
                t.getResponseBody().close();
            } else {
                t.sendResponseHeaders(200, output.size());
                output.writeTo(t.getResponseBody());
                t.getResponseBody().close();
            }
        }
    }

    static class LocalReplayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                Map<String, String> query = splitQuery(t.getRequestURI());
                String filePath = query.get("file_path");
                
                if (filePath == null || filePath.isEmpty()) {
                    System.err.println("Error: file_path parameter is required");
                    t.sendResponseHeaders(400, 0);
                    t.getResponseBody().close();
                    return;
                }
                
                handleLocalReplay(t, filePath);
            } catch(InterruptedException e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            } catch(Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            }
        }

        private void handleLocalReplay(HttpExchange t, String filePath) throws IOException, InterruptedException {
            System.err.println("Processing local replay: " + filePath);
            
            File replayFile = new File(filePath);
            
            // Validate file exists and is readable
            if (!replayFile.exists()) {
                System.err.println("Error: File not found: " + filePath);
                t.sendResponseHeaders(404, 0);
                t.getResponseBody().close();
                return;
            }
            
            if (!replayFile.canRead()) {
                System.err.println("Error: File not readable: " + filePath);
                t.sendResponseHeaders(403, 0);
                t.getResponseBody().close();
                return;
            }
            
            try (FileInputStream fileStream = new FileInputStream(replayFile)) {
                // Determine if we need to decompress
                boolean isBz2 = filePath.endsWith(".bz2");
                String decompressCmd = isBz2 ? "bunzip2" : "cat";
                
                // Create the processing pipeline: decompress | parse | aggregate
                String cmd = String.format("%s | curl -X POST -T - localhost:5600 | node processors/createParsedDataBlob.mjs", decompressCmd);
                System.err.println("Local processing command: " + cmd);
                
                Process proc = new ProcessBuilder(new String[] {"bash", "-c", cmd}).start();
                
                // Pipe file stream to the process
                OutputStream procInput = proc.getOutputStream();
                copy(fileStream, procInput);
                procInput.close();
                
                // Collect output and errors
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ByteArrayOutputStream error = new ByteArrayOutputStream();
                copy(proc.getInputStream(), output);
                copy(proc.getErrorStream(), error);
                System.err.println(error.toString());
                
                int exitCode = proc.waitFor();
                handleProcessResult(t, exitCode, output, error);
                
            } catch (IOException e) {
                System.err.println("Local file read error: " + e.getMessage());
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            }
        }

        private void handleProcessResult(HttpExchange t, int exitCode, ByteArrayOutputStream output, ByteArrayOutputStream error) throws IOException {
            if (exitCode != 0) {
                int status = 500;
                String errorStr = error.toString();
                
                // Handle various error conditions
                if (errorStr.contains("bunzip2: Data integrity error when decompressing")) {
                    System.err.println("Corrupted replay file");
                    status = 200;
                }
                if (errorStr.contains("bunzip2: Compressed file ends unexpectedly")) {
                    System.err.println("Corrupted replay file");
                    status = 200;
                }
                if (errorStr.contains("bunzip2: (stdin) is not a bzip2 file.")) {
                    System.err.println("Tried to decompress a non-bz2 file");
                    status = 200;
                }
                if (errorStr.contains("curl: (28) Operation timed out")) {
                    System.err.println("Parse operation timed out");
                    status = 200;
                }
                
                t.sendResponseHeaders(status, 0);
                t.getResponseBody().close();
            } else {
                t.sendResponseHeaders(200, output.size());
                output.writeTo(t.getResponseBody());
                t.getResponseBody().close();
            }
        }
    }

    public static Map<String, String> splitQuery(URI uri) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String query = uri.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return query_pairs;
    }

    // buffer size used for reading and writing
    private static final int BUFFER_SIZE = 8192;

    /**
     * Reads all bytes from an input stream and writes them to an output stream.
    */
    private static long copy(InputStream source, OutputStream sink) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }
}