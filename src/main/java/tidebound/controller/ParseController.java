package tidebound.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import tidebound.Parse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class ParseController {

    @PostMapping(
            path = "/",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> parse(HttpServletRequest request) {
        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream inputStream = request.getInputStream()) {
                new Parse(inputStream, outputStream);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to parse replay", ex);
            }
        };

        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }
}

