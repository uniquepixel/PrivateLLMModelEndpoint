# LLM Proxy Server for LM Studio Integration

A Java-based HTTP proxy server that converts Gemini API format requests to OpenAI/LM Studio format, enabling the use of private LLM models with applications designed for the Gemini API (like crlinkingbot).

## Features

- **API Format Conversion**: Automatically converts between Gemini and OpenAI/LM Studio API formats
- **LM Studio Integration**: Seamlessly forwards requests to a local LM Studio endpoint
- **Optional Authentication**: Supports Bearer token authentication for secure access
- **Health Check Endpoint**: Monitor server status and configuration
- **Concurrent Request Handling**: Thread pool executor for handling multiple simultaneous requests
- **Comprehensive Logging**: Detailed logging of all requests and responses for debugging
- **Error Handling**: Robust error handling with appropriate HTTP status codes

## Prerequisites

- **Java 17** or higher
- **Maven 3.6+** for building
- **LM Studio** installed and running with a model loaded

## LM Studio Setup

1. Download and install [LM Studio](https://lmstudio.ai/)
2. Load your preferred LLM model in LM Studio
3. Start the LM Studio local server:
   - Go to the "Local Server" tab in LM Studio
   - Click "Start Server"
   - Note the endpoint URL (default: `http://localhost:1234/v1/chat/completions`)

## Building the Application

Build the application using Maven:

```bash
mvn clean package
```

This creates a fat JAR file at `target/privatellm-proxy-1.0.0.jar` with all dependencies included.

## Configuration

The server is configured using environment variables. Create a `.env` file based on `.env.example`:

```bash
cp .env.example .env
```

Edit `.env` with your configuration:

```bash
# Required: LM Studio endpoint URL
LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions

# Optional: API secret for Bearer token authentication
API_SECRET=your_secret_here
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `LM_STUDIO_ENDPOINT` | Yes | The URL of your LM Studio OpenAI-compatible endpoint |
| `API_SECRET` | No | If set, clients must provide this as a Bearer token for authentication |

## Running the Server

### Using environment variables from .env file

On Linux/Mac:
```bash
export $(cat .env | xargs)
java -jar target/privatellm-proxy-1.0.0.jar
```

On Windows (PowerShell):
```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2])
    }
}
java -jar target/privatellm-proxy-1.0.0.jar
```

### Using inline environment variables

On Linux/Mac:
```bash
LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions java -jar target/privatellm-proxy-1.0.0.jar
```

On Windows (Command Prompt):
```cmd
set LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions
java -jar target/privatellm-proxy-1.0.0.jar
```

The server will start on port 8080 and display:
```
=================================================
LLM Proxy Server for LM Studio Integration
=================================================
LM Studio Endpoint: http://localhost:1234/v1/chat/completions
Authentication: Disabled
Port: 8080
=================================================
Server started successfully on port 8080
Health check: http://localhost:8080/health
API endpoint: http://localhost:8080/api/generate
```

## API Endpoints

### Health Check

**Endpoint**: `GET /health`

Check if the server is running and view its configuration.

**Example Request**:
```bash
curl http://localhost:8080/health
```

**Example Response**:
```json
{
  "status": "healthy",
  "lm_studio_endpoint": "http://localhost:1234/v1/chat/completions",
  "timestamp": 1703001234
}
```

### Generate Response

**Endpoint**: `POST /api/generate`

Send a Gemini-format request and receive a Gemini-format response.

**Authentication**: If `API_SECRET` is configured, include it as a Bearer token:
```
Authorization: Bearer your_secret_here
```

**Request Format** (Gemini API format):
```json
{
  "contents": [
    {
      "parts": [
        {
          "text": "Hello, how are you?"
        }
      ]
    }
  ]
}
```

**Response Format** (Gemini API format):
```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "I'm doing well, thank you for asking!"
          }
        ]
      },
      "finishReason": "STOP"
    }
  ]
}
```

**Example Request without Authentication**:
```bash
curl -X POST http://localhost:8080/api/generate \
  -H "Content-Type: application/json" \
  -d '{
    "contents": [
      {
        "parts": [
          {
            "text": "What is the capital of France?"
          }
        ]
      }
    ]
  }'
```

**Example Request with Authentication**:
```bash
curl -X POST http://localhost:8080/api/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_secret_here" \
  -d '{
    "contents": [
      {
        "parts": [
          {
            "text": "What is the capital of France?"
          }
        ]
      }
    ]
  }'
```

## Using with crlinkingbot

To configure crlinkingbot to use this proxy server instead of the Gemini API:

1. Start the LLM Proxy Server with authentication enabled:
   ```bash
   LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions \
   API_SECRET=my_secure_secret \
   java -jar target/privatellm-proxy-1.0.0.jar
   ```

2. Configure crlinkingbot to use the proxy:
   - Set the API endpoint to: `http://localhost:8080/api/generate`
   - Set the API key to your `API_SECRET` value
   - Ensure crlinkingbot sends requests in Gemini API format

The proxy will automatically handle the format conversion between Gemini and LM Studio.

## Request/Response Flow

1. **Client** sends Gemini-format request to proxy (`/api/generate`)
2. **Proxy** validates authentication (if configured)
3. **Proxy** extracts text from Gemini `contents` array
4. **Proxy** converts to OpenAI format with `messages` array
5. **Proxy** forwards to LM Studio endpoint
6. **LM Studio** processes the request and returns OpenAI-format response
7. **Proxy** converts OpenAI response to Gemini `candidates` format
8. **Proxy** returns Gemini-format response to client

## Error Handling

The server returns appropriate HTTP status codes:

- `200 OK`: Successful request
- `401 Unauthorized`: Missing or invalid API secret
- `405 Method Not Allowed`: Wrong HTTP method used
- `500 Internal Server Error`: Server or LM Studio error

All errors include a JSON response with an error message:
```json
{
  "error": "Error description"
}
```

## Timeouts

- **Connect Timeout**: 30 seconds
- **Read Timeout**: 60 seconds

## Logging

The server logs all requests and responses to stdout for debugging:
- Incoming requests with timestamps and client addresses
- Request/response transformations
- Errors and exceptions

Example log output:
```
[2024-01-01T12:00:00.000Z] Generate request from /127.0.0.1:54321
Received Gemini request: {"contents":[{"parts":[{"text":"Hello"}]}]}
Converted to OpenAI request: {"messages":[{"role":"user","content":"Hello"}],"temperature":0.7,"max_tokens":2000,"stream":false}
Received LM Studio response: {"choices":[{"message":{"content":"Hi there!"}}]}
Converted to Gemini response: {"candidates":[{"content":{"parts":[{"text":"Hi there!"}]},"finishReason":"STOP"}]}
```

## Troubleshooting

### Server won't start
- **Error**: "LM_STUDIO_ENDPOINT environment variable is required"
  - **Solution**: Ensure you've set the `LM_STUDIO_ENDPOINT` environment variable

### Connection refused to LM Studio
- **Error**: "Connection refused" or "LM Studio returned error code"
  - **Solution**: 
    - Verify LM Studio is running
    - Check the endpoint URL matches your LM Studio configuration
    - Ensure a model is loaded in LM Studio

### Authentication failures
- **Error**: "Unauthorized. Invalid or missing API secret."
  - **Solution**: Include the correct `Authorization: Bearer <API_SECRET>` header in your requests

### Port 8080 already in use
- **Error**: "Address already in use"
  - **Solution**: 
    - Stop any other service using port 8080
    - Or modify the `PORT` constant in `LLMProxyServer.java` and rebuild

## Development

### Project Structure
```
.
├── pom.xml                           # Maven configuration
├── src/
│   └── main/
│       └── java/
│           └── privatellm/
│               └── LLMProxyServer.java  # Main server implementation
├── .env.example                      # Example environment configuration
├── .gitignore                        # Git ignore rules
└── README.md                         # This file
```

### Building from Source
```bash
# Clean and compile
mvn clean compile

# Run tests (if any)
mvn test

# Package into JAR
mvn package

# Run directly with Maven
mvn exec:java -Dexec.mainClass="privatellm.LLMProxyServer"
```

## Queue Worker Client

The Queue Worker Client is a standalone application that processes remote queue requests from the crlinkingbot API. It fetches pending linking requests, processes images using local LM Studio to extract Clash Royale player tags, and submits the results back to the remote API.

### What It Does

- Fetches pending linking requests from a remote crlinkingbot server
- Downloads Clash Royale profile screenshots from the requests
- Processes images with LM Studio (vision-capable model) to extract player tags
- Submits results (success or failure) back to the remote API
- Runs once on-demand and exits when complete

### Prerequisites

1. **LM Studio** with a vision-capable model loaded and running
   - Go to the "Local Server" tab
   - Start the server (default: `http://localhost:1234`)
   - Ensure a vision model is loaded that can process images

2. **Remote API Access**
   - Access to a crlinkingbot server with queue API
   - Valid API secret token for authentication

### Configuration

The queue worker uses three environment variables:

| Variable | Required | Description |
|----------|----------|-------------|
| `REMOTE_API_URL` | Yes | Base URL of the crlinkingbot API (e.g., `http://your-server.com:8090`) |
| `QUEUE_API_SECRET` | Yes | Bearer token for authenticating with the remote API |
| `LM_STUDIO_ENDPOINT` | Yes | Local LM Studio endpoint (e.g., `http://localhost:1234/v1/chat/completions`) |

Update your `.env` file with these values:

```bash
# Queue Worker Configuration
REMOTE_API_URL=http://your-server.com:8090
QUEUE_API_SECRET=your_secret_token_here

# LM Studio Configuration (same as proxy server)
LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions
```

### Running the Queue Worker

#### Using environment variables from .env file

On Linux/Mac:
```bash
export $(cat .env | xargs)
java -jar target/queue-worker-client.jar
```

On Windows (PowerShell):
```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2])
    }
}
java -jar target/queue-worker-client.jar
```

#### Using inline environment variables

On Linux/Mac:
```bash
REMOTE_API_URL=http://your-server.com:8090 \
QUEUE_API_SECRET=your_secret_token \
LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions \
java -jar target/queue-worker-client.jar
```

On Windows (Command Prompt):
```cmd
set REMOTE_API_URL=http://your-server.com:8090
set QUEUE_API_SECRET=your_secret_token
set LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions
java -jar target/queue-worker-client.jar
```

### Expected Output

When running, the queue worker displays detailed progress:

```
Queue Worker Client Starting...
=================================
Configuration:
  Remote API: http://your-server.com:8090
  LM Studio: http://localhost:1234/v1/chat/completions

Fetching pending requests...
Found 3 request(s) to process

Processing request 1/3 (ID: abc-123)
  User: username#1234
  Images: 2
  Downloading 2 image(s)...
  ✓ Downloaded 2 image(s)
  Analyzing images with LM Studio...
  ✓ Extracted player tag: #ABC123XYZ
  ✓ Submitted result

Processing request 2/3 (ID: def-456)
  User: anotheruser#5678
  Images: 1
  Downloading 1 image(s)...
  ✓ Downloaded 1 image(s)
  Analyzing images with LM Studio...
  ✗ Failed to extract player tag
  ✓ Submitted failure result

Processing request 3/3 (ID: ghi-789)
  User: thirduser#9012
  Images: 3
  Downloading 3 image(s)...
  ✓ Downloaded 3 image(s)
  Analyzing images with LM Studio...
  ✓ Extracted player tag: #2PPXYZ
  ✓ Submitted result

=================================
Processing complete!
Processed: 3
Success: 2
Failed: 1
=================================
```

### How It Works

1. **Fetch Pending Requests**: Calls `GET /api/queue/pending` on the remote API to retrieve all pending linking requests
2. **Process Each Request**:
   - Downloads images from the provided URLs
   - Converts images to base64 format
   - Sends images to LM Studio with a German-language prompt
   - Extracts player tag using regex pattern `#[A-Z0-9]{3,10}`
3. **Submit Results**: Calls `POST /api/queue/result` with:
   - Success status
   - Extracted player tag (if successful)
   - Error message (if failed)
4. **Exit**: Program exits with code 0 on success, 1 on critical failure

### Error Handling

The queue worker is designed to be resilient:

- **Individual Request Failures**: If one request fails, processing continues with remaining requests
- **Network Errors**: Retries are not implemented; failures are logged and submitted back to the API
- **Invalid Environment Variables**: Program exits immediately with error code 1
- **LM Studio Unavailable**: Error is logged and failure result is submitted for that request

### Troubleshooting

#### "ERROR: REMOTE_API_URL environment variable is required"
- **Solution**: Set all three required environment variables before running

#### "GET request failed with code 401"
- **Solution**: Check that `QUEUE_API_SECRET` matches the expected token on the remote API

#### "LM Studio returned error code"
- **Solution**: 
  - Verify LM Studio is running and accessible
  - Ensure a vision-capable model is loaded
  - Check the `LM_STUDIO_ENDPOINT` URL is correct

#### "Failed to download any images"
- **Solution**: 
  - Check network connectivity to image URLs
  - Verify image URLs are accessible from your network

#### "Failed to extract player tag"
- **Solution**: 
  - The images may not contain a visible player tag
  - LM Studio model may need to be changed to a better vision model
  - Check LM Studio logs for processing errors

### Scheduling Periodic Execution

To run the queue worker periodically (e.g., every 5 minutes), use:

**Linux/Mac (cron)**:
```bash
*/5 * * * * cd /path/to/project && export $(cat .env | xargs) && java -jar target/queue-worker-client.jar >> queue-worker.log 2>&1
```

**Windows (Task Scheduler)**:
Create a batch file `run-queue-worker.bat`:
```batch
@echo off
cd C:\path\to\project
for /f "tokens=*" %%a in (.env) do set %%a
java -jar target\queue-worker-client.jar >> queue-worker.log 2>&1
```

Then schedule it in Task Scheduler to run every 5 minutes.

## License

This project is provided as-is for integration with LM Studio and private LLM models.

## Support

For issues or questions:
1. Verify LM Studio is running and accessible
2. Check the server logs for detailed error messages
3. Ensure environment variables are correctly set
4. Test the health endpoint to verify configuration
