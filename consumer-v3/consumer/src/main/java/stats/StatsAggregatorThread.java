package stats;

import model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class StatsAggregatorThread implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StatsAggregatorThread.class);
    private static final int QUEUE_DEPTH_SAMPLE_INTERVAL_SEC = 1;

    private final String threadId;
    private final BlockingQueue<ChatMessage> queue;
    private volatile boolean running = true;

    // per-thread counters (no sharing)
    private final AtomicLong localMessages = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> localMessagesPerRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> localMessagesPerUser = new ConcurrentHashMap<>();

    // throughput tracking
    private volatile long startTimeMs = System.currentTimeMillis();
    private volatile double currentThroughput = 0.0;

    // queue depth tracking
    private volatile int currentQueueDepth = 0;

    // test state
    private volatile boolean testRunning = false;

    private final ScheduledExecutorService sampler;

    public StatsAggregatorThread(String threadId,
                                 BlockingQueue<ChatMessage> queue) {
        this.threadId = threadId;
        this.queue = queue;
        this.sampler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stats-sampler-" + threadId);
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void run() {
        log.info("[{}] StatsAggregatorThread started.", threadId);
        startSampler();

        while (running || !queue.isEmpty()) {
            try {
                ChatMessage msg = queue.poll(500, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    if (!testRunning) {
                        // first message of a new test — reset and start tracking
                        testRunning = true;
                        startTimeMs = System.currentTimeMillis();
                        localMessages.set(0);
                        localMessagesPerRoom.clear();
                        localMessagesPerUser.clear();
                        log.info("[{}] New test detected, counters reset.", threadId);
                    }
                    localMessages.incrementAndGet();
                    localMessagesPerRoom
                            .computeIfAbsent(msg.getRoomId(), k -> new AtomicLong(0))
                            .incrementAndGet();
                    localMessagesPerUser
                            .computeIfAbsent(msg.getUserId(), k -> new AtomicLong(0))
                            .incrementAndGet();
                    log.debug("[{}] Aggregated stats for message '{}'.",
                            threadId, msg.getMessageId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[{}] Unexpected error aggregating stats: {}", threadId, e.getMessage());
            }
        }

        sampler.shutdownNow();
        try {
            sampler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logFinalThroughput();
        log.info("[{}] StatsAggregatorThread stopped.", threadId);
    }

    private void startSampler() {
        sampler.scheduleAtFixedRate(() -> {
            currentQueueDepth = queue.size();
            log.info("[{}] queueDepth={}", threadId, currentQueueDepth);
        }, QUEUE_DEPTH_SAMPLE_INTERVAL_SEC, QUEUE_DEPTH_SAMPLE_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void logFinalThroughput() {
        if (!testRunning) {
            log.info("[{}] No test has run yet, nothing to report.", threadId);
            return;
        }
        double elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0;
        currentThroughput = elapsedSec > 0 ? localMessages.get() / elapsedSec : 0.0;
        log.info("[{}] Final throughput={} msg/sec (processed {} messages in {} sec)",
                threadId, currentThroughput, localMessages.get(), elapsedSec);
        testRunning = false;
    }

    public void stop() { running = false; }
    public String getThreadId() { return threadId; }
    public double getThroughput() { return currentThroughput; }
    public int getQueueDepth() { return currentQueueDepth; }
    public long getLocalMessages() { return localMessages.get(); }
    public ConcurrentHashMap<String, AtomicLong> getLocalMessagesPerRoom() { return localMessagesPerRoom; }
    public ConcurrentHashMap<String, AtomicLong> getLocalMessagesPerUser() { return localMessagesPerUser; }
}