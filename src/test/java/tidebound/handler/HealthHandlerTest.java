package tidebound.handler;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthHandlerTest {

    @Mock
    private HttpExchange exchange;

    @Test
    void testHandle_ReturnsOkResponse() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(outputStream);
        
        HealthHandler handler = new HealthHandler();
        handler.handle(exchange);
        
        verify(exchange).sendResponseHeaders(200, 2);
        assertEquals("ok", outputStream.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testHandle_ClosesOutputStream() throws IOException {
        OutputStream outputStream = mock(OutputStream.class);
        when(exchange.getResponseBody()).thenReturn(outputStream);
        
        HealthHandler handler = new HealthHandler();
        handler.handle(exchange);
        
        verify(exchange).sendResponseHeaders(200, 2);
        verify(outputStream).write(any(byte[].class));
        verify(outputStream).close();
    }

    @Test
    void testHandle_ResponseLengthIsCorrect() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(outputStream);
        
        HealthHandler handler = new HealthHandler();
        handler.handle(exchange);
        
        verify(exchange).sendResponseHeaders(eq(200), eq(2L));
        assertEquals(2, outputStream.size());
    }

    @Test
    void testHandle_ResponseContentIsUtf8() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(outputStream);
        
        HealthHandler handler = new HealthHandler();
        handler.handle(exchange);
        
        String response = outputStream.toString(StandardCharsets.UTF_8);
        assertEquals("ok", response);
    }
}

