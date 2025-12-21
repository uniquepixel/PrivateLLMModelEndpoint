# CR Linking Queue Worker

A Java-based queue worker client that processes CR (Clash Royale) linking requests from the remote crlinkingbot API. It fetches pending linking requests, processes profile screenshots using local LM Studio with a vision model, extracts player tags, and submits results back to the API.

## Features

- **Queue Processing**: Fetches pending linking requests from remote crlinkingbot API
- **Image Analysis**: Processes Clash Royale profile screenshots with local LM Studio vision models
- **Player Tag Extraction**: Extracts player tags from images using AI vision
- **Result Submission**: Automatically submits processing results back to the remote API
- **One-Shot Execution**: Runs once and exits when complete - designed for scheduled/cron execution
- **Comprehensive Logging**: Detailed logging of all operations for debugging and monitoring

## Prerequisites

- **Java 17** or higher
- **Maven 3.6+** for building
- **LM Studio** installed and running with a vision-capable model loaded
- **Access to crlinkingbot API** (root server with queue endpoints)

## LM Studio Setup

1. Download and install [LM Studio](https://lmstudio.ai/)
2. Load a vision-capable LLM model in LM Studio (e.g., LLaVA, Qwen-VL, or similar)
3. Start the LM Studio local server:
   - Go to the "Local Server" tab in LM Studio
   - Click "Start Server"
   - Note the endpoint URL (default: `http://localhost:1234/v1/chat/completions`)
4. Ensure the vision model is properly loaded and can process images

## Building the Application

Build the queue worker using Maven:

```bash
mvn clean package
```

This creates a fat JAR file at `target/queue-worker-client.jar` with all dependencies included.

## Configuration

The queue worker is configured using environment variables. Create a `.env` file based on `.env.example`:

```bash
cp .env.example .env
```

Edit `.env` with your configuration:

```bash
# Queue Worker Configuration
REMOTE_API_URL=http://your-server.com:8090
QUEUE_API_SECRET=your_secret_token_here

# LM Studio Configuration (local)
LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `REMOTE_API_URL` | Yes | Base URL of the crlinkingbot API server (e.g., `http://your-server.com:8090`) |
| `QUEUE_API_SECRET` | Yes | Bearer token for authenticating with the remote API |
| `LM_STUDIO_ENDPOINT` | Yes | Local LM Studio endpoint URL for vision processing |

## Running the Queue Worker

### Using environment variables from .env file

**Linux/Mac:**
```bash
export $(cat .env | xargs)
java -jar target/queue-worker-client.jar
```

**Windows (PowerShell):**
```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2])
    }
}
java -jar target/queue-worker-client.jar
```

### Using inline environment variables

**Linux/Mac:**
```bash
REMOTE_API_URL=http://your-server.com:8090 \
QUEUE_API_SECRET=your_secret_token \
LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions \
java -jar target/queue-worker-client.jar
```

**Windows (Command Prompt):**
```cmd
set REMOTE_API_URL=http://your-server.com:8090
set QUEUE_API_SECRET=your_secret_token
set LM_STUDIO_ENDPOINT=http://localhost:1234/v1/chat/completions
java -jar target/queue-worker-client.jar
```

## Expected Output

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

## How It Works

1. **Fetch Pending Requests**: Calls `GET /api/queue/pending` on the remote API to retrieve all pending linking requests
2. **Process Each Request**:
   - Downloads Clash Royale profile screenshots from the provided URLs
   - Converts images to base64 format for API transmission
   - Sends images to LM Studio with a vision prompt
   - Extracts player tag using regex pattern `#[A-Z0-9]{3,10}`
3. **Submit Results**: Calls `POST /api/queue/result` with:
   - Success/failure status
   - Extracted player tag (if successful)
   - Error message (if failed)
4. **Exit**: Program exits with code 0 on success, 1 on critical failure

## Scheduling Periodic Execution

To run the queue worker periodically (e.g., every 5 minutes), use:

**Linux/Mac (cron):**
```bash
*/5 * * * * cd /path/to/project && export $(cat .env | xargs) && java -jar target/queue-worker-client.jar >> queue-worker.log 2>&1
```

**Windows (Task Scheduler):**

Create a batch file `run-queue-worker.bat`:
```batch
@echo off
cd C:\path\to\project
for /f "tokens=*" %%a in (.env) do set %%a
java -jar target\queue-worker-client.jar >> queue-worker.log 2>&1
```

Then schedule it in Task Scheduler to run every 5 minutes.

## Troubleshooting

### Environment Variable Errors

**Error**: `ERROR: REMOTE_API_URL environment variable is required`
- **Solution**: Set all three required environment variables before running the worker

### Authentication Failures

**Error**: `GET request failed with code 401`
- **Solution**: Verify that `QUEUE_API_SECRET` matches the expected token on the remote API server

### LM Studio Connection Issues

**Error**: `LM Studio returned error code` or `Connection refused`
- **Solution**: 
  - Verify LM Studio is running and accessible at the configured endpoint
  - Ensure a vision-capable model is loaded in LM Studio
  - Check the `LM_STUDIO_ENDPOINT` URL is correct (default: `http://localhost:1234/v1/chat/completions`)
  - Test LM Studio by accessing the endpoint directly

### Image Download Failures

**Error**: `Failed to download any images`
- **Solution**: 
  - Check network connectivity to image URLs
  - Verify image URLs are accessible from your network
  - Check firewall settings

### Player Tag Extraction Failures

**Error**: `Failed to extract player tag`
- **Solution**: 
  - The images may not contain a visible or readable player tag
  - Try a different/better vision model in LM Studio
  - Check LM Studio logs for processing errors
  - Ensure the vision model supports image analysis

### Build Failures

**Error**: Maven build fails
- **Solution**:
  - Ensure Java 17 or higher is installed: `java -version`
  - Ensure Maven is installed: `mvn -version`
  - Clean the build: `mvn clean` then rebuild: `mvn package`

## Development

### Project Structure
```
.
├── pom.xml                              # Maven configuration
├── src/
│   └── main/
│       └── java/
│           └── privatellm/
│               ├── QueueWorkerClient.java  # Main worker implementation
│               ├── APIClient.java          # Remote API communication
│               └── VisionService.java      # LM Studio vision integration
├── .env.example                         # Example environment configuration
├── .gitignore                           # Git ignore rules
└── README.md                            # This file
```

### Building from Source
```bash
# Clean and compile
mvn clean compile

# Run tests (if any)
mvn test

# Package into JAR
mvn package

# Run directly with Maven (with environment variables set)
mvn exec:java -Dexec.mainClass="privatellm.QueueWorkerClient"
```

## Error Handling

The queue worker is designed to be resilient:

- **Individual Request Failures**: If one request fails, processing continues with remaining requests
- **Network Errors**: Failures are logged and submitted back to the API as failed results
- **Invalid Environment Variables**: Program exits immediately with error code 1
- **LM Studio Unavailable**: Error is logged and failure result is submitted for that request

## License

This project is provided as-is for integration with LM Studio and crlinkingbot.

## Support

For issues or questions:
1. Verify all prerequisites are installed and running
2. Check the worker logs for detailed error messages
3. Ensure environment variables are correctly configured
4. Test LM Studio endpoint independently
5. Verify network connectivity to the remote API server
