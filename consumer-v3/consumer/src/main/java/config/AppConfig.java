package config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads config.properties at startup and exposes typed getters.
 * Every other class takes an AppConfig in its constructor.
 */
public class AppConfig {

    private final Properties props;

    public AppConfig(String filePath) throws IOException {
        props = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            props.load(fis);
        }
    }

    public AppConfig() throws IOException {
        props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) throw new IOException("config.properties not found on classpath");
            props.load(is);
        }
    }

    // RabbitMQ
    public String getRabbitMQHost() {
        String env = System.getenv("RABBITMQ_HOST");
        return env != null ? env : props.getProperty("rabbitmq.host", "localhost");
    }

    public int getRabbitMQPort() {
        String env = System.getenv("RABBITMQ_PORT");
        return Integer.parseInt(env != null ? env : props.getProperty("rabbitmq.port", "5672"));
    }

    public String getRabbitMQUsername() {
        String env = System.getenv("RABBITMQ_USERNAME");
        return env != null ? env : props.getProperty("rabbitmq.username", "admin");
    }

    public String getRabbitMQPassword() {
        String env = System.getenv("RABBITMQ_PASSWORD");
        return env != null ? env : props.getProperty("rabbitmq.password", "rabbitmq");
    }

    public String getRabbitMQVirtualHost() {
        String env = System.getenv("RABBITMQ_VIRTUALHOST");
        return env != null ? env : props.getProperty("rabbitmq.virtualhost", "/");
    }

    public long getRabbitMQReconnectDelayMs() {
        String env = System.getenv("RABBITMQ_RECONNECT_DELAY_MS");
        return Long.parseLong(env != null ? env : props.getProperty("rabbitmq.reconnect.delay.ms", "5000"));
    }

    // Redis

    public String getRedisHost() {
        String env = System.getenv("REDIS_HOST");
        return env != null ? env : props.getProperty("redis.host", "localhost");
    }

    public int getRedisPort() {
        String env = System.getenv("REDIS_PORT");
        return Integer.parseInt(env != null ? env : props.getProperty("redis.port", "6379"));
    }

    public String getRedisPassword() {
        String env = System.getenv("REDIS_PASSWORD");
        return env != null ? env : props.getProperty("redis.password", "");
    }

    public int getDedupTtlSeconds() {
        String env = System.getenv("REDIS_DEDUP_TTL_SECONDS");
        return Integer.parseInt(env != null ? env : props.getProperty("redis.dedup.ttl.seconds", "86400"));
    }

    // Database
    public String getDbUrl() {
        String env = System.getenv("DB_URL");
        return env != null ? env : props.getProperty("db.url",
                "jdbc:postgresql://chatflow-db.crtgzfzmeycw.us-east-1.rds.amazonaws.com:5432/postgres");
    }

    public String getDbUsername() {
        String env = System.getenv("DB_USERNAME");
        return env != null ? env : props.getProperty("db.username", "postgres");
    }

    public String getDbPassword() {
        String env = System.getenv("DB_PASSWORD");
        return env != null ? env : props.getProperty("db.password", "");
    }

    public int getDbWriterThreadCount() {
        String env = System.getenv("DB_WRITER_THREAD_COUNT");
        return Integer.parseInt(env != null ? env : props.getProperty("db.writer.thread.count", "4"));
    }

    public int getDbWriterQueueCapacity() {
        String env = System.getenv("DB_WRITER_QUEUE_CAPACITY");
        return Integer.parseInt(env != null ? env : props.getProperty("db.writer.queue.capacity", "10000"));
    }

    public int getDbPoolSize() {
        String env = System.getenv("DB_POOL_SIZE");
        return Integer.parseInt(env != null ? env : props.getProperty("db.pool.size", "20"));
    }

    // stats
    public int getStatsThreadCount() {
        String env = System.getenv("STATS_THREAD_COUNT");
        return Integer.parseInt(env != null ? env : props.getProperty("stats.thread.count", "2"));
    }

    public int getStatsQueueCapacity() {
        String env = System.getenv("STATS_QUEUE_CAPACITY");
        return Integer.parseInt(env != null ? env : props.getProperty("stats.queue.capacity", "10000"));
    }

    // Part 1 Broadcast Callback

    /**
     * The URL of Part 1's internal broadcast endpoint.
     * Part 2 POSTs to this URL to trigger broadcasts to WebSocket clients.
     * Example: http://10.0.1.5:8081/internal/broadcast
     */
    public String getPart1BroadcastUrl() {
        String env = System.getenv("PART1_BROADCAST_URL");
        return env != null ? env : props.getProperty("part1.broadcast.url", "http://localhost:8081/internal/broadcast");
    }

    // Consumer Pool

    public int getConsumerThreadCount() {
        String env = System.getenv("CONSUMER_THREAD_COUNT");
        return Integer.parseInt(env != null ? env : props.getProperty("consumer.thread.count", "4"));
    }

    // Health Check

    public int getHealthCheckPort() {
        String env = System.getenv("HEALTHCHECK_PORT");
        return Integer.parseInt(env != null ? env : props.getProperty("healthcheck.port", "8082"));
    }

    // Message Processing
    public int getMessageRetryMax() {
        String env = System.getenv("MESSAGE_RETRY_MAX");
        return Integer.parseInt(env != null ? env : props.getProperty("message.retry.max", "3"));
    }

    public long getMessageRetryDelayMs() {
        String env = System.getenv("MESSAGE_RETRY_DELAY_MS");
        return Long.parseLong(env != null ? env : props.getProperty("message.retry.delay.ms", "500"));
    }
}