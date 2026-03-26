package websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import model.ChatMessage;
import model.ChatResponse;
import model.MessageEnvelope;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import rabbitmq.RabbitMQConnectionManager;
import util.JsonUtil;
import validator.MessageValidator;
import validator.ValidationResult;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWebSocketServer extends WebSocketServer {

    private static final String EXCHANGE_NAME = "chat.exchange";
    private final ConcurrentHashMap<WebSocket, String> roomMapping;
    private final RabbitMQConnectionManager rabbitMQConnectionManager;

    /**
     * Unique ID for this server instance.
     * Generated once at startup — useful for tracing which instance
     * handled a message when running multiple instances later.
     */
    private final String serverId = "server-" + UUID.randomUUID().toString().substring(0, 8);

    public ChatWebSocketServer(int port, RabbitMQConnectionManager rabbitMQConnectionManager) {
        super(new InetSocketAddress(port));
        this.roomMapping = new ConcurrentHashMap<>();
        this.rabbitMQConnectionManager = rabbitMQConnectionManager;
        System.out.println("Server instance ID: " + serverId);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String uri = handshake.getResourceDescriptor();
        String roomId = extractRoomId(uri);

        if (roomId != null) {
            roomMapping.put(conn, roomId);
            System.out.println("New connection to room: " + roomId);
        } else {
            System.out.println("Invalid connection from: " + conn.getRemoteSocketAddress());
            conn.close(1003, "Invalid room path");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String roomId = roomMapping.remove(conn);
        System.out.println("Disconnected from room: " + roomId);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Received message: " + message);
        try {
            // parse what the client sent
            ChatMessage chatMessage = JsonUtil.fromJson(message, ChatMessage.class);

            // validate
            ValidationResult validationResult = MessageValidator.validate(chatMessage);
            if (!validationResult.isValid()) {
                sendError(conn, validationResult.getMessage());
                return;
            }

            // check RabbitMQ is available
            if (!rabbitMQConnectionManager.isConnected()) {
                sendError(conn, "Message service temporarily unavailable. Please try again.");
                return;
            }

            // extract connection metadata
            String roomId = roomMapping.get(conn);
            String clientIp = conn.getRemoteSocketAddress().getAddress().getHostAddress();
            String routingKey = "room." + roomId;

            // map MessageEnvelope
            MessageEnvelope envelope = new MessageEnvelope(
                    UUID.randomUUID().toString(),
                    roomId,
                    chatMessage.getUserId(),
                    chatMessage.getUsername(),
                    chatMessage.getMessage(),
                    Instant.now().toString(),
                    chatMessage.getMessageType(),
                    serverId,
                    clientIp
            );

            // serialize envelope to JSON
            byte[] body = JsonUtil.toJson(envelope).getBytes(StandardCharsets.UTF_8);

            // set deliveryMode=2, survives RabbitMQ restart)
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2)
                    .contentType("application/json")
                    .build();

            Channel channel = rabbitMQConnectionManager.borrowChannel();
            try {
                channel.basicPublish(EXCHANGE_NAME, routingKey, props, body);
                System.out.println("Published to RabbitMQ [" + routingKey + "]: " + envelope.getMessageId());
            } finally {
                rabbitMQConnectionManager.returnChannel(channel);
            }

        } catch (JsonProcessingException e) {
            sendError(conn, "Invalid JSON format: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to publish to RabbitMQ: " + e.getMessage());
            sendError(conn, "Failed to deliver message. Please try again.");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port " + getPort());
        setConnectionLostTimeout(100);
    }

    private void sendError(WebSocket conn, String errorMessage) {
        try {
            ChatResponse response = new ChatResponse("ERROR", errorMessage);
            conn.send(JsonUtil.toJson(response));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String extractRoomId(String uri) {
        if (uri != null && uri.startsWith("/chat/")) {
            String[] parts = uri.split("/");
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        return null;
    }

    public ConcurrentHashMap<WebSocket, String> getRoomMapping() {
        return roomMapping;
    }
}