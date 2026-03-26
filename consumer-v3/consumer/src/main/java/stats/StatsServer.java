package stats;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import db.DbWriterPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight HTTP server for stats reporting.
 * Runs on port 8083.
 *
 * POST /stats/report → logs final throughput and DB write latency percentiles, returns 200 OK
 */
public class StatsServer {

    private static final Logger log = LoggerFactory.getLogger(StatsServer.class);

    private static final int PORT = 8083;

    private final StatsAggregatorPool statsAggregatorPool;
    private final DbWriterPool dbWriterPool;
    private HttpServer httpServer;

    public StatsServer(StatsAggregatorPool statsAggregatorPool, DbWriterPool dbWriterPool) {
        this.statsAggregatorPool = statsAggregatorPool;
        this.dbWriterPool = dbWriterPool;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(PORT), 0);
        httpServer.createContext("/stats/report", this::handleReport);
        httpServer.start();
        log.info("Stats server started on port {}", PORT);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
        }
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }

        statsAggregatorPool.reportFinalThroughput();
        dbWriterPool.reportLatencyPercentiles();
        sendResponse(exchange, 200, "{\"status\": \"ok\"}");
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