package privatellm;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.Executors;

/**
 * LLM Proxy Server for LM Studio Integration
 * Converts Gemini API format to OpenAI/LM Studio format
 */
public class LLMProxyServer {
    private static final int PORT = 8080;
    private static final int CONNECT_TIMEOUT = 30000; // 30 seconds
    private static final int READ_TIMEOUT = 60000; // 60 seconds
    
    private static String lmStudioEndpoint;
    private static String apiSecret;

    public static void main(String[] args) throws IOException {
        // Load environment variables
        lmStudioEndpoint = System.getenv("LM_STUDIO_ENDPOINT");
        apiSecret = System.getenv("API_SECRET");

        // Validate required configuration
        if (lmStudioEndpoint == null || lmStudioEndpoint.isEmpty()) {
            System.err.println("ERROR: LM_STUDIO_ENDPOINT environment variable is required");
            System.exit(1);
        }

        System.out.println("=================================================");
        System.out.println("LLM Proxy Server for LM Studio Integration");
        System.out.println("=================================================");
        System.out.println("LM Studio Endpoint: " + lmStudioEndpoint);
        System.out.println("Authentication: " + (apiSecret != null && !apiSecret.isEmpty() ? "Enabled" : "Disabled"));
        System.out.println("Port: " + PORT);
        System.out.println("=================================================");

        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Set up endpoints
        server.createContext("/health", new HealthCheckHandler());
        server.createContext("/api/generate", new GenerateHandler());
        
        // Use a thread pool executor for handling concurrent requests
        server.setExecutor(Executors.newFixedThreadPool(10));
        
        // Start the server
        server.start();
        System.out.println("Server started successfully on port " + PORT);
        System.out.println("Health check: http://localhost:" + PORT + "/health");
        System.out.println("API endpoint: http://localhost:" + PORT + "/api/generate");
    }

    /**
     * Handler for the health check endpoint
     */
    static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("[" + Instant.now() + "] Health check request from " + exchange.getRemoteAddress());

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, createErrorResponse("Method not allowed. Use GET."));
                return;
            }

            JSONObject response = new JSONObject();
            response.put("status", "healthy");
            response.put("lm_studio_endpoint", lmStudioEndpoint);
            response.put("timestamp", Instant.now().getEpochSecond());

            sendJsonResponse(exchange, 200, response.toString());
        }
    }

    /**
     * Handler for the API generate endpoint
     */
    static class GenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("[" + Instant.now() + "] Generate request from " + exchange.getRemoteAddress());

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, createErrorResponse("Method not allowed. Use POST."));
                return;
            }

            // Check authentication if API secret is configured
            if (apiSecret != null && !apiSecret.isEmpty()) {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (!isValidAuthentication(authHeader, apiSecret)) {
                    System.err.println("Authentication failed");
                    sendJsonResponse(exchange, 401, createErrorResponse("Unauthorized. Invalid or missing API secret."));
                    return;
                }
            }

            try {
                // Read request body
                String requestBody = readRequestBody(exchange);
                System.out.println("Received Gemini request: " + requestBody);

                // Convert Gemini format to OpenAI format
                String openAIRequest = convertGeminiToOpenAI(requestBody);
                System.out.println("Converted to OpenAI request: " + openAIRequest);

                // Forward to LM Studio
                String lmStudioResponse = forwardToLMStudio(openAIRequest);
                System.out.println("Received LM Studio response: " + lmStudioResponse);

                // Convert OpenAI response to Gemini format
                String geminiResponse = convertOpenAIToGemini(lmStudioResponse);
                System.out.println("Converted to Gemini response: " + geminiResponse);

                // Send response
                sendJsonResponse(exchange, 200, geminiResponse);

            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());
                e.printStackTrace();
                sendJsonResponse(exchange, 500, createErrorResponse("Internal server error: " + e.getMessage()));
            }
        }

        /**
         * Convert Gemini API format to OpenAI format
         */
        private String convertGeminiToOpenAI(String geminiRequest) {
            JSONObject geminiJson = new JSONObject(geminiRequest);
            JSONObject openAIJson = new JSONObject();

            // Extract text from Gemini contents array
            StringBuilder fullText = new StringBuilder();
            if (geminiJson.has("contents")) {
                JSONArray contents = geminiJson.getJSONArray("contents");
                for (int i = 0; i < contents.length(); i++) {
                    JSONObject content = contents.getJSONObject(i);
                    if (content.has("parts")) {
                        JSONArray parts = content.getJSONArray("parts");
                        for (int j = 0; j < parts.length(); j++) {
                            JSONObject part = parts.getJSONObject(j);
                            if (part.has("text")) {
                                if (fullText.length() > 0) {
                                    fullText.append("\n");
                                }
                                fullText.append(part.getString("text"));
                            }
                        }
                    }
                }
            }

            // Create OpenAI messages array
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", fullText.toString());
            messages.put(message);

            openAIJson.put("messages", messages);
            openAIJson.put("temperature", 0.7);
            openAIJson.put("max_tokens", 2000);
            openAIJson.put("stream", false);

            return openAIJson.toString();
        }

        /**
         * Convert OpenAI response to Gemini format
         */
        private String convertOpenAIToGemini(String openAIResponse) {
            JSONObject openAIJson = new JSONObject(openAIResponse);
            JSONObject geminiJson = new JSONObject();

            // Extract content from OpenAI response
            String responseText = "";
            if (openAIJson.has("choices")) {
                JSONArray choices = openAIJson.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    if (choice.has("message")) {
                        JSONObject message = choice.getJSONObject("message");
                        if (message.has("content")) {
                            responseText = message.getString("content");
                        }
                    }
                }
            }

            // Create Gemini candidates array
            JSONArray candidates = new JSONArray();
            JSONObject candidate = new JSONObject();
            
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", responseText);
            parts.put(part);
            content.put("parts", parts);
            
            candidate.put("content", content);
            candidate.put("finishReason", "STOP");
            
            candidates.put(candidate);
            geminiJson.put("candidates", candidates);

            return geminiJson.toString();
        }

        /**
         * Forward request to LM Studio endpoint
         */
        private String forwardToLMStudio(String requestBody) throws IOException {
            URL url = new URL(lmStudioEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                // Read error stream for debugging
                String errorBody = "";
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder error = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        error.append(line.trim());
                    }
                    errorBody = error.toString();
                } catch (Exception e) {
                    // Ignore if error stream cannot be read
                }
                throw new IOException("LM Studio returned error code: " + responseCode + 
                    (errorBody.isEmpty() ? "" : ", body: " + errorBody));
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        }
    }

    /**
     * Read the request body from the HTTP exchange
     */
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
    }

    /**
     * Send a JSON response
     */
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] response = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    /**
     * Create a JSON error response
     */
    private static String createErrorResponse(String message) {
        JSONObject error = new JSONObject();
        error.put("error", message);
        return error.toString();
    }

    /**
     * Validates authentication using constant-time comparison to prevent timing attacks
     */
    private static boolean isValidAuthentication(String authHeader, String expectedSecret) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        
        String providedSecret = authHeader.substring(7); // Remove "Bearer " prefix
        
        // Use constant-time comparison to prevent timing attacks
        try {
            byte[] providedBytes = providedSecret.getBytes(StandardCharsets.UTF_8);
            byte[] expectedBytes = expectedSecret.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(providedBytes, expectedBytes);
        } catch (Exception e) {
            return false;
        }
    }
}
