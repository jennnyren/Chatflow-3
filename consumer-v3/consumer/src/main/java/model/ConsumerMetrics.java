package model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Health and performance metrics for a single ConsumerThread.
 * AtomicLong ensures the health check endpoint can read safely from another thread.
 */
public class ConsumerMetrics {

    private final String threadId;
    private final AtomicLong messagesConsumed = new AtomicLong(0);
    private final AtomicLong messagesDiscarded = new AtomicLong(0);
    private final AtomicLong duplicatesSkipped = new AtomicLong(0);
    private final AtomicLong messagesBroadcast = new AtomicLong(0);
    private final AtomicLong messagesFailedAllRetries = new AtomicLong(0);
    private volatile long lastHeartbeatMs = System.currentTimeMillis();
    private volatile boolean healthy = true;

    public ConsumerMetrics(String threadId) {
        this.threadId = threadId;
    }

    public void recordConsumed() {
        messagesConsumed.incrementAndGet();
        lastHeartbeatMs = System.currentTimeMillis();
    }

    public void recordDiscarded() {
        messagesDiscarded.incrementAndGet();
    }

    public void recordDuplicateSkipped() {
        duplicatesSkipped.incrementAndGet();
    }

    public void recordMessageBroadcast() {
        messagesBroadcast.incrementAndGet();
    }

    public void recordFailure() {
        messagesFailedAllRetries.incrementAndGet();
    }

    public void reset() {
        messagesConsumed.set(0);
        messagesDiscarded.set(0);
        duplicatesSkipped.set(0);
        messagesBroadcast.set(0);
        messagesFailedAllRetries.set(0);
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public void heartbeat() {
        lastHeartbeatMs = System.currentTimeMillis();
    }

    public String getThreadId() { return threadId; }
    public long getMessagesConsumed() { return messagesConsumed.get(); }
    public long getMessagesDiscarded() { return messagesDiscarded.get(); }
    public long getDuplicatesSkipped() { return duplicatesSkipped.get(); }
    public long getMessagesBroadcast() { return messagesBroadcast.get(); }
    public long getMessagesFailedAllRetries() { return messagesFailedAllRetries.get(); }
    public long getLastHeartbeatMs() { return lastHeartbeatMs; }
    public boolean isHealthy() { return healthy; }

    public long secondsSinceLastHeartbeat() {
        return (System.currentTimeMillis() - lastHeartbeatMs) / 1000;
    }
}