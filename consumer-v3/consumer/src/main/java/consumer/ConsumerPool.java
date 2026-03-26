package consumer;

import config.AppConfig;
import dedup.DeduplicationService;
import model.ConsumerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import room.RoomManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Creates and manages the pool of ConsumerThreads.
 * Room distribution (round-robin):
 * 10 rooms / 4 threads → thread-1:[r1,r5,r9], thread-2:[r2,r6,r10], thread-3:[r3,r7], thread-4:[r4,r8]
 * Each thread owns its rooms for the lifetime of the pool.
 */
public class ConsumerPool {

    private static final Logger log = LoggerFactory.getLogger(ConsumerPool.class);

    private final AppConfig config;
    private final RoomManager roomManager;
    private final DeduplicationService deduplicationService;

    private final List<ConsumerThread> consumerThreads = new ArrayList<>();
    private ExecutorService executorService;

    public ConsumerPool(AppConfig config, RoomManager roomManager,
                        DeduplicationService deduplicationService) {
        this.config = config;
        this.roomManager = roomManager;
        this.deduplicationService = deduplicationService;
    }

    public void start(List<String> rooms) {
        int threadCount = config.getConsumerThreadCount();
        int roomCount = rooms.size();

        log.info("Starting ConsumerPool: {} threads for {} rooms.", threadCount, roomCount);

        if (roomCount == 0) {
            log.warn("No rooms provided. Consumer threads will not start.");
            return;
        }

        List<List<String>> assignments = distributeRooms(rooms, threadCount);

        executorService = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r);
            t.setDaemon(false);
            return t;
        });

        for (int i = 0; i < threadCount; i++) {
            String threadId = "consumer-" + (i + 1);
            ConsumerThread thread = new ConsumerThread(
                    threadId, assignments.get(i), config, roomManager, deduplicationService);
            consumerThreads.add(thread);
            executorService.submit(thread);
            log.info("Started {}. Rooms: {}", threadId, assignments.get(i));
        }
    }

    public void shutdown() {
        log.info("Shutting down ConsumerPool...");
        consumerThreads.forEach(ConsumerThread::stop);

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
        log.info("ConsumerPool shutdown complete.");
    }

    public List<ConsumerMetrics> getAllMetrics() {
        List<ConsumerMetrics> result = new ArrayList<>();
        consumerThreads.forEach(t -> result.add(t.getMetrics()));
        return Collections.unmodifiableList(result);
    }

    private List<List<String>> distributeRooms(List<String> rooms, int threadCount) {
        List<List<String>> assignments = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) assignments.add(new ArrayList<>());
        for (int i = 0; i < rooms.size(); i++) assignments.get(i % threadCount).add(rooms.get(i));
        return assignments;
    }
}