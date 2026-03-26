package model;

public class ChatResponse {
    private String status;
    private String serverTimestamp;
    private ChatMessage originalMessage;
    private String error;

    public ChatResponse(){
    }

    public ChatResponse(String status, ChatMessage originalMessage) {
        this.status = status;
        this.serverTimestamp = java.time.Instant.now().toString();
        this.originalMessage = originalMessage;
    }

    public ChatResponse(String status, String error) {
        this.status = status;
        this.serverTimestamp = java.time.Instant.now().toString();
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(String serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    public ChatMessage getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(ChatMessage originalMessage) {
        this.originalMessage = originalMessage;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
