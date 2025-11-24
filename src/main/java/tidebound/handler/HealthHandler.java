package tidebound.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler for health check endpoint.
 */
public class HealthHandler implements HttpHandler {
    
    private static final byte[] OK_RESPONSE = "ok".getBytes(StandardCharsets.UTF_8);
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, OK_RESPONSE.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(OK_RESPONSE);
        }
    }
}

