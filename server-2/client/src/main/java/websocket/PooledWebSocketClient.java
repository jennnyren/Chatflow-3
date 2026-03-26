package websocket;

import metrics.MetricsCollector;
import model.ChatResponse;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import util.JsonUtil;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PooledWebSocketClient extends WebSocketClient {
    private final String roomId;
    private final CountDownLatch connectLatch;
    private final AtomicBoolean isReady;
    private final AtomicLong messagesSent;
    private final AtomicLong messagesReceived;
    private final MetricsCollector metrics;

    public PooledWebSocketClient(URI serverUri, String roomId, MetricsCollector metrics) {
        super(serverUri);
        this.roomId = roomId;
        this.connectLatch = new CountDownLatch(1);
        this.isReady = new AtomicBoolean(false);
        this.messagesSent = new AtomicLong(0);
        this.messagesReceived = new AtomicLong(0);
        this.metrics = metrics;

        setConnectionLostTimeout(10);
    }

    public PooledWebSocketClient(URI serverUri, String roomId) {
        this(serverUri, roomId, null);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        isReady.set(true);
        connectLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        messagesReceived.incrementAndGet();
        System.out.println("Echoed message received: " + message);

        if (metrics != null) {
            long ackTime = System.currentTimeMillis();

            // Parse the response to get status
            try {
                ChatResponse response = JsonUtil.fromJson(message, ChatResponse.class);
                String status = response.getStatus(); // "success" or "error"
                metrics.recordAcknowledgment(ackTime, status, roomId);
            } catch (Exception e) {
                // If parsing fails, still record it
                metrics.recordAcknowledgment(ackTime, "unknown", roomId);
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        isReady.set(false);
    }

    @Override
    public void onError(Exception ex) {
        isReady.set(false);
        connectLatch.countDown();
    }

    public boolean waitForConnection(long timeout, TimeUnit unit) throws InterruptedException {
        return connectLatch.await(timeout, unit);
    }

    public boolean isReady() {
        return isReady.get() && isOpen();
    }

    public String getRoomId() {
        return roomId;
    }

    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    public long getMessagesSent() {
        return messagesSent.get();
    }

    public long getMessagesReceived() {
        return messagesReceived.get();
    }
}
