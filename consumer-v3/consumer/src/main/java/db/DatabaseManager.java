package db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public class DatabaseManager {
    private static HikariDataSource dataSource;

    public static void init(String jdbcUrl, String username, String password, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(poolSize / 2);
        config.setConnectionTimeout(3000);
        dataSource = new HikariDataSource(config);
    }

    public static DataSource getDataSource() { return dataSource; }

    public static void close() {
        if (dataSource != null) dataSource.close();
    }
}