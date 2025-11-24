package tidebound.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler for Swagger UI endpoint.
 */
public class SwaggerUIHandler implements HttpHandler {
    
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

