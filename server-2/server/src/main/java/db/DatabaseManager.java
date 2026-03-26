package db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public class DatabaseManager {

    private static HikariDataSource dataSource;

    public static void init() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://chatflow-db.crtgzfzmeycw.us-east-1.rds.amazonaws.com:5432/postgres");
        //test tunnelling
        //config.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres");
        config.setUsername("postgres");
        config.setPassword("wefRid-0xuqhi-cihzyc");
        config.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(config);
    }

    public static DataSource getDataSource() {
        return dataSource;
    }

    public static void close() {
        if (dataSource != null) dataSource.close();
    }
}