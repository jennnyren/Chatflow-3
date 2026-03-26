package websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import config.AppConfig;
import model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Broadcasts a message by calling Part 1's internal HTTP endpoint.
 *
 * Part 1 owns the WebSocket connections. This class is the bridge:
 * it serializes the message and POSTs it to Part 1, which then
 * iterates its roomMapping and sends to all connected clients.
 *
 * POST http://part1-host:8081/internal/broadcast
 * Body: { "roomId": "room1", "message": "<serialized message JSON>" }
 *
 * Thread safety: HttpClient is thread-safe and shared across all consumer threads.
 */
public class WebSocketBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcaster.class);

    private final String broadcastUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebSocketBroadcaster(AppConfig config) {
        this.broadcastUrl = config.getPart1BroadcastUrl();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("WebSocketBroadcaster initialized. Will call: {}", broadcastUrl);
    }

    /**
     * Posts the message to Part 1's broadcast endpoint.
     *
     * @throws BroadcastException if the HTTP call fails or Part 1 returns a non-200 response.
     */
    public void broadcast(String roomId, ChatMessage message) throws BroadcastException {
        // Serialize the message payload (this is what Part 1 will send to WebSocket clients)
        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new BroadcastException("Failed to serialize message: " + e.getMessage(), false);
        }

        // Build the request body
        String requestBody;
        try {
            Map<String, String> body = new HashMap<>();
            body.put("roomId", roomId);
            body.put("message", messageJson);
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BroadcastException("Failed to build request body: " + e.getMessage(), false);
        }

        // POST to Part 1
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(broadcastUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.debug("Broadcast to room '{}' succeeded. Part 1 response: {}",
                        roomId, response.body());
            } else {
                // Non-200 from Part 1 — retryable (Part 1 might be temporarily overloaded)
                throw new BroadcastException(
                        "Part 1 returned HTTP " + response.statusCode() + ": " + response.body(),
                        true);
            }

        } catch (BroadcastException e) {
            throw e; // rethrow as-is
        } catch (Exception e) {
            // Network failure, timeout, connection refused — all retryable
            throw new BroadcastException("HTTP call to Part 1 failed: " + e.getMessage(), true);
        }
    }

    /**
     * Thrown when broadcast cannot reach Part 1 or Part 1 returns an error.
     * retryable=true  → RoomManager will retry with backoff
     * retryable=false → RoomManager will discard the message
     */
    public static class BroadcastException extends Exception {
        private final boolean retryable;

        public BroadcastException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public boolean isRetryable() { return retryable; }
    }
}