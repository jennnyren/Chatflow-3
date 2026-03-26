package http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.java_websocket.WebSocket;
import util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class BroadcastServlet extends HttpServlet {
    private final ConcurrentHashMap<WebSocket, String> roomMapping;

    public BroadcastServlet(ConcurrentHashMap<WebSocket, String> roomMapping) {
        this.roomMapping = roomMapping;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // read body using servlet API
        String body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // JSON parsing logic
        String roomId;
        String message;
        try {
            Map<String, String> payload = JsonUtil.fromJson(body, Map.class);
            roomId = payload.get("roomId");
            message = payload.get("message");

            if (roomId == null || roomId.isEmpty() || message == null || message.isEmpty()) {
                resp.setStatus(400);
                resp.setContentType("application/json");
                resp.getWriter().write(JsonUtil.toJson(Map.of("error", "roomId and message are required")));
                return;
            }
        } catch (Exception e) {
            resp.setStatus(400);
            resp.setContentType("application/json");
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", "Invalid JSON: " + e.getMessage())));
            return;
        }

        // broadcast loop
        int sent = 0;
        int failed = 0;
        for (Map.Entry<WebSocket, String> entry : roomMapping.entrySet()) {
            if (roomId.equals(entry.getValue())) {
                WebSocket conn = entry.getKey();
                try {
                    if (conn.isOpen()) {
                        conn.send(message);
                        sent++;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to send to client in room " + roomId + ": " + e.getMessage());
                    failed++;
                }
            }
        }

        // Build response using JsonUtil
        Map<String, Object> result = new HashMap<>();
        result.put("sent", sent);
        result.put("failed", failed);
        result.put("roomId", roomId);
        resp.setStatus(200);
        resp.setContentType("application/json");
        resp.getWriter().write(JsonUtil.toJson(result));
    }
}
