package opendota;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
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

class RegisterTask extends TimerTask 
{ 
   public void run()
   {
        if (System.getenv().containsKey("SERVICE_REGISTRY_HOST")) {
            try {
                String ip = "";
                if (System.getenv().containsKey("EXTERNAL")) {
                    // If configured as external, request external IP and report it
                    ip = RegisterTask.shellExec("curl " + System.getenv().get("SERVICE_REGISTRY_HOST") + "/ip");
                } else {
                    // Otherwise, use hostname -i to get internal IP
                    ip = RegisterTask.shellExec("hostname -i");
                }
                long nproc = Math.round(Runtime.getRuntime().availableProcessors() * 1.5);
                String postCmd = "curl -X POST --max-time 60 -L " + System.getenv().get("SERVICE_REGISTRY_HOST") + "/register/parser/" + ip + ":5600" + "?size=" + nproc + "&key=" + System.getenv().get("RETRIEVER_SECRET");
                System.err.println(postCmd);
                RegisterTask.shellExec(postCmd);
            } catch (Exception e) {
                System.err.println(e);
            }
        }
   }

   public static String shellExec(String cmdCommand) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        final Process process = Runtime.getRuntime().exec(cmdCommand);
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
        }
        return stringBuilder.toString();
    }
} 

