package tidebound.util;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpUtilTest {

    @Test
    void testSplitQuery_WithValidQuery() throws Exception {
        URI uri = new URI("http://example.com/path?key1=value1&key2=value2&key3=value%20with%20spaces");
        Map<String, String> result = HttpUtil.splitQuery(uri);
        
        assertEquals(3, result.size());
        assertEquals("value1", result.get("key1"));
        assertEquals("value2", result.get("key2"));
        assertEquals("value with spaces", result.get("key3"));
    }

    @Test
    void testSplitQuery_WithEmptyQuery() throws Exception {
        URI uri = new URI("http://example.com/path");
        Map<String, String> result = HttpUtil.splitQuery(uri);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testSplitQuery_WithNullQuery() throws Exception {
        URI uri = new URI("http://example.com/path?");
        Map<String, String> result = HttpUtil.splitQuery(uri);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testSplitQuery_WithSingleParameter() throws Exception {
        URI uri = new URI("http://example.com/path?replay_url=s3://bucket/key");
        Map<String, String> result = HttpUtil.splitQuery(uri);
        
        assertEquals(1, result.size());
        assertEquals("s3://bucket/key", result.get("replay_url"));
    }

    @Test
    void testSplitQuery_WithEmptyValue() throws Exception {
        URI uri = new URI("http://example.com/path?key1=&key2=value");
        Map<String, String> result = HttpUtil.splitQuery(uri);
        
        assertEquals(2, result.size());
        assertEquals("", result.get("key1"));
        assertEquals("value", result.get("key2"));
    }

    @Test
    void testSplitQuery_WithSpecialCharacters() throws Exception {
        URI uri = new URI("http://example.com/path?url=https%3A%2F%2Fexample.com%2Ffile.dem");
        Map<String, String> result = HttpUtil.splitQuery(uri);
        
        assertEquals(1, result.size());
        assertEquals("https://example.com/file.dem", result.get("url"));
    }

    @Test
    void testCopy_WithValidStreams() throws IOException {
        String testData = "Hello, World! This is test data.";
        ByteArrayInputStream source = new ByteArrayInputStream(testData.getBytes());
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        
        long bytesCopied = HttpUtil.copy(source, sink);
        
        assertEquals(testData.length(), bytesCopied);
        assertEquals(testData, sink.toString());
    }

    @Test
    void testCopy_WithEmptyStream() throws IOException {
        ByteArrayInputStream source = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        
        long bytesCopied = HttpUtil.copy(source, sink);
        
        assertEquals(0, bytesCopied);
        assertEquals(0, sink.size());
    }

    @Test
    void testCopy_WithLargeData() throws IOException {
        // Create data larger than buffer size
        byte[] largeData = new byte[HttpUtil.getBufferSize() * 3];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        
        ByteArrayInputStream source = new ByteArrayInputStream(largeData);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        
        long bytesCopied = HttpUtil.copy(source, sink);
        
        assertEquals(largeData.length, bytesCopied);
        assertArrayEquals(largeData, sink.toByteArray());
    }

    @Test
    void testCopy_ThrowsIOException_WhenSourceIsNull() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        
        assertThrows(NullPointerException.class, () -> {
            HttpUtil.copy(null, sink);
        });
    }

    @Test
    void testCopy_ThrowsIOException_WhenSinkIsNull() {
        ByteArrayInputStream source = new ByteArrayInputStream("test".getBytes());
        
        assertThrows(NullPointerException.class, () -> {
            HttpUtil.copy(source, null);
        });
    }

    @Test
    void testGetBufferSize() {
        int bufferSize = HttpUtil.getBufferSize();
        assertEquals(8192, bufferSize);
    }
}

