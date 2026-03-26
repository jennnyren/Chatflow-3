package model;

/**
 * Represents the raw message payload sent by the client over WebSocket.
 * This is the inbound model only; it never gets published to RabbitMQ directly.
 * The server maps it into a MessageEnvelope before publishing.
 */
public class ChatMessage {

    private String username;
    private String userId;
    private String message;
    private String messageType; // TEXT | JOIN | LEAVE

    public ChatMessage() {}

    public ChatMessage(String username, String userId, String message, String messageType) {
        this.username = username;
        this.userId = userId;
        this.message = message;
        this.messageType = messageType;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "username='" + username + '\'' +
                ", message='" + message + '\'' +
                ", messageType='" + messageType + '\'' +
                '}';
    }
}