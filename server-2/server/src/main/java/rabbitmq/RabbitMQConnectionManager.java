package rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class RabbitMQConnectionManager {

    private volatile Connection connection;
    private volatile BlockingQueue<Channel> channelPool;

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final int poolSize;

    public RabbitMQConnectionManager(String host, int port, String username, String password, int poolSize) throws Exception {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        connectWithBackoff();
    }

    /**
     * Attempts to connect to RabbitMQ with exponential backoff.
     */
    private void connectWithBackoff() throws Exception {
        long backoff = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                System.out.println("RabbitMQ connection attempt " + attempt + "/" + MAX_RETRIES);

                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(host);
                factory.setPort(port);
                factory.setUsername(username);
                factory.setPassword(password);
                this.connection = factory.newConnection();

                BlockingQueue<Channel> newPool = new ArrayBlockingQueue<>(poolSize);
                for (int i = 0; i < poolSize; i++) {
                    newPool.add(connection.createChannel());
                }
                this.channelPool = newPool;

                System.out.println("RabbitMQ connected successfully. Pool size: " + poolSize);
                return;

            } catch (IOException | TimeoutException e) {
                System.err.println("Attempt " + attempt + " failed: " + e.getMessage());

                if (attempt == MAX_RETRIES) {
                    throw new Exception("RabbitMQ unavailable after " + MAX_RETRIES + " attempts.", e);
                }

                System.out.println("Retrying in " + backoff + "ms...");
                Thread.sleep(backoff);
                backoff *= 2; // double the wait: 1s → 2s → 4s → 8s → 16s
            }
        }
    }

    private synchronized void reconnect() {
        // double-check inside synchronized block, another thread may have
        // already reconnected by the time this thread gets the lock
        if (isConnected()) {
            return;
        }

        System.out.println("RabbitMQ connection lost. Attempting reconnect...");
        try {
            connectWithBackoff();
        } catch (Exception e) {
            System.err.println("Reconnect failed: " + e.getMessage());
        }
    }

    public Channel borrowChannel() throws InterruptedException, IOException {
        Channel channel = channelPool.take();

        if (!channel.isOpen()) {
            System.out.println("Channel was closed, replacing...");
            try {
                channel = connection.createChannel();
            } catch (IOException e) {
                // connection itself is dead — reconnect then try again
                reconnect();
                channel = connection.createChannel();
            }
        }

        return channel;
    }

    public void returnChannel(Channel channel) {
        channelPool.offer(channel);
    }

    public boolean isConnected() {
        return connection != null && connection.isOpen();
    }

    public void close() throws Exception {
        for (Channel ch : channelPool) {
            if (ch.isOpen()) ch.close();
        }
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }
}