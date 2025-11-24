package tidebound.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for HTTP-related operations.
 */
public class HttpUtil {
    
    private static final int BUFFER_SIZE = 8192;
    
    /**
     * Parses query parameters from a URI.
     * 
     * @param uri The URI to parse
     * @return Map of query parameter names to values
     * @throws UnsupportedEncodingException If UTF-8 encoding is not supported
     */
    public static Map<String, String> splitQuery(URI uri) throws UnsupportedEncodingException {
        Map<String, String> queryPairs = new LinkedHashMap<>();
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return queryPairs;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String value = idx < pair.length() - 1 
                    ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") 
                    : "";
                queryPairs.put(key, value);
            }
        }
        return queryPairs;
    }
    
    /**
     * Reads all bytes from an input stream and writes them to an output stream.
     * 
     * @param source Input stream to read from
     * @param sink Output stream to write to
     * @return Number of bytes copied
     * @throws IOException If an I/O error occurs
     */
    public static long copy(InputStream source, OutputStream sink) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }
    
    /**
     * Gets the buffer size used for reading and writing streams.
     * 
     * @return Buffer size in bytes
     */
    public static int getBufferSize() {
        return BUFFER_SIZE;
    }
}

