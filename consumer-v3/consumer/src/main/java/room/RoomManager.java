package room;

import config.AppConfig;
import db.DbWriterPool;
import model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stats.StatsAggregatorPool;
import websocket.WebSocketBroadcaster;

/**
 * Routes a consumed message to the WebSocketBroadcaster with retry logic.
 *
 * Pipeline position: Consumer → RoomManager → WebSocketBroadcaster → Part 1 HTTP
 *
 * Responsibilities:
 * - Validate the message has a roomId
 * - Attempt broadcast, retry on retryable failures
 * - Return ACK / NACK / DISCARD so ConsumerThread knows what to tell RabbitMQ
 */
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final WebSocketBroadcaster broadcaster;
    private final int maxRetries;
    private final long retryDelayMs;
    private final DbWriterPool dbWriterPool;
    private final StatsAggregatorPool statsAggregatorPool;

    public RoomManager(WebSocketBroadcaster broadcaster, AppConfig config,
                       DbWriterPool dbWriterPool, StatsAggregatorPool statsAggregatorPool) {
        this.broadcaster = broadcaster;
        this.dbWriterPool = dbWriterPool;
        this.statsAggregatorPool = statsAggregatorPool;
        this.maxRetries = config.getMessageRetryMax();
        this.retryDelayMs = config.getMessageRetryDelayMs();
    }

    /**
     * Process and broadcast a message.
     * @return ProcessResult telling ConsumerThread how to ack/nack in RabbitMQ
     */
    public ProcessResult process(ChatMessage message) {
        if (message.getRoomId() == null || message.getRoomId().isEmpty()) {
            log.warn("Message '{}' has no roomId — discarding.", message.getMessageId());
            return ProcessResult.DISCARD;
        }

        int attempt = 0;
        while (attempt <= maxRetries) {
            try {
                broadcaster.broadcast(message.getRoomId(), message);
                dbWriterPool.submit(message);
                statsAggregatorPool.submit(message);
                log.debug("Message '{}' broadcast to room '{}' on attempt {}.",
                        message.getMessageId(), message.getRoomId(), attempt + 1);
                return ProcessResult.ACK;

            } catch (WebSocketBroadcaster.BroadcastException e) {
                if (!e.isRetryable()) {
                    log.error("Non-retryable broadcast failure for message '{}': {}",
                            message.getMessageId(), e.getMessage());
                    return ProcessResult.DISCARD;
                }

                attempt++;
                if (attempt > maxRetries) {
                    log.error("Message '{}' failed all {} retry attempts. Will nack.",
                            message.getMessageId(), maxRetries);
                    return ProcessResult.NACK;
                }

                log.warn("Broadcast attempt {}/{} failed for message '{}'. Retrying in {}ms. Reason: {}",
                        attempt, maxRetries, message.getMessageId(), retryDelayMs, e.getMessage());

                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return ProcessResult.NACK;
                }
            }
        }

        return ProcessResult.NACK;
    }

    public enum ProcessResult {
        /** Broadcast succeeded → basicAck */
        ACK,
        /** All retries exhausted → basicNack(requeue=true) */
        NACK,
        /** Invalid message or non-retryable error → basicAck to remove from queue */
        DISCARD
    }
}