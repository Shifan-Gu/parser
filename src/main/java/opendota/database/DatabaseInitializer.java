package opendota.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {
    
    private static final String CREATE_TABLES_SQL = 
        "CREATE TABLE IF NOT EXISTS game_events (" +
        "    id BIGSERIAL PRIMARY KEY," +
        "    match_id BIGINT NOT NULL," +
        "    time INTEGER NOT NULL," +
        "    type VARCHAR(50)," +
        "    team INTEGER," +
        "    unit VARCHAR(100)," +
        "    key TEXT," +
        "    value INTEGER," +
        "    slot INTEGER," +
        "    player_slot INTEGER," +
        "    player1 INTEGER," +
        "    player2 INTEGER," +
        "    attackername VARCHAR(100)," +
        "    targetname VARCHAR(100)," +
        "    sourcename VARCHAR(100)," +
        "    targetsourcename VARCHAR(100)," +
        "    attackerhero BOOLEAN," +
        "    targethero BOOLEAN," +
        "    attackerillusion BOOLEAN," +
        "    targetillusion BOOLEAN," +
        "    abilitylevel INTEGER," +
        "    inflictor VARCHAR(100)," +
        "    gold_reason INTEGER," +
        "    xp_reason INTEGER," +
        "    valuename VARCHAR(100)," +
        "    gold INTEGER," +
        "    lh INTEGER," +
        "    xp INTEGER," +
        "    x REAL," +
        "    y REAL," +
        "    z REAL," +
        "    stuns REAL," +
        "    hero_id INTEGER," +
        "    variant INTEGER," +
        "    facet_hero_id INTEGER," +
        "    itemslot INTEGER," +
        "    charges INTEGER," +
        "    secondary_charges INTEGER," +
        "    life_state INTEGER," +
        "    level INTEGER," +
        "    kills INTEGER," +
        "    deaths INTEGER," +
        "    assists INTEGER," +
        "    denies INTEGER," +
        "    entityleft BOOLEAN," +
        "    ehandle INTEGER," +
        "    isNeutralActiveDrop BOOLEAN," +
        "    isNeutralPassiveDrop BOOLEAN," +
        "    obs_placed INTEGER," +
        "    sen_placed INTEGER," +
        "    creeps_stacked INTEGER," +
        "    camps_stacked INTEGER," +
        "    rune_pickups INTEGER," +
        "    repicked BOOLEAN," +
        "    randomed BOOLEAN," +
        "    pred_vict BOOLEAN," +
        "    stun_duration REAL," +
        "    slow_duration REAL," +
        "    tracked_death BOOLEAN," +
        "    greevils_greed_stack INTEGER," +
        "    tracked_sourcename VARCHAR(100)," +
        "    firstblood_claimed INTEGER," +
        "    teamfight_participation REAL," +
        "    towers_killed INTEGER," +
        "    roshans_killed INTEGER," +
        "    observers_placed INTEGER," +
        "    draft_order INTEGER," +
        "    pick BOOLEAN," +
        "    draft_active_team INTEGER," +
        "    draft_extime0 INTEGER," +
        "    draft_extime1 INTEGER," +
        "    networth INTEGER," +
        "    stage INTEGER," +
        "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
        ");";
    
    private static final String CREATE_INDEXES_SQL = 
        "CREATE INDEX IF NOT EXISTS idx_game_events_match_id ON game_events(match_id);" +
        "CREATE INDEX IF NOT EXISTS idx_game_events_type ON game_events(type);" +
        "CREATE INDEX IF NOT EXISTS idx_game_events_slot ON game_events(slot);" +
        "CREATE INDEX IF NOT EXISTS idx_game_events_time ON game_events(time);" +
        "CREATE INDEX IF NOT EXISTS idx_game_events_hero_id ON game_events(hero_id);" +
        "CREATE INDEX IF NOT EXISTS idx_game_events_created_at ON game_events(created_at);";
    
    public static void initializeDatabase() throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                // Create the main events table
                statement.execute(CREATE_TABLES_SQL);
                
                // Create indexes for better query performance
                statement.execute(CREATE_INDEXES_SQL);
                
                System.out.println("Database tables and indexes created successfully.");
            }
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
