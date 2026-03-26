package model;

import java.util.ArrayList;
import java.util.List;

public class MessageRound {
    private final String roomId;
    private final String userId;
    private final String username;
    private final List<ChatMessage> messages;
    private int retryCount;
    private final int RETRY_MAX = 5;

    public MessageRound(String roomId, String userId, String username) {
        this.roomId = roomId;
        this.userId = userId;
        this.username = username;
        this.messages = new ArrayList<>();
        this.retryCount = 0;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
    }

    public String getRoomId() {
        return roomId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public int getMessageCount() {
        return messages.size();
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
