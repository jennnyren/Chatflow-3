package model;

public class MessageMetric {
    private final long timestamp;
    private final String messageType;
    private final String roomId;
    private long latencyMs;
    private String statusCode;
    private long acknowledgmentTime;

    public MessageMetric(long timestamp, String messageType, String roomId) {
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.roomId = roomId;
        this.statusCode = "PENDING";
    }

    public void setAcknowledgment(long ackTime, String status) {
        this.acknowledgmentTime = ackTime;
        this.statusCode = status;
        this.latencyMs = ackTime - timestamp;
    }

    public long getTimestamp() { return timestamp; }
    public String getMessageType() { return messageType; }
    public String getRoomId() { return roomId; }
    public long getLatencyMs() { return latencyMs; }
    public String getStatusCode() { return statusCode; }

    public String toCSV() {
        return timestamp + "," + messageType + "," + latencyMs + "," + statusCode + "," + roomId;
    }
}
