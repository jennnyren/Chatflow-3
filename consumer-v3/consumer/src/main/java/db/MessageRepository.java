package db;

import model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

public class MessageRepository {
    private static final Logger log = LoggerFactory.getLogger(MessageRepository.class);

    private static final String UPSERT_MESSAGE = """
        INSERT INTO messages
            (message_id, room_id, user_id, username, message, message_type, timestamp, server_id, client_ip)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (message_id) DO NOTHING
    """;

    private static final String UPSERT_ACTIVITY = """
        INSERT INTO user_room_activity (user_id, room_id, last_activity, message_count)
        VALUES (?, ?, ?, 1)
        ON CONFLICT (user_id, room_id) DO UPDATE
            SET last_activity = EXCLUDED.last_activity,
                message_count = user_room_activity.message_count + 1
    """;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 200;

    public void save(ChatMessage msg) {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (attempt < MAX_RETRIES) {
            try (Connection conn = DatabaseManager.getDataSource().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    insertMessage(conn, msg);
                    upsertActivity(conn, msg);
                    conn.commit();
                    return; // success
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    // dead letter — log and give up
                    log.error("DEAD LETTER: Failed to save message '{}' after {} attempts: {}",
                            msg.getMessageId(), MAX_RETRIES, e.getMessage());
                    return;
                }
                log.warn("DB write attempt {}/{} failed for '{}', retrying in {}ms",
                        attempt, MAX_RETRIES, msg.getMessageId(), backoff);
                try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff *= 2; // exponential backoff
            }
        }
    }

    private void insertMessage(Connection conn, ChatMessage msg) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_MESSAGE)) {
            ps.setString(1, msg.getMessageId());
            ps.setString(2, msg.getRoomId());
            ps.setString(3, msg.getUserId());
            ps.setString(4, msg.getUsername());
            ps.setString(5, msg.getMessage());
            ps.setString(6, msg.getMessageType());
            ps.setObject(7, OffsetDateTime.parse(msg.getTimestamp()));
            ps.setString(8, msg.getServerId());
            ps.setString(9, msg.getClientIp());
            ps.executeUpdate();
        }
    }

    private void upsertActivity(Connection conn, ChatMessage msg) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_ACTIVITY)) {
            ps.setString(1, msg.getUserId());
            ps.setString(2, msg.getRoomId());
            ps.setObject(3, OffsetDateTime.parse(msg.getTimestamp()));
            ps.executeUpdate();
        }
    }

    public void saveAll(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return;

        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (attempt < MAX_RETRIES) {
            try (Connection conn = DatabaseManager.getDataSource().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    batchInsertMessages(conn, messages);
                    batchUpsertActivities(conn, messages);
                    conn.commit();
                    return; // success
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    log.error("DEAD LETTER: Failed to save batch of {} messages after {} attempts: {}",
                            messages.size(), MAX_RETRIES, e.getMessage());
                    return;
                }
                log.warn("DB batch write attempt {}/{} failed, retrying in {}ms",
                        attempt, MAX_RETRIES, backoff);
                try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff *= 2;
            }
        }
    }

    private void batchInsertMessages(Connection conn, List<ChatMessage> messages) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_MESSAGE)) {
            for (ChatMessage msg : messages) {
                ps.setString(1, msg.getMessageId());
                ps.setString(2, msg.getRoomId());
                ps.setString(3, msg.getUserId());
                ps.setString(4, msg.getUsername());
                ps.setString(5, msg.getMessage());
                ps.setString(6, msg.getMessageType());
                ps.setObject(7, OffsetDateTime.parse(msg.getTimestamp()));
                ps.setString(8, msg.getServerId());
                ps.setString(9, msg.getClientIp());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void batchUpsertActivities(Connection conn, List<ChatMessage> messages) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_ACTIVITY)) {
            for (ChatMessage msg : messages) {
                ps.setString(1, msg.getUserId());
                ps.setString(2, msg.getRoomId());
                ps.setObject(3, OffsetDateTime.parse(msg.getTimestamp()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}