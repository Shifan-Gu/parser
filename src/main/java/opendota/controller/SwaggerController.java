package opendota.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/swagger")
public class SwaggerController {

    private static final byte[] PAGE_CONTENT = (
            "<!DOCTYPE html>"
                    + "<html lang=\"en\">"
                    + "<head>"
                    + "<meta charset=\"UTF-8\"/>"
                    + "<title>Parser API Docs</title>"
                    + "<link rel=\"stylesheet\" href=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui.css\" />"
                    + "<style>body{margin:0;background-color:#fafafa;} #swagger-ui{box-sizing:border-box;}</style>"
                    + "</head>"
                    + "<body>"
                    + "<div id=\"swagger-ui\"></div>"
                    + "<script src=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js\"></script>"
                    + "<script>"
                    + "window.onload=function(){SwaggerUIBundle({url:'"
                    + "/swagger/openapi.json"
                    + "',dom_id:'#swagger-ui'});};"
                    + "</script>"
                    + "</body>"
                    + "</html>"
    ).getBytes(StandardCharsets.UTF_8);

    private final Resource openApiSpec;

    public SwaggerController(ResourceLoader resourceLoader) {
        this.openApiSpec = resourceLoader.getResource("classpath:swagger/openapi.json");
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> page() {
        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_HTML)
                .body(PAGE_CONTENT);
    }

    @GetMapping(path = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> spec() {
        if (!openApiSpec.exists()) {
            return ResponseEntity.internalServerError().build();
        }
        try {
            return ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(openApiSpec.contentLength())
                    .body(openApiSpec);
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

