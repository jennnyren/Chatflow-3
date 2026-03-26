import db.DatabaseManager;
import http.HttpServerManager;
import rabbitmq.RabbitMQConnectionManager;
import websocket.ChatWebSocketServer;

/**
 * Part 1 entry point.
 *
 * Starts two servers:
 *   - ChatWebSocketServer on port 8080  → browser clients connect here
 *   - BroadcastHttpServer  on port 8081 → Part 2 consumer calls here to broadcast
 *
 * Shutdown hook gracefully stops both servers and closes RabbitMQ connections.
 */
public class Main {

    private static final int WEBSOCKET_PORT = 8080;
    private static final int BROADCAST_HTTP_PORT = 8081;

    public static void main(String[] args) throws Exception {

        System.out.println("=== Chat Server (Part 1) Starting ===");

        String rabbitHost = System.getenv().getOrDefault("RABBITMQ_HOST", "172.31.47.205");

        DatabaseManager.init();

        RabbitMQConnectionManager rabbitMQConnectionManager = new RabbitMQConnectionManager(
                rabbitHost,
                5672,
                "admin",
                "rabbitmq",
                10
        );

        ChatWebSocketServer webSocketServer = new ChatWebSocketServer(
                WEBSOCKET_PORT, rabbitMQConnectionManager);

        try {
            webSocketServer.start();
            Thread.sleep(1000);
        } catch (Exception e) {
            System.err.println("Failed to start WebSocket server: " + e.getMessage());
            e.printStackTrace();
        }

        HttpServerManager broadcastHttpServer = new HttpServerManager(
                BROADCAST_HTTP_PORT, webSocketServer.getRoomMapping());
        broadcastHttpServer.start();
        System.out.println("Broadcast HTTP server started on port " + BROADCAST_HTTP_PORT);

        System.out.println("=== Chat Server Running ===");
        System.out.println("  WebSocket:  ws://localhost:" + WEBSOCKET_PORT);
        System.out.println("  Broadcast:  http://localhost:" + BROADCAST_HTTP_PORT + "/internal/broadcast");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal received...");
            try {
                broadcastHttpServer.stop();
            } catch (Exception e) {
                System.err.println("Error stopping HTTP server: " + e.getMessage());
            }
            try {
                webSocketServer.stop(1000);
            } catch (Exception e) {
                System.err.println("Error stopping WebSocket server: " + e.getMessage());
            }
            try {
                rabbitMQConnectionManager.close();
            } catch (Exception e) {
                System.err.println("Error stopping RabbitMQ server: " + e.getMessage());
            }

            DatabaseManager.close();

            System.out.println("=== Chat Server Stopped ===");
        }, "shutdown-hook"));

        // Keep main thread alive
        Thread.currentThread().join();
    }
}