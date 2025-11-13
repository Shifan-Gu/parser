package tidebound.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public class DotaConstantsDatabaseConfig {
    private static final String SCHEMA = "dota_constants";
    private static final HikariDataSource DATA_SOURCE;

    static {
        DATA_SOURCE = initializeDataSource();
    }

    private DotaConstantsDatabaseConfig() {}

    private static HikariDataSource initializeDataSource() {
        String host = envOrDefault("DOTACONSTANTS_DB_HOST", envOrDefault("DB_HOST", "localhost"));
        String port = envOrDefault("DOTACONSTANTS_DB_PORT", envOrDefault("DB_PORT", "5432"));
        String database = envOrDefault("DOTACONSTANTS_DB_NAME", envOrDefault("DB_NAME", "dota_parser"));
        String username = envOrDefault("DOTACONSTANTS_DB_USER", envOrDefault("DB_USER", "postgres"));
        String password = envOrDefault("DOTACONSTANTS_DB_PASSWORD", envOrDefault("DB_PASSWORD", "postgres"));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setSchema(SCHEMA);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "150");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("reWriteBatchedInserts", "true");

        HikariDataSource dataSource = new HikariDataSource(config);
        ensureSchemaExists(dataSource);
        return dataSource;
    }

    private static void ensureSchemaExists(HikariDataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to ensure presence of schema '" + SCHEMA + "'", exception);
        }
    }

    private static String envOrDefault(String key, String defaultValue) {
        return Objects.requireNonNullElse(System.getenv(key), defaultValue);
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public static void closeDataSource() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }
}


