CREATE TABLE messages (
                          message_id      VARCHAR(36) PRIMARY KEY,
                          room_id         VARCHAR(255) NOT NULL,
                          user_id         VARCHAR(255) NOT NULL,
                          username        VARCHAR(255) NOT NULL,
                          message         TEXT,
                          message_type    VARCHAR(10) NOT NULL CHECK (message_type IN ('JOIN', 'TEXT', 'LEAVE')),
                          timestamp       TIMESTAMPTZ NOT NULL,
                          server_id       VARCHAR(255),
                          client_ip       VARCHAR(45)
);

CREATE TABLE user_room_activity (
                                    user_id         VARCHAR(255) NOT NULL,
                                    room_id         VARCHAR(255) NOT NULL,
                                    last_activity   TIMESTAMPTZ NOT NULL,
                                    message_count   INT DEFAULT 0,
                                    PRIMARY KEY (user_id, room_id)
);

CREATE INDEX idx_messages_room_time ON messages(room_id, timestamp);
CREATE INDEX idx_messages_user_time ON messages(user_id, timestamp);
CREATE INDEX idx_messages_timestamp ON messages(timestamp);
CREATE INDEX idx_messages_type ON messages(message_type, timestamp);