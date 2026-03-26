package stats;

import model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class StatsAggregatorPool {

    private static final Logger log = LoggerFactory.getLogger(StatsAggregatorPool.class);

    private final BlockingQueue<ChatMessage> queue;
    private final List<StatsAggregatorThread> aggregatorThreads = new ArrayList<>();
    private ExecutorService executorService;

    public StatsAggregatorPool(int threadCount, int queueCapacity) {
        this.queue = new LinkedBlockingQueue<>(queueCapacity);

        executorService = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r);
            t.setDaemon(false);
            return t;
        });

        for (int i = 0; i < threadCount; i++) {
            String threadId = "stats-aggregator-" + (i + 1);
            StatsAggregatorThread thread = new StatsAggregatorThread(threadId, queue);
            aggregatorThreads.add(thread);
            executorService.submit(thread);
            log.info("Started {}.", threadId);
        }

        log.info("StatsAggregatorPool started with {} threads, queue capacity {}.",
                threadCount, queueCapacity);
    }

    public void submit(ChatMessage message) {
        if (!queue.offer(message)) {
            log.warn("Stats queue full! Dropping message '{}'.", message.getMessageId());
        }
    }

    public void reportFinalThroughput() {
        log.info("=== Final Throughput Report ===");
        aggregatorThreads.forEach(StatsAggregatorThread::logFinalThroughput);

        long total = getTotalMessages();
        log.info("[stats-pool] Combined total={} messages", total);
        log.info("=== End of Report ===");
    }

    // aggregate across all threads
    public long getTotalMessages() {
        return aggregatorThreads.stream()
                .mapToLong(StatsAggregatorThread::getLocalMessages)
                .sum();
    }

    public Map<String, AtomicLong> getMessagesPerRoom() {
        Map<String, AtomicLong> combined = new HashMap<>();
        for (StatsAggregatorThread t : aggregatorThreads) {
            t.getLocalMessagesPerRoom().forEach((room, count) ->
                    combined.computeIfAbsent(room, k -> new AtomicLong(0))
                            .addAndGet(count.get()));
        }
        return combined;
    }

    public Map<String, AtomicLong> getMessagesPerUser() {
        Map<String, AtomicLong> combined = new HashMap<>();
        for (StatsAggregatorThread t : aggregatorThreads) {
            t.getLocalMessagesPerUser().forEach((user, count) ->
                    combined.computeIfAbsent(user, k -> new AtomicLong(0))
                            .addAndGet(count.get()));
        }
        return combined;
    }

    public void shutdown() {
        log.info("Shutting down StatsAggregatorPool...");
        aggregatorThreads.forEach(StatsAggregatorThread::stop);

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("StatsAggregatorPool shutdown complete.");
    }
}