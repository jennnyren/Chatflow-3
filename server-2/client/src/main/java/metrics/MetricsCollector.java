package metrics;

import model.MessageMetric;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsCollector {
    private final Queue<MessageMetric> pendingMetrics;
    private final Queue<MessageMetric> completedMetrics;
    private final Map<String, AtomicInteger> roomMessageCount;
    private final Map<String, AtomicInteger> messageTypeCount;
    private final Map<Long, AtomicInteger> throughputBuckets;

    private final long testStartTime;

    public MetricsCollector() {
        this.pendingMetrics = new ConcurrentLinkedQueue<>();
        this.completedMetrics = new ConcurrentLinkedQueue<>();
        this.roomMessageCount = new ConcurrentHashMap<>();
        this.messageTypeCount = new ConcurrentHashMap<>();
        this.throughputBuckets = new ConcurrentHashMap<>();
        this.testStartTime = System.currentTimeMillis();
    }

    public void recordMessageSent(String messageType, String roomId) {
        long timestamp = System.currentTimeMillis();
        MessageMetric metric = new MessageMetric(timestamp, messageType, roomId);
        pendingMetrics.offer(metric);

        messageTypeCount.computeIfAbsent(messageType, k -> new AtomicInteger(0)).incrementAndGet();

        roomMessageCount.computeIfAbsent(roomId, k -> new AtomicInteger(0)).incrementAndGet();

        long bucketKey = (timestamp - testStartTime) / 10000; // 10-second buckets
        throughputBuckets.computeIfAbsent(bucketKey, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void recordAcknowledgment(long ackTime, String status, String roomId) {
        MessageMetric metric = pendingMetrics.poll();
        if (metric != null) {
            metric.setAcknowledgment(ackTime, status);
            completedMetrics.offer(metric);
        }
    }

    public void writeMetricsToCSV(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("timestamp,messageType,latency,statusCode,roomId");

            for (MessageMetric metric : completedMetrics) {
                writer.println(metric.toCSV());
            }

            System.out.println("Metrics written to " + filename);
        }
    }

    public StatisticalAnalysis calculateStatistics() {
        List<Long> latencies = new ArrayList<>();

        for (MessageMetric metric : completedMetrics) {
            latencies.add(metric.getLatencyMs());
        }

        if (latencies.isEmpty()) {
            return new StatisticalAnalysis();
        }

        Collections.sort(latencies);

        StatisticalAnalysis stats = new StatisticalAnalysis();
        stats.totalMessages = latencies.size();
        stats.minLatency = latencies.get(0);
        stats.maxLatency = latencies.get(latencies.size() - 1);

        // Calculate mean
        long sum = 0;
        for (long latency : latencies) {
            sum += latency;
        }
        stats.meanLatency = sum / (double) latencies.size();

        // Calculate median
        int medianIndex = latencies.size() / 2;
        if (latencies.size() % 2 == 0) {
            stats.medianLatency = (latencies.get(medianIndex - 1) + latencies.get(medianIndex)) / 2.0;
        } else {
            stats.medianLatency = latencies.get(medianIndex);
        }

        // Calculate percentiles
        stats.p95Latency = latencies.get((int) (latencies.size() * 0.95));
        stats.p99Latency = latencies.get((int) (latencies.size() * 0.99));

        // Per-room throughput
        stats.roomThroughput = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : roomMessageCount.entrySet()) {
            stats.roomThroughput.put(entry.getKey(), entry.getValue().get());
        }

        // Message type distribution
        stats.messageTypeDistribution = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : messageTypeCount.entrySet()) {
            stats.messageTypeDistribution.put(entry.getKey(), entry.getValue().get());
        }

        // Throughput over time
        stats.throughputOverTime = new TreeMap<>();
        for (Map.Entry<Long, AtomicInteger> entry : throughputBuckets.entrySet()) {
            stats.throughputOverTime.put(entry.getKey(), entry.getValue().get());
        }

        return stats;
    }

    public static class StatisticalAnalysis {
        public int totalMessages;
        public long minLatency;
        public long maxLatency;
        public double meanLatency;
        public double medianLatency;
        public long p95Latency;
        public long p99Latency;
        public Map<String, Integer> roomThroughput;
        public Map<String, Integer> messageTypeDistribution;
        public Map<Long, Integer> throughputOverTime;

        public void printReport() {
            System.out.println("STATISTICAL ANALYSIS REPORT");
            System.out.println("-------------------------------------");
            System.out.println("Response Time Statistics:");
            System.out.println("Mean: " + String.format("%.2f", meanLatency) + " ms");
            System.out.println("Median: " + String.format("%.2f", medianLatency) + " ms");
            System.out.println("95th Percentile: " + p95Latency + " ms");
            System.out.println("99th Percentile: " + p99Latency + " ms");
            System.out.println("Min: " + minLatency + " ms");
            System.out.println("Max: " + maxLatency + " ms");

            System.out.println("\nMessage Type Distribution:");
            for (Map.Entry<String, Integer> entry : messageTypeDistribution.entrySet()) {
                double percentage = (entry.getValue() * 100.0) / totalMessages;
                System.out.println("" + entry.getKey() + ": " + entry.getValue() +
                        " (" + String.format("%.1f", percentage) + "%)");
            }

            System.out.println("\nThroughput Per Room:");
            for (Map.Entry<String, Integer> entry : roomThroughput.entrySet()) {
                System.out.println("Room " + entry.getKey() + ": " + entry.getValue() + " messages");
            }
        }

        public void printThroughputChart() {
            System.out.println("\n------------------------------------");
            System.out.println("THROUGHPUT OVER TIME (msg/10sec)");

            if (throughputOverTime.isEmpty()) {
                System.out.println("No data available");
                return;
            }

            // Find max for scaling
            int maxThroughput = throughputOverTime.values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(1);

            int chartWidth = 50;

            for (Map.Entry<Long, Integer> entry : throughputOverTime.entrySet()) {
                long bucket = entry.getKey();
                int count = entry.getValue();
                int barLength = (int) ((count / (double) maxThroughput) * chartWidth);

                String bar = "█".repeat(Math.max(0, barLength));
                System.out.printf("%3d-%3ds |%-" + chartWidth + "s| %d msg/s%n",
                        bucket * 10, (bucket + 1) * 10, bar, count / 10);
            }
            System.out.println("-----------------------------------\n");
        }
    }
}
