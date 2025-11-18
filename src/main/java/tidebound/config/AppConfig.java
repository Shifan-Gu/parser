package tidebound.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import tidebound.S3Service;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

@Configuration
public class AppConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public S3Service s3Service() {
        return new S3Service();
    }

    /**
     * DataSource bean for Spring Boot auto-configuration.
     * This allows Flyway to automatically run migrations on startup.
     */
    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        LOGGER.info("Configuring DataSource for Flyway migrations");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        return new HikariDataSource(config);
    }

    /**
     * Flyway bean that runs migrations on startup.
     * Spring Boot's auto-configuration will call migrate() automatically.
     * This explicit bean ensures we have control over the configuration.
     */
    @Bean
    @DependsOn("dataSource")
    public Flyway flyway(DataSource dataSource) {
        LOGGER.info("Configuring Flyway for database migrations");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load();
        
        // Log migration status before running
        var info = flyway.info();
        var current = info.current();
        var pending = info.pending();
        
        if (current != null) {
            LOGGER.info("Current database version: {}", current.getVersion());
        } else {
            LOGGER.info("Database is not yet versioned. Will apply baseline and migrations.");
        }
        
        if (pending.length > 0) {
            LOGGER.info("Found {} pending migration(s):", pending.length);
            for (var migration : pending) {
                LOGGER.info("  - {}: {}", migration.getVersion(), migration.getDescription());
            }
        } else {
            LOGGER.info("No pending migrations found.");
        }
        
        // Run migrations explicitly
        var result = flyway.migrate();
        if (result.migrationsExecuted > 0) {
            LOGGER.info("Successfully applied {} migration(s)", result.migrationsExecuted);
        } else {
            LOGGER.info("Database schema is up to date. No migrations needed.");
        }
        
        return flyway;
    }
}

