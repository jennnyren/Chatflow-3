package consumer;

import com.rabbitmq.client.*;
import config.AppConfig;
import dedup.DeduplicationService;
import model.ChatMessage;
import model.ConsumerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import room.RoomManager;
import util.JsonUtil;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * A single consumer thread.
 *
 * For each assigned room:
 * Subscribes to the room's queue
 * On each message: deserialize → dedup → RoomManager → ack/nack
 *
 * One thread per set of rooms guarantees in-order delivery within each room
 * (since RabbitMQ queues are FIFO and basicQos=1 means one unacked message at a time).
 *
 * On RabbitMQ connection loss, retries every reconnectDelayMs until restored.
 */
public class ConsumerThread implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ConsumerThread.class);

    private final String threadId;
    private final List<String> assignedRooms;
    private final AppConfig config;
    private final RoomManager roomManager;
    private final DeduplicationService deduplicationService;
    private final ConsumerMetrics metrics;

    private volatile boolean running = true;

    public ConsumerThread(String threadId,
                          List<String> assignedRooms,
                          AppConfig config,
                          RoomManager roomManager,
                          DeduplicationService deduplicationService) {
        this.threadId = threadId;
        this.assignedRooms = assignedRooms;
        this.config = config;
        this.roomManager = roomManager;
        this.deduplicationService = deduplicationService;
        this.metrics = new ConsumerMetrics(threadId);
    }

    @Override
    public void run() {
        log.info("[{}] Starting. Assigned rooms: {}", threadId, assignedRooms);
        metrics.setHealthy(true);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                runConsumerLoop();
            } catch (Exception e) {
                if (!running) break;
                log.error("[{}] Consumer loop failed: {}. Reconnecting in {}ms...",
                        threadId, e.getMessage(), config.getRabbitMQReconnectDelayMs());
                metrics.setHealthy(false);
                metrics.recordFailure();
                sleep(config.getRabbitMQReconnectDelayMs());
            }
        }

        log.info("[{}] Stopped.", threadId);
    }

    private void runConsumerLoop() throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory factory = buildConnectionFactory();

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            log.info("[{}] Connected to RabbitMQ.", threadId);
            metrics.setHealthy(true);

            // One unacked message at a time, ensures ordering and fair distribution
            channel.basicQos(1);

            // Subscribe to each assigned room queue
            // Queue name = roomId, matching how Part 1 routes messages
            for (String roomId : assignedRooms) {
                //channel.queueDeclare(roomId, true, false, false, null);
                channel.basicConsume(roomId, false, buildDeliverCallback(channel), tag ->
                        log.warn("[{}] Consumer cancelled for room '{}'.", threadId, roomId));
                log.info("[{}] Subscribed to queue '{}'.", threadId, roomId);
            }

            metrics.heartbeat();

            // Block here; RabbitMQ pushes messages via the delivery callback above
            while (running && channel.isOpen() && connection.isOpen()) {
                metrics.heartbeat();
                Thread.sleep(5000);
            }
        }
    }

    private DeliverCallback buildDeliverCallback(Channel channel) {
        return (consumerTag, delivery) -> {
            String body = new String(delivery.getBody(), "UTF-8");
            long deliveryTag = delivery.getEnvelope().getDeliveryTag();
            String queueName = delivery.getEnvelope().getRoutingKey();

            // Deserialize
            ChatMessage message;
            try {
                message = JsonUtil.fromJson(body, ChatMessage.class);
            } catch (Exception e) {
                log.error("[{}] Failed to deserialize message from '{}': {}",
                        threadId, queueName, e.getMessage());
                safeAck(channel, deliveryTag); // remove malformed message from queue
                metrics.recordFailure();
                return;
            }

            log.debug("[{}] Received message '{}' for room '{}'.",
                    threadId, message.getMessageId(), message.getRoomId());

            // Deduplication
            if (deduplicationService.isDuplicate(message.getMessageId())) {
                log.info("[{}] Duplicate '{}' — skipping.", threadId, message.getMessageId());
                safeAck(channel, deliveryTag);
                metrics.recordDuplicateSkipped();
                return;
            }

            // Route to RoomManager
            RoomManager.ProcessResult result = roomManager.process(message);

            switch (result) {
                case ACK:
                    deduplicationService.markSeen(message.getMessageId());
                    safeAck(channel, deliveryTag);
                    metrics.recordMessageProcessed();
                    break;

                case NACK:
                    // Requeue — put back for retry
                    safeNack(channel, deliveryTag, true);
                    metrics.recordFailure();
                    break;

                case DISCARD:
                    // Ack to remove — retrying won't help
                    safeAck(channel, deliveryTag);
                    log.warn("[{}] Message '{}' discarded.", threadId, message.getMessageId());
                    break;
            }
        };
    }

    private void safeAck(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("[{}] Failed to ack {}: {}", threadId, deliveryTag, e.getMessage());
        }
    }

    private void safeNack(Channel channel, long deliveryTag, boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (IOException e) {
            log.error("[{}] Failed to nack {}: {}", threadId, deliveryTag, e.getMessage());
        }
    }

    private ConnectionFactory buildConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(config.getRabbitMQHost());
        factory.setPort(config.getRabbitMQPort());
        factory.setUsername(config.getRabbitMQUsername());
        factory.setPassword(config.getRabbitMQPassword());
        factory.setVirtualHost(config.getRabbitMQVirtualHost());
        factory.setAutomaticRecoveryEnabled(false);
        return factory;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() { running = false; }
    public ConsumerMetrics getMetrics() { return metrics; }
    public String getThreadId() { return threadId; }
}