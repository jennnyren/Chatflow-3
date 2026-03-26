package http;

import db.DatabaseManager;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import util.JsonUtil;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public class MetricsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        try {
            Map<String, Object> metrics = collectMetrics();
            resp.getWriter().write(JsonUtil.toJson(metrics));
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write(JsonUtil.toJson(Map.of("error", e.getMessage())));
        }
    }

    private Map<String, Object> collectMetrics() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();

        try (Connection conn = DatabaseManager.getDataSource().getConnection()) {

            // --- CORE QUERIES ---

            result.put("totalMessages", queryLong(conn,
                    "SELECT COUNT(*) FROM messages"));

            result.put("activeUsersLastHour", queryLong(conn,
                    "SELECT COUNT(DISTINCT user_id) FROM messages " +
                            "WHERE timestamp >= NOW() - INTERVAL '1 hour'"));

            result.put("roomsLastActivity", queryList(conn,
                    "SELECT room_id, MAX(timestamp) as last_activity, COUNT(*) as message_count " +
                            "FROM messages GROUP BY room_id ORDER BY last_activity DESC",
                    rs -> Map.of(
                            "roomId", rs.getString("room_id"),
                            "lastActivity", rs.getString("last_activity"),
                            "messageCount", rs.getLong("message_count")
                    )));

            // --- ANALYTICS QUERIES ---

            result.put("messagesPerMinute", queryList(conn,
                    "SELECT DATE_TRUNC('minute', timestamp) as minute, COUNT(*) as count " +
                            "FROM messages WHERE timestamp >= NOW() - INTERVAL '1 hour' " +
                            "GROUP BY minute ORDER BY minute DESC",
                    rs -> Map.of(
                            "minute", rs.getString("minute"),
                            "count", rs.getLong("count")
                    )));

            result.put("topActiveUsers", queryList(conn,
                    "SELECT user_id, username, COUNT(*) as message_count " +
                            "FROM messages GROUP BY user_id, username " +
                            "ORDER BY message_count DESC LIMIT 10",
                    rs -> Map.of(
                            "userId", rs.getString("user_id"),
                            "username", rs.getString("username"),
                            "messageCount", rs.getLong("message_count")
                    )));

            result.put("topActiveRooms", queryList(conn,
                    "SELECT room_id, COUNT(*) as message_count " +
                            "FROM messages GROUP BY room_id " +
                            "ORDER BY message_count DESC LIMIT 10",
                    rs -> Map.of(
                            "roomId", rs.getString("room_id"),
                            "messageCount", rs.getLong("message_count")
                    )));

            result.put("userParticipationPatterns", queryList(conn,
                    "SELECT user_id, message_type, COUNT(*) as count " +
                            "FROM messages GROUP BY user_id, message_type " +
                            "ORDER BY user_id, message_type",
                    rs -> Map.of(
                            "userId", rs.getString("user_id"),
                            "messageType", rs.getString("message_type"),
                            "count", rs.getLong("count")
                    )));
        }

        return result;
    }

    // --- helpers ---

    private long queryLong(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private List<Map<String, Object>> queryList(Connection conn, String sql, RowMapper mapper) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapper.map(rs));
        }
        return list;
    }

    @FunctionalInterface
    interface RowMapper {
        Map<String, Object> map(ResultSet rs) throws SQLException;
    }
}