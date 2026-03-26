package model;

public class GeneratedMessage {
    private final ChatMessage message;
    private final String roomId;
    private int retryCount;
    private final int RETRY_MAX = 5;

    public GeneratedMessage(ChatMessage message, String roomId) {
        this.message = message;
        this.roomId = roomId;
        this.retryCount = 0;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public String getRoomId() {
        return roomId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        retryCount++;
    }

    public boolean canRetry() {
        return retryCount < RETRY_MAX;
    }
}
