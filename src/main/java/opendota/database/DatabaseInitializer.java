package opendota.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

public class DatabaseInitializer {
    
    public static void initializeDatabase() throws SQLException {
        // Use Flyway to manage database migrations
        try {
            String host = System.getenv("DB_HOST");
            String port = System.getenv("DB_PORT");
            String database = System.getenv("DB_NAME");
            String username = System.getenv("DB_USER");
            String password = System.getenv("DB_PASSWORD");
            
            // Use defaults if environment variables are not set
            if (host == null) host = "localhost";
            if (port == null) port = "5432";
            if (database == null) database = "dota_parser";
            if (username == null) username = "postgres";
            if (password == null) password = "postgres";
            
            String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
            
            // Configure Flyway
            Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true) // Baseline existing databases
                .baselineVersion("0") // Start baseline at version 0
                .validateOnMigrate(true)
                .load();
            
            // Get migration info
            MigrationInfo[] pendingMigrations = flyway.info().pending();
            MigrationInfo currentVersion = flyway.info().current();
            
            if (currentVersion != null) {
                System.out.println("Current database version: " + currentVersion.getVersion());
            } else {
                System.out.println("Database is not yet versioned. Will apply baseline and migrations.");
            }
            
            if (pendingMigrations.length > 0) {
                System.out.println("Found " + pendingMigrations.length + " pending migration(s).");
            }
            
            // Run migrations
            int migrationsApplied = flyway.migrate().migrationsExecuted;
            
            if (migrationsApplied > 0) {
                System.out.println("Successfully applied " + migrationsApplied + " migration(s).");
            } else {
                System.out.println("Database schema is up to date. No migrations needed.");
            }
            
            // Verify connection
            try (Connection connection = DatabaseConfig.getConnection()) {
                System.out.println("Database connection verified successfully.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during Flyway migration: " + e.getMessage());
            e.printStackTrace();
            throw new SQLException("Failed to initialize database with Flyway", e);
        }
    }
    
    public static void createDatabaseIfNotExists() throws SQLException {
        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        
        if (host == null) host = "localhost";
        if (port == null) port = "5432";
        if (username == null) username = "postgres";
        if (password == null) password = "postgres";
        
        String database = System.getenv("DB_NAME");
        if (database == null) database = "dota_parser";
        
        // Connect to postgres database to create the target database if it doesn't exist
        String postgresUrl = String.format("jdbc:postgresql://%s:%s/postgres", host, port);
        
        try (Connection connection = java.sql.DriverManager.getConnection(postgresUrl, username, password)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + database);
                System.out.println("Database " + database + " created successfully.");
            } catch (SQLException e) {
                if (e.getSQLState().equals("42P04")) { // Database already exists
                    System.out.println("Database " + database + " already exists.");
                } else {
                    throw e;
                }
            }
        }
    }
}
