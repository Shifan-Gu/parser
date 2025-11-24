package tidebound.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import tidebound.Parse;

/**
 * Handler for parsing replay files.
 */
public class ParseHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, 0);
        try (InputStream inputStream = exchange.getRequestBody();
             OutputStream outputStream = exchange.getResponseBody()) {
            new Parse(inputStream, outputStream);
        } catch (Exception e) {
            System.err.println("Error parsing replay: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

