package dedup;

import config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

/**
 * Prevents the same message from being broadcast twice using Redis.
 *
 * Flow:
 * 1. isDuplicate(messageId) → false  → process message
 * 2. broadcast succeeds
 * 3. markSeen(messageId)             → write key with TTL
 * 4. isDuplicate(messageId) → true   → skip on any retry
 *
 * Redis key: "seen:{messageId}", TTL: configurable (default 24h)
 */
public class DeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);
    private static final String KEY_PREFIX = "seen:";

    private final JedisPool jedisPool;
    private final int ttlSeconds;

    public DeduplicationService(AppConfig config) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);

        String password = config.getRedisPassword();
        if (password != null && !password.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, config.getRedisHost(),
                    config.getRedisPort(), 2000, password);
        } else {
            this.jedisPool = new JedisPool(poolConfig, config.getRedisHost(),
                    config.getRedisPort(), 2000);
        }

        this.ttlSeconds = config.getDedupTtlSeconds();
        log.info("DeduplicationService ready. Redis: {}:{}", config.getRedisHost(), config.getRedisPort());
    }

    /**
     * Returns true if this messageId was already processed.
     * On Redis failure, returns false (allow through — better duplicate than drop).
     */
    public boolean isDuplicate(String messageId) {
        if (messageId == null || messageId.isEmpty()) return false;
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(KEY_PREFIX + messageId);
        } catch (Exception e) {
            log.error("Redis error in isDuplicate for '{}'. Allowing through. Error: {}", messageId, e.getMessage());
            return false;
        }
    }

    /**
     * Marks messageId as seen. Call AFTER successful broadcast.
     * Uses NX (set only if not exists) to handle concurrent threads safely.
     */
    public void markSeen(String messageId) {
        if (messageId == null || messageId.isEmpty()) return;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(KEY_PREFIX + messageId, "1", SetParams.setParams().ex(ttlSeconds).nx());
        } catch (Exception e) {
            log.error("Redis error in markSeen for '{}': {}", messageId, e.getMessage());
        }
    }

    public void close() {
        jedisPool.close();
    }
}