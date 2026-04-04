package db;

import model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class DbWriterPool {

    private static final Logger log = LoggerFactory.getLogger(DbWriterPool.class);

    private final BlockingQueue<ChatMessage> queue;
    private final List<DbWriterThread> writerThreads = new ArrayList<>();
    private final ConcurrentLinkedQueue<Long> latencySamples = new ConcurrentLinkedQueue<>();
    private final AtomicLong messagesWrittenToDb = new AtomicLong(0);
    private final AtomicLong messagesDroppedQueueFull = new AtomicLong(0);
    private final AtomicLong messagesDeadLettered = new AtomicLong(0);
    private ExecutorService executorService;

    public DbWriterPool(int threadCount, int queueCapacity) {
        this.queue = new LinkedBlockingQueue<>(queueCapacity);

        executorService = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r);
            t.setDaemon(false);
            return t;
        });

        for (int i = 0; i < threadCount; i++) {
            String threadId = "db-writer-" + (i + 1);
            DbWriterThread thread = new DbWriterThread(threadId, queue, latencySamples, messagesWrittenToDb, messagesDeadLettered);
            writerThreads.add(thread);
            executorService.submit(thread);
            log.info("Started {}.", threadId);
        }

        log.info("DbWriterPool started with {} threads, queue capacity {}.",
                threadCount, queueCapacity);
    }

    public void submit(ChatMessage message) {
        if (!queue.offer(message)) {
            messagesDroppedQueueFull.incrementAndGet();
            log.warn("DB writer queue full! Dropping message '{}'.", message.getMessageId());
        }
    }

    public long getMessagesWrittenToDb() { return messagesWrittenToDb.get(); }
    public long getMessagesDroppedQueueFull() { return messagesDroppedQueueFull.get(); }
    public long getMessagesDeadLettered() { return messagesDeadLettered.get(); }

    public void resetMetrics() {
        messagesWrittenToDb.set(0);
        messagesDroppedQueueFull.set(0);
        messagesDeadLettered.set(0);
    }

    public void reportLatencyPercentiles() {
        List<Long> samples = new ArrayList<>(latencySamples);
        if (samples.isEmpty()) {
            log.info("[db-writer-pool] No latency samples recorded.");
            return;
        }

        Collections.sort(samples);
        int size = samples.size();
        long p50 = samples.get((int) Math.ceil(size * 0.50) - 1);
        long p95 = samples.get((int) Math.ceil(size * 0.95) - 1);
        long p99 = samples.get((int) Math.ceil(size * 0.99) - 1);

        log.info("[db-writer-pool] DB write latency | samples={} | p50={}ms | p95={}ms | p99={}ms",
                size, p50, p95, p99);
    }

    public void shutdown() {
        log.info("Shutting down DbWriterPool...");
        writerThreads.forEach(DbWriterThread::stop);

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
        log.info("DbWriterPool shutdown complete.");
    }
}