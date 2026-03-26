package worker;

import metrics.MetricsCollector;
import model.ChatMessage;
import model.ChatResponse;
import model.MessageRound;
import util.JsonUtil;
import websocket.ConnectionPool;
import websocket.PooledWebSocketClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SenderWorker implements Runnable {
    private final BlockingQueue<MessageRound> roundQueue;
    private final BlockingQueue<MessageRound> retryQueue;
    private final ConnectionPool connectionPool;
    private final AtomicInteger successCount;
    private final AtomicInteger failureCount;
    private final int roundsToSend;
    private final MetricsCollector metricsCollector;
    private volatile boolean running = true;

    public SenderWorker (BlockingQueue<MessageRound> roundQueue,
                         BlockingQueue<MessageRound> retryQueue,
                         ConnectionPool connectionPool,
                         AtomicInteger successCount,
                         AtomicInteger failureCount,
                         int roundsToSend,
                         MetricsCollector metricsCollector) {
        this.roundQueue = roundQueue;
        this.retryQueue = retryQueue;
        this.connectionPool = connectionPool;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.roundsToSend = roundsToSend;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void run() {
        int sentRounds = 0;
        int emptyPollCount = 0;

        try {
            while (running && sentRounds < roundsToSend) {
                MessageRound round = roundQueue.poll(500, TimeUnit.MILLISECONDS);

                if (round == null) {
                    emptyPollCount++;
                    if (emptyPollCount >= 5) { // 2.5 seconds of empty queue
                        System.out.println(Thread.currentThread().getName() +
                                ": Queue empty after " + sentRounds + " rounds, exiting");
                        break; // EXIT EARLY - don't wait for quota
                    }
                    continue;
                }

                emptyPollCount = 0;
                boolean success = sendRound(round);

                if (success) {
                    successCount.addAndGet(round.getMessageCount());
                    sentRounds++;
                } else {
                    failureCount.addAndGet(round.getMessageCount());
                    sentRounds++; // Count it to avoid blocking
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean sendRound(MessageRound round) {
        try {
            PooledWebSocketClient client = connectionPool.getConnection(round.getRoomId());
            if(!client.isReady()) {
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
            // Exponential backoff
            try {
                long backoff = (long) Math.pow(2, round.getRetryCount()) * 100;
                Thread.sleep(Math.min(backoff, 5000));
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
    public void shutdown() {
        running = false;
    }
}
