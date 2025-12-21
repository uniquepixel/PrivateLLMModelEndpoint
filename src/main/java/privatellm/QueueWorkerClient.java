package privatellm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Queue Worker Client for Processing Remote Queue
 * Fetches pending linking requests from remote API, processes them with LM Studio, and submits results
 */
public class QueueWorkerClient {
    private final APIClient apiClient;
    private final VisionService visionService;
    
    private int processedCount = 0;
    private int successCount = 0;
    private int failedCount = 0;
    
    public QueueWorkerClient(String remoteApiUrl, String queueApiSecret, String lmStudioEndpoint) {
        this.apiClient = new APIClient(remoteApiUrl, queueApiSecret);
        this.visionService = new VisionService(lmStudioEndpoint);
    }
    
    public static void main(String[] args) {
        System.out.println("Queue Worker Client Starting...");
        System.out.println("=================================");
        
        // Load configuration from environment variables
        String remoteApiUrl = System.getenv("REMOTE_API_URL");
        String queueApiSecret = System.getenv("QUEUE_API_SECRET");
        String lmStudioEndpoint = System.getenv("LM_STUDIO_ENDPOINT");
        
        // Validate required environment variables
        if (remoteApiUrl == null || remoteApiUrl.isEmpty()) {
            System.err.println("ERROR: REMOTE_API_URL environment variable is required");
            System.exit(1);
        }
        
        if (queueApiSecret == null || queueApiSecret.isEmpty()) {
            System.err.println("ERROR: QUEUE_API_SECRET environment variable is required");
            System.exit(1);
        }
        
        if (lmStudioEndpoint == null || lmStudioEndpoint.isEmpty()) {
            System.err.println("ERROR: LM_STUDIO_ENDPOINT environment variable is required");
            System.exit(1);
        }
        
        // Display configuration
        System.out.println("Configuration:");
        System.out.println("  Remote API: " + remoteApiUrl);
        System.out.println("  LM Studio: " + lmStudioEndpoint);
        System.out.println();
        
        // Create client instance and process queue
        try {
            QueueWorkerClient client = new QueueWorkerClient(remoteApiUrl, queueApiSecret, lmStudioEndpoint);
            client.processQueue();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Main processing loop - fetch and process all pending requests
     */
    public void processQueue() {
        try {
            System.out.println("Fetching pending requests...");
            List<QueueRequest> requests = fetchPendingRequests();
            
            System.out.println("Found " + requests.size() + " request(s) to process");
            
            if (requests.isEmpty()) {
                System.out.println();
                System.out.println("=================================");
                System.out.println("No pending requests to process");
                System.out.println("=================================");
                return;
            }
            
            System.out.println();
            
            // Process each request
            for (int i = 0; i < requests.size(); i++) {
                QueueRequest request = requests.get(i);
                System.out.println("Processing request " + (i + 1) + "/" + requests.size() + " (ID: " + request.id + ")");
                System.out.println("  User: " + request.userTag);
                System.out.println("  Images: " + request.imageUrls.size());
                
                try {
                    processRequest(request);
                } catch (Exception e) {
                    System.err.println("  ✗ Error processing request: " + e.getMessage());
                    failedCount++;
                    
                    // Try to submit failure result
                    try {
                        submitResult(request.id, false, null, e.getMessage());
                        System.out.println("  ✓ Submitted failure result");
                    } catch (Exception submitError) {
                        System.err.println("  ✗ Failed to submit failure result: " + submitError.getMessage());
                    }
                }
                
                processedCount++;
                System.out.println();
            }
            
            // Print summary
            System.out.println("=================================");
            System.out.println("Processing complete!");
            System.out.println("Processed: " + processedCount);
            System.out.println("Success: " + successCount);
            System.out.println("Failed: " + failedCount);
            System.out.println("=================================");
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to fetch pending requests: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Fetch all pending requests from remote API
     */
    private List<QueueRequest> fetchPendingRequests() throws IOException {
        String response = apiClient.get("/api/queue/pending");
        JSONObject responseJson = new JSONObject(response);
        
        List<QueueRequest> requests = new ArrayList<>();
        
        if (responseJson.has("requests")) {
            JSONArray requestsArray = responseJson.getJSONArray("requests");
            
            for (int i = 0; i < requestsArray.length(); i++) {
                JSONObject reqJson = requestsArray.getJSONObject(i);
                QueueRequest request = new QueueRequest();
                
                request.id = reqJson.getString("id");
                request.messageId = reqJson.optString("messageId", "");
                request.channelId = reqJson.optString("channelId", "");
                request.guildId = reqJson.optString("guildId", "");
                request.userId = reqJson.optString("userId", "");
                request.userTag = reqJson.optString("userTag", "");
                request.timestamp = reqJson.optLong("timestamp", 0);
                request.retryCount = reqJson.optInt("retryCount", 0);
                
                // Parse image URLs
                if (reqJson.has("imageUrls")) {
                    JSONArray imageUrlsArray = reqJson.getJSONArray("imageUrls");
                    for (int j = 0; j < imageUrlsArray.length(); j++) {
                        request.imageUrls.add(imageUrlsArray.getString(j));
                    }
                }
                
                requests.add(request);
            }
        }
        
        return requests;
    }
    
    /**
     * Process a single request
     */
    private void processRequest(QueueRequest request) throws IOException {
        // Extract player tag using vision service
        String playerTag = visionService.extractPlayerTag(request.imageUrls);
        
        if (playerTag != null && !playerTag.isEmpty()) {
            System.out.println("  ✓ Extracted player tag: " + playerTag);
            
            // Submit success result
            submitResult(request.id, true, playerTag, null);
            System.out.println("  ✓ Submitted result");
            successCount++;
            
        } else {
            System.out.println("  ✗ Failed to extract player tag");
            
            // Submit failure result
            submitResult(request.id, false, null, "Could not extract player tag from images");
            System.out.println("  ✓ Submitted failure result");
            failedCount++;
        }
    }
    
    /**
     * Submit result back to remote API
     */
    private void submitResult(String requestId, boolean success, String playerTag, String errorMessage) throws IOException {
        JSONObject result = new JSONObject();
        result.put("requestId", requestId);
        result.put("success", success);
        
        if (playerTag != null) {
            result.put("playerTag", playerTag);
        }
        
        if (errorMessage != null) {
            result.put("errorMessage", errorMessage);
        }
        
        apiClient.post("/api/queue/result", result);
    }
    
    /**
     * Internal class to represent a queue request
     */
    static class QueueRequest {
        String id;
        String messageId;
        String channelId;
        String guildId;
        String userId;
        String userTag;
        List<String> imageUrls = new ArrayList<>();
        long timestamp;
        int retryCount;
    }
}
