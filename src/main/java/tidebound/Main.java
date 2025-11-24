package tidebound;

import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;

import com.sun.net.httpserver.HttpServer;

import tidebound.database.DatabaseInitializer;
import tidebound.handler.BlobHandler;
import tidebound.handler.HealthHandler;
import tidebound.handler.LocalReplayHandler;
import tidebound.handler.ParseHandler;
import tidebound.handler.SwaggerSpecHandler;
import tidebound.handler.SwaggerUIHandler;

/**
 * Main entry point for the replay parser HTTP server.
 */
public class Main {
    
    // Server configuration
    private static final int SERVER_PORT = 5600;
    private static final int REGISTRATION_INTERVAL_MS = 5000;

    public static void main(String[] args) throws Exception {
        initializeDatabaseOnStartup();
        HttpServer server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
        server.createContext("/", new ParseHandler());
        server.createContext("/healthz", new HealthHandler());
        server.createContext("/blob", new BlobHandler());
        server.createContext("/local", new LocalReplayHandler());
        server.createContext("/swagger/openapi.json", new SwaggerSpecHandler());
        server.createContext("/swagger", new SwaggerUIHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        // Re-register ourselves
        Timer timer = new Timer(); 
        TimerTask task = new RegisterTask(); 
        timer.schedule(task, 0, REGISTRATION_INTERVAL_MS);
    }

    /**
     * Initializes the database on startup if enabled via environment variable.
     * 
     * @throws Exception If database initialization fails
     */
    private static void initializeDatabaseOnStartup() throws Exception {
        String dbEnabled = System.getenv("DB_ENABLED");
        boolean databaseEnabled = "true".equalsIgnoreCase(dbEnabled) || "1".equals(dbEnabled);

        if (!databaseEnabled) {
            System.err.println("Database integration disabled. Set DB_ENABLED=true to enable.");
            return;
        }

        try {
            DatabaseInitializer.createDatabaseIfNotExists();
        } catch (Exception e) {
            System.err.println("Warning: Could not create database: " + e.getMessage());
        }

        try {
            DatabaseInitializer.initializeDatabase();
        } catch (Exception e) {
            throw new RuntimeException("Failed to run database migrations", e);
        }
    }
}