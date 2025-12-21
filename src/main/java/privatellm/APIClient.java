package privatellm;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client for communicating with remote crlinkingbot API
 */
public class APIClient {
    private static final int CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds
    
    private final String baseUrl;
    private final String apiSecret;
    
    public APIClient(String baseUrl, String apiSecret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiSecret = apiSecret;
    }
    
    /**
     * Perform HTTP GET request
     */
    public String get(String endpoint) throws IOException {
        String urlString = baseUrl + endpoint;
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        addAuthHeader(conn);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errorBody = readErrorStream(conn);
            throw new IOException("GET request failed with code " + responseCode + 
                (errorBody.isEmpty() ? "" : ": " + errorBody));
        }
        
        return readResponseBody(conn);
    }
    
    /**
     * Perform HTTP POST request with JSON body
     */
    public String post(String endpoint, JSONObject body) throws IOException {
        String urlString = baseUrl + endpoint;
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        addAuthHeader(conn);
        
        // Send request body
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errorBody = readErrorStream(conn);
            throw new IOException("POST request failed with code " + responseCode + 
                (errorBody.isEmpty() ? "" : ": " + errorBody));
        }
        
        return readResponseBody(conn);
    }
    
    /**
     * Add Authorization header with Bearer token
     */
    private void addAuthHeader(HttpURLConnection conn) {
        if (apiSecret != null && !apiSecret.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiSecret);
        }
    }
    
    /**
     * Read response body from connection
     */
    private String readResponseBody(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            return response.toString();
        }
    }
    
    /**
     * Read error stream from connection
     */
    private String readErrorStream(HttpURLConnection conn) {
        try {
            if (conn.getErrorStream() == null) {
                return "";
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    error.append(line.trim());
                }
                return error.toString();
            }
        } catch (Exception e) {
            return "";
        }
    }
}
