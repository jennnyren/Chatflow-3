package model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Health and performance metrics for a single ConsumerThread.
 * AtomicLong ensures the health check endpoint can read safely from another thread.
 */
public class ConsumerMetrics {

    private final String threadId;
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong messagesFailedAllRetries = new AtomicLong(0);
    private final AtomicLong duplicatesSkipped = new AtomicLong(0);
    private volatile long lastHeartbeatMs = System.currentTimeMillis();
    private volatile boolean healthy = true;

    public ConsumerMetrics(String threadId) {
        this.threadId = threadId;
    }

    public void recordMessageProcessed() {
        messagesProcessed.incrementAndGet();
        lastHeartbeatMs = System.currentTimeMillis();
    }

    public void recordFailure() {
        messagesFailedAllRetries.incrementAndGet();
    }

    public void recordDuplicateSkipped() {
        duplicatesSkipped.incrementAndGet();
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public void heartbeat() {
        lastHeartbeatMs = System.currentTimeMillis();
    }

    public String getThreadId() { return threadId; }
    public long getMessagesProcessed() { return messagesProcessed.get(); }
    public long getMessagesFailedAllRetries() { return messagesFailedAllRetries.get(); }
    public long getDuplicatesSkipped() { return duplicatesSkipped.get(); }
    public long getLastHeartbeatMs() { return lastHeartbeatMs; }
    public boolean isHealthy() { return healthy; }

    public long secondsSinceLastHeartbeat() {
        return (System.currentTimeMillis() - lastHeartbeatMs) / 1000;
    }
}