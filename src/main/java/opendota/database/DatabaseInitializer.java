package opendota.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {
    
    public static void initializeDatabase() throws SQLException {
        // Tables are created via init.sql script mounted in Docker
        // This method can be used for additional runtime initialization if needed
        try (Connection connection = DatabaseConfig.getConnection()) {
            System.out.println("Database connection verified successfully.");
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
