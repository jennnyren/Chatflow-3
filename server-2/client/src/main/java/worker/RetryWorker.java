package worker;

import metrics.MetricsCollector;
import model.ChatMessage;
import model.MessageRound;
import util.JsonUtil;
import websocket.ConnectionPool;
import websocket.PooledWebSocketClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryWorker implements Runnable {
    private final BlockingQueue<MessageRound> retryQueue;
    private final ConnectionPool connectionPool;
    private final AtomicInteger successCount;
    private final AtomicInteger failureCount;
    private final MetricsCollector metricsCollector;
    private volatile boolean running = true;

    public RetryWorker(BlockingQueue<MessageRound> retryQueue,
                       ConnectionPool connectionPool,
                       AtomicInteger successCount,
                       AtomicInteger failureCount,
                       MetricsCollector metricsCollector) {
        this.retryQueue = retryQueue;
        this.connectionPool = connectionPool;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void run() {
        try {
            while (running) {
                MessageRound round = retryQueue.poll(500, TimeUnit.MICROSECONDS);

                if (round == null) {
                    continue;
                }

                boolean success = retryRound(round);

                if (success) {
                    successCount.addAndGet(round.getMessageCount());
                } else {
                    if (round.canRetry()) {
                        round.incrementRetryCount();
                        retryQueue.offer(round);
                    } else {
                        failureCount.addAndGet(round.getMessageCount());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean retryRound(MessageRound round) {
        try {
            long backoff = (long) Math.pow(2, round.getRetryCount()) * 100;
            Thread.sleep(Math.min(backoff, 500));

            PooledWebSocketClient client = connectionPool.getConnection(round.getRoomId());

            if (!client.isReady()) {
                return false;
            }

            for (ChatMessage message : round.getMessages()) {
                if (metricsCollector != null) {
                    metricsCollector.recordMessageSent(
                            message.getMessageType(),
                            round.getRoomId()
                    );
                }

                String json = JsonUtil.toJson(message);
                client.send(json);
                client.incrementMessagesSent();

                Thread.sleep(5);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() {
        running = false;
    }
}
