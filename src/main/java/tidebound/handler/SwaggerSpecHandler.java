package tidebound.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import tidebound.util.HttpUtil;

/**
 * Handler for Swagger OpenAPI specification endpoint.
 */
public class SwaggerSpecHandler implements HttpHandler {
    
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
        try (InputStream specStream = SwaggerSpecHandler.class.getResourceAsStream(SPEC_RESOURCE)) {
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
        byte[] data = new byte[HttpUtil.getBufferSize()];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}

