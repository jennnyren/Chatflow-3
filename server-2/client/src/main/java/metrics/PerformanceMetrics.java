package metrics;

public class PerformanceMetrics {
    private int successfulMessages;
    private int failedMessages;
    private long totalRuntimeMs;
    private double throughput;
    private int ConnectionCount;
    private int reconnectCount;
    private int activeConnections;

    public void setSuccessfulMessages(int count) { this.successfulMessages = count; }
    public void setFailedMessages(int count) { this.failedMessages = count; }
    public void setTotalRuntimeMs(long ms) { this.totalRuntimeMs = ms; }
    public void setThroughput(double throughput) { this.throughput = throughput; }
    public void setConnectionCount(int count) { this.ConnectionCount = count; }
    public void setReconnectCount(int count) { this.reconnectCount = count; }
    public void setActiveConnections(int count) { this.activeConnections = count; }

    public void printReport() {
        System.out.println("----------------------------------------");
        System.out.println("PERFORMANCE METRICS REPORT       ");
        System.out.println("Successful Messages: " + successfulMessages);
        System.out.println("Failed Messages: " + failedMessages);
        System.out.println("Total Runtime: " + totalRuntimeMs + " ms (" +
                (totalRuntimeMs / 1000.0) + " seconds)");
        System.out.println("Overall Throughput: " + String.format("%.2f", throughput) +
                " messages/second");
        System.out.println("Total Connections: " + ConnectionCount);
        System.out.println("Reconnections: " + reconnectCount);
        System.out.println("Active Connections: " + activeConnections);
        System.out.println("----------------------------------------\n");
    }
}