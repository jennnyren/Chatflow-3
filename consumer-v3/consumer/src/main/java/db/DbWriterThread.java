package db;

import model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DbWriterThread implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DbWriterThread.class);

    private static final int BATCH_SIZE = Integer.parseInt(
            System.getenv().getOrDefault("DB_BATCH_SIZE", "100")
    );
    private static final long FLUSH_INTERVAL_MS = Long.parseLong(
            System.getenv().getOrDefault("DB_FLUSH_INTERVAL_MS", "100")
    );

    private final String threadId;
    private final BlockingQueue<ChatMessage> queue;
    private final MessageRepository messageRepository;
    private final ConcurrentLinkedQueue<Long> latencySamples;
    private final AtomicLong messagesWrittenToDb;
    private final AtomicLong messagesDeadLettered;
    private volatile boolean running = true;

    public DbWriterThread(String threadId,
                          BlockingQueue<ChatMessage> queue,
                          ConcurrentLinkedQueue<Long> latencySamples,
                          AtomicLong messagesWrittenToDb,
                          AtomicLong messagesDeadLettered) {
        this.threadId = threadId;
        this.queue = queue;
        this.latencySamples = latencySamples;
        this.messagesWrittenToDb = messagesWrittenToDb;
        this.messagesDeadLettered = messagesDeadLettered;
        this.messageRepository = new MessageRepository();
    }

    @Override
    public void run() {
        log.info("[{}] DbWriterThread started.", threadId);
        List<ChatMessage> batch = new ArrayList<>(BATCH_SIZE);
        long lastFlush = System.currentTimeMillis();

        while (running || !queue.isEmpty()) {
            try {
                // Drain up to BATCH_SIZE messages
                ChatMessage message = queue.poll(50, TimeUnit.MILLISECONDS);
                if (message != null) {
                    batch.add(message);
                    queue.drainTo(batch, BATCH_SIZE - batch.size());
                }

                // Flush if batch is full OR flush interval has elapsed
                boolean batchFull = batch.size() >= BATCH_SIZE;
                boolean intervalElapsed = System.currentTimeMillis() - lastFlush >= FLUSH_INTERVAL_MS;

                if (!batch.isEmpty() && (batchFull || intervalElapsed)) {
                    flushBatch(batch);
                    lastFlush = System.currentTimeMillis();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[{}] Unexpected error: {}", threadId, e.getMessage());
            }
        }

        // Final flush on shutdown
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }

        log.info("[{}] DbWriterThread stopped.", threadId);
    }

    private void flushBatch(List<ChatMessage> batch) {
        try {
            long start = System.currentTimeMillis();
            int inserted = messageRepository.saveAll(batch);
            long latencyMs = System.currentTimeMillis() - start;
            latencySamples.add(latencyMs);
            messagesWrittenToDb.addAndGet(inserted);
            log.debug("[{}] Flushed batch of {} messages ({} inserted) in {}ms.", threadId, batch.size(), inserted, latencyMs);
        } catch (MessageRepository.DeadLetterException e) {
            log.warn("[{}] Batch of {} failed all retries — falling back to individual saves.", threadId, e.getBatchSize());
            fallbackIndividual(batch);
        } catch (Exception e) {
            log.error("[{}] Unexpected error flushing batch of {}: {}", threadId, batch.size(), e.getMessage());
        } finally {
            batch.clear();
        }
    }

    private void fallbackIndividual(List<ChatMessage> batch) {
        for (ChatMessage msg : batch) {
            try {
                messageRepository.save(msg);
                messagesWrittenToDb.incrementAndGet();
            } catch (Exception e) {
                messagesDeadLettered.incrementAndGet();
                log.error("[{}] Dead letter: message '{}' permanently lost: {}", threadId, msg.getMessageId(), e.getMessage());
            }
        }
    }

    public void stop() { running = false; }
    public String getThreadId() { return threadId; }
}