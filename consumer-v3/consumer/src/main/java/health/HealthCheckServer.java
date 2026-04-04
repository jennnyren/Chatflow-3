package health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import consumer.ConsumerPool;
import db.DbWriterPool;
import model.ConsumerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.JsonUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight HTTP health check server for Part 2.
 * Runs on port 8082 (Part 1 already uses 8080 and 8081).
 *
 * GET /health → full JSON stats
 * GET /ready  → 200 if all threads healthy, 503 if not
 */
public class HealthCheckServer {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckServer.class);

    private final int port;
    private final ConsumerPool consumerPool;
    private final DbWriterPool dbWriterPool;
    private HttpServer httpServer;

    public HealthCheckServer(int port, ConsumerPool consumerPool, DbWriterPool dbWriterPool) {
        this.port = port;
        this.consumerPool = consumerPool;
        this.dbWriterPool = dbWriterPool;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/health", this::handleHealth);
        httpServer.createContext("/health/reset", this::handleReset);
        httpServer.createContext("/ready", this::handleReady);
        httpServer.start();
        log.info("Health check server started on port {}", port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, JsonUtil.toJson(Map.of("error", "Method not allowed")));
            return;
        }
        try {
        List<ConsumerMetrics> threadMetrics = consumerPool.getAllMetrics();
        boolean allHealthy = threadMetrics.stream().allMatch(ConsumerMetrics::isHealthy);

        long totalConsumed   = threadMetrics.stream().mapToLong(ConsumerMetrics::getMessagesConsumed).sum();
        long totalDiscarded  = threadMetrics.stream().mapToLong(ConsumerMetrics::getMessagesDiscarded).sum();
        long totalDuplicates = threadMetrics.stream().mapToLong(ConsumerMetrics::getDuplicatesSkipped).sum();
        long totalBroadcast  = threadMetrics.stream().mapToLong(ConsumerMetrics::getMessagesBroadcast).sum();
        long totalWrittenDb    = dbWriterPool.getMessagesWrittenToDb();
        log.info("DEBUG totalWrittenDb={}", totalWrittenDb);
        long totalDropped      = dbWriterPool.getMessagesDroppedQueueFull();
        log.info("DEBUG totalDropped={}", totalDropped);
        long totalDeadLettered = dbWriterPool.getMessagesDeadLettered();
        log.info("DEBUG totalDeadLettered={}", totalDeadLettered);

        Map<String, Object> response = new HashMap<>();
        response.put("status", allHealthy ? "UP" : "DEGRADED");
        response.put("threadCount", threadMetrics.size());
        response.put("messagesConsumed", totalConsumed);
        response.put("messagesDiscarded", totalDiscarded);
        response.put("duplicatesSkipped", totalDuplicates);
        response.put("messagesBroadcast", totalBroadcast);
        response.put("messagesWrittenToDb", totalWrittenDb);
        response.put("messagesDroppedDbQueueFull", totalDropped);
        response.put("messagesDeadLettered", totalDeadLettered);
        response.put("threads", threadMetrics);
        sendResponse(exchange, 200, JsonUtil.toJson(response));
        } catch (Throwable t) {
            log.error("handleHealth failed: {}: {}", t.getClass().getName(), t.getMessage(), t);
            sendResponse(exchange, 500, "{\"error\": \"" + t.getMessage() + "\"}");
        }
    }

    private void handleReset(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }
        consumerPool.getAllMetrics().forEach(ConsumerMetrics::reset);
        dbWriterPool.resetMetrics();
        log.info("Metrics reset.");
        sendResponse(exchange, 200, "{\"status\": \"reset\"}");
    }

    private void handleReady(HttpExchange exchange) throws IOException {
        List<ConsumerMetrics> metrics = consumerPool.getAllMetrics();
        boolean allHealthy = metrics.stream().allMatch(ConsumerMetrics::isHealthy);
        sendResponse(exchange, allHealthy ? 200 : 503,
                "{\"ready\": " + allHealthy + "}");
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}