import config.AppConfig;
import consumer.ConsumerPool;
import db.DatabaseManager;
import db.DbWriterPool;
import dedup.DeduplicationService;
import health.HealthCheckServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import room.RoomManager;
import stats.StatsAggregatorPool;
import stats.StatsServer;
import websocket.WebSocketBroadcaster;

import java.util.ArrayList;
import java.util.List;

/**
 * Part 2 entry point.
 *
 * Startup sequence:
 * 1. Load config
 * 2. Initialize DeduplicationService (Redis)
 * 3. Initialize WebSocketBroadcaster (HTTP client pointed at Part 1)
 * 4. Wire RoomManager
 * 5. Start ConsumerPool (begins reading from RabbitMQ)
 * 6. Start HealthCheck server
 * 7. Start Stats server
 * 8. Register shutdown hook
 *
 * Usage:
 *   java -jar consumer.jar
 *   java -jar consumer.jar --config /etc/consumer/config.properties
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        log.info("=== Chat Consumer (Part 2) Starting ===");

        // Config
        AppConfig config = loadConfig(args);
        List<String> rooms = parseRooms(args);

        // db
        DatabaseManager.init(
                config.getDbUrl(),
                config.getDbUsername(),
                config.getDbPassword(),
                config.getDbPoolSize()
        );

        DbWriterPool dbWriterPool = new DbWriterPool(
                config.getDbWriterThreadCount(),
                config.getDbWriterQueueCapacity()
        );
        StatsAggregatorPool statsAggregatorPool = new StatsAggregatorPool(
                config.getStatsThreadCount(),
                config.getStatsQueueCapacity()
        );

        // Redis deduplication
        DeduplicationService deduplicationService = new DeduplicationService(config);

        // WebSocket broadcaster (calls Part 1 via HTTP)
        WebSocketBroadcaster broadcaster = new WebSocketBroadcaster(config);

        // Room manager
        RoomManager roomManager = new RoomManager(broadcaster, config, dbWriterPool, statsAggregatorPool);

        // Consumer pool
        ConsumerPool consumerPool = new ConsumerPool(config, roomManager, deduplicationService);
        consumerPool.start(rooms);

        // Health check server
        HealthCheckServer healthCheckServer = new HealthCheckServer(
                config.getHealthCheckPort(), consumerPool);
        healthCheckServer.start();

        // Stats server
        StatsServer statsServer = new StatsServer(statsAggregatorPool, dbWriterPool);
        statsServer.start();

        log.info("=== Chat Consumer (Part 2) Running ===");
        log.info("  Rooms:         {}", rooms);
        log.info("  Part 1 target: {}", config.getPart1BroadcastUrl());
        log.info("  Health check:  http://localhost:{}/health", config.getHealthCheckPort());
        log.info("  Stats report:  http://localhost:8083/stats/report");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received...");
            consumerPool.shutdown();
            dbWriterPool.shutdown();
            statsAggregatorPool.shutdown();
            statsServer.stop();
            healthCheckServer.stop();
            deduplicationService.close();
            DatabaseManager.close();
            log.info("=== Chat Consumer (Part 2) Stopped ===");
        }, "shutdown-hook"));

        Thread.currentThread().join();
    }

    private static AppConfig loadConfig(String[] args) throws Exception {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                log.info("Loading config from: {}", args[i + 1]);
                return new AppConfig(args[i + 1]);
            }
        }
        log.info("Loading config from classpath.");
        return new AppConfig();
    }

    private static List<String> parseRooms(String[] args) {
        List<String> rooms = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            rooms.add("room." + i);
        }
        return rooms;
    }
}