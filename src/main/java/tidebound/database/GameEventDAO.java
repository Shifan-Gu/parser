package tidebound.database;

import tidebound.Parse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

public class GameEventDAO {
    
    private static final String RAW_SCHEMA = "replay_raw";
    
    private Connection connection;
    private Long matchId;
    
    // Prepared statements for each event type
    private PreparedStatement combatLogStmt;
    private PreparedStatement actionStmt;
    private PreparedStatement pingStmt;
    private PreparedStatement chatTypeStmt;
    private PreparedStatement chatStmt;
    private PreparedStatement chatwheelStmt;
    private PreparedStatement cosmeticsStmt;
    private PreparedStatement dotaplusStmt;
    private PreparedStatement epilogueStmt;
    private PreparedStatement neutralTokenStmt;
    private PreparedStatement neutralItemHistoryStmt;
    private PreparedStatement playerSlotStmt;
    private PreparedStatement draftStartStmt;
    private PreparedStatement draftTimingStmt;
    private PreparedStatement intervalStmt;
    private PreparedStatement abilityLevelStmt;
    private PreparedStatement startingItemStmt;
    private PreparedStatement gamePausedStmt;
    private PreparedStatement wardStmt;
    
    public GameEventDAO(Long matchId) throws SQLException {
        this.matchId = matchId;
        this.connection = DatabaseConfig.getConnection();
        initializePreparedStatements();
    }
    
    private void initializePreparedStatements() throws SQLException {
        // Combat log events
        String combatLogSql =
            "INSERT INTO " + qualifiedTable("combat_log_events") + " (match_id, time, type, attackername, targetname, sourcename, " +
            "targetsourcename, attackerhero, targethero, attackerillusion, targetillusion, inflictor, " +
            "value, valuename, gold_reason, xp_reason, stun_duration, slow_duration, greevils_greed_stack, " +
            "tracked_death, tracked_sourcename, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        combatLogStmt = connection.prepareStatement(combatLogSql);
        
        // Action events
        String actionSql =
            "INSERT INTO " + qualifiedTable("action_events") + " (match_id, time, slot, key, created_at) " +
            "VALUES (?, ?, ?, ?, ?)";
        actionStmt = connection.prepareStatement(actionSql);
        
        // Ping events
        String pingSql =
            "INSERT INTO " + qualifiedTable("ping_events") + " (match_id, time, slot, created_at) " +
            "VALUES (?, ?, ?, ?)";
        pingStmt = connection.prepareStatement(pingSql);
        
        // Chat type events (numbered types)
        String chatTypeSql =
            "INSERT INTO " + qualifiedTable("chat_type_events") + " (match_id, time, type, player1, player2, value, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        chatTypeStmt = connection.prepareStatement(chatTypeSql);
        
        // Chat events
        String chatSql =
            "INSERT INTO " + qualifiedTable("chat_events") + " (match_id, time, slot, unit, key, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        chatStmt = connection.prepareStatement(chatSql);
        
        // Chatwheel events
        String chatwheelSql =
            "INSERT INTO " + qualifiedTable("chatwheel_events") + " (match_id, time, slot, key, created_at) " +
            "VALUES (?, ?, ?, ?, ?)";
        chatwheelStmt = connection.prepareStatement(chatwheelSql);
        
        // Cosmetics events
        String cosmeticsSql =
            "INSERT INTO " + qualifiedTable("cosmetics_events") + " (match_id, time, key, created_at) " +
            "VALUES (?, ?, ?, ?)";
        cosmeticsStmt = connection.prepareStatement(cosmeticsSql);
        
        // Dotaplus events
        String dotaplusSql =
            "INSERT INTO " + qualifiedTable("dotaplus_events") + " (match_id, time, key, created_at) " +
            "VALUES (?, ?, ?, ?)";
        dotaplusStmt = connection.prepareStatement(dotaplusSql);
        
        // Epilogue events
        String epilogueSql =
            "INSERT INTO " + qualifiedTable("epilogue_events") + " (match_id, time, key, created_at) " +
            "VALUES (?, ?, ?, ?)";
        epilogueStmt = connection.prepareStatement(epilogueSql);
        
        // Neutral token events
        String neutralTokenSql =
            "INSERT INTO " + qualifiedTable("neutral_token_events") + " (match_id, time, slot, key, created_at) " +
            "VALUES (?, ?, ?, ?, ?)";
        neutralTokenStmt = connection.prepareStatement(neutralTokenSql);
        
        // Neutral item history events
        String neutralItemHistorySql =
            "INSERT INTO " + qualifiedTable("neutral_item_history_events") + " (match_id, time, slot, key, isNeutralActiveDrop, " +
            "isNeutralPassiveDrop, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        neutralItemHistoryStmt = connection.prepareStatement(neutralItemHistorySql);
        
        // Player slot events
        String playerSlotSql =
            "INSERT INTO " + qualifiedTable("player_slot_events") + " (match_id, time, key, value, created_at) " +
            "VALUES (?, ?, ?, ?, ?)";
        playerSlotStmt = connection.prepareStatement(playerSlotSql);
        
        // Draft start events
        String draftStartSql =
            "INSERT INTO " + qualifiedTable("draft_start_events") + " (match_id, time, created_at) " +
            "VALUES (?, ?, ?)";
        draftStartStmt = connection.prepareStatement(draftStartSql);
        
        // Draft timing events
        String draftTimingSql =
            "INSERT INTO " + qualifiedTable("draft_timing_events") + " (match_id, time, draft_order, pick, hero_id, " +
            "draft_active_team, draft_extime0, draft_extime1, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        draftTimingStmt = connection.prepareStatement(draftTimingSql);
        
        // Interval events
        String intervalSql =
            "INSERT INTO " + qualifiedTable("interval_events") + " (match_id, time, slot, unit, hero_id, variant, facet_hero_id, " +
            "level, x, y, life_state, gold, lh, xp, stuns, kills, deaths, assists, denies, " +
            "obs_placed, sen_placed, creeps_stacked, camps_stacked, rune_pickups, towers_killed, " +
            "roshans_killed, observers_placed, networth, repicked, randomed, pred_vict, " +
            "firstblood_claimed, teamfight_participation, stage, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        intervalStmt = connection.prepareStatement(intervalSql);
        
        // Ability level events
        String abilitySql =
            "INSERT INTO " + qualifiedTable("ability_level_events") + " (match_id, time, targetname, valuename, abilitylevel, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        abilityLevelStmt = connection.prepareStatement(abilitySql);
        
        // Starting item events
        String startingItemSql =
            "INSERT INTO " + qualifiedTable("starting_item_events") + " (match_id, time, slot, targetname, valuename, value, " +
            "itemslot, charges, secondary_charges, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        startingItemStmt = connection.prepareStatement(startingItemSql);
        
        // Game paused events
        String gamePausedSql =
            "INSERT INTO " + qualifiedTable("game_paused_events") + " (match_id, time, key, value, created_at) " +
            "VALUES (?, ?, ?, ?, ?)";
        gamePausedStmt = connection.prepareStatement(gamePausedSql);
        
        // Ward events
        String wardSql =
            "INSERT INTO " + qualifiedTable("ward_events") + " (match_id, time, type, slot, x, y, z, entityleft, ehandle, " +
            "attackername, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        wardStmt = connection.prepareStatement(wardSql);
    }
    
    public void insertEvent(Parse.Entry entry) throws SQLException {
        if (entry.type == null) {
            return;
        }
        
        Timestamp timestamp = new Timestamp(new Date().getTime());
        int time = entry.time != null ? entry.time : 0;
        
        // Route to appropriate table based on event type
        if (entry.type.startsWith("DOTA_COMBATLOG_")) {
            insertCombatLogEvent(entry, timestamp, time);
        } else {
            switch (entry.type) {
                case "actions":
                    insertActionEvent(entry, timestamp, time);
                    break;
                case "pings":
                    insertPingEvent(entry, timestamp, time);
                    break;
                case "chat":
                    insertChatEvent(entry, timestamp, time);
                    break;
                case "chatwheel":
                    insertChatwheelEvent(entry, timestamp, time);
                    break;
                case "cosmetics":
                    insertCosmeticsEvent(entry, timestamp, time);
                    break;
                case "dotaplus":
                    insertDotaplusEvent(entry, timestamp, time);
                    break;
                case "epilogue":
                    insertEpilogueEvent(entry, timestamp, time);
                    break;
                case "neutral_token":
                    insertNeutralTokenEvent(entry, timestamp, time);
                    break;
                case "neutral_item_history":
                    insertNeutralItemHistoryEvent(entry, timestamp, time);
                    break;
                case "player_slot":
                    insertPlayerSlotEvent(entry, timestamp, time);
                    break;
                case "draft_start":
                    insertDraftStartEvent(entry, timestamp, time);
                    break;
                case "draft_timings":
                    insertDraftTimingEvent(entry, timestamp, time);
                    break;
                case "interval":
                    insertIntervalEvent(entry, timestamp, time);
                    break;
                case "DOTA_ABILITY_LEVEL":
                    insertAbilityLevelEvent(entry, timestamp, time);
                    break;
                case "STARTING_ITEM":
                    insertStartingItemEvent(entry, timestamp, time);
                    break;
                case "game_paused":
                    insertGamePausedEvent(entry, timestamp, time);
                    break;
                case "obs":
                case "sen":
                case "obs_left":
                case "sen_left":
                    insertWardEvent(entry, timestamp, time);
                    break;
                default:
                    // Handle chat event types that are numeric strings
                    if (isNumericChatType(entry.type)) {
                        insertChatTypeEvent(entry, timestamp, time);
                    }
                    // Unknown event type - log and skip
                    System.err.println("Unknown event type: " + entry.type);
                    break;
            }
        }
    }
    
    private boolean isNumericChatType(String type) {
        try {
            Integer.parseInt(type);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private String qualifiedTable(String tableName) {
        return RAW_SCHEMA + "." + tableName;
    }
    
    private void insertCombatLogEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        combatLogStmt.setLong(1, matchId);
        combatLogStmt.setInt(2, time);
        combatLogStmt.setString(3, entry.type);
        combatLogStmt.setString(4, entry.attackername);
        combatLogStmt.setString(5, entry.targetname);
        combatLogStmt.setString(6, entry.sourcename);
        combatLogStmt.setString(7, entry.targetsourcename);
        combatLogStmt.setObject(8, entry.attackerhero);
        combatLogStmt.setObject(9, entry.targethero);
        combatLogStmt.setObject(10, entry.attackerillusion);
        combatLogStmt.setObject(11, entry.targetillusion);
        combatLogStmt.setString(12, entry.inflictor);
        combatLogStmt.setObject(13, entry.value);
        combatLogStmt.setString(14, entry.valuename);
        combatLogStmt.setObject(15, entry.gold_reason);
        combatLogStmt.setObject(16, entry.xp_reason);
        combatLogStmt.setObject(17, entry.stun_duration);
        combatLogStmt.setObject(18, entry.slow_duration);
        combatLogStmt.setObject(19, entry.greevils_greed_stack);
        combatLogStmt.setObject(20, entry.tracked_death);
        combatLogStmt.setString(21, entry.tracked_sourcename);
        combatLogStmt.setTimestamp(22, timestamp);
        combatLogStmt.addBatch();
    }
    
    private void insertActionEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        actionStmt.setLong(1, matchId);
        actionStmt.setInt(2, time);
        actionStmt.setObject(3, entry.slot);
        actionStmt.setString(4, entry.key);
        actionStmt.setTimestamp(5, timestamp);
        actionStmt.addBatch();
    }
    
    private void insertPingEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        pingStmt.setLong(1, matchId);
        pingStmt.setInt(2, time);
        pingStmt.setObject(3, entry.slot);
        pingStmt.setTimestamp(4, timestamp);
        pingStmt.addBatch();
    }
    
    private void insertChatTypeEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        chatTypeStmt.setLong(1, matchId);
        chatTypeStmt.setInt(2, time);
        chatTypeStmt.setString(3, entry.type);
        chatTypeStmt.setObject(4, entry.player1);
        chatTypeStmt.setObject(5, entry.player2);
        chatTypeStmt.setObject(6, entry.value);
        chatTypeStmt.setTimestamp(7, timestamp);
        chatTypeStmt.addBatch();
    }
    
    private void insertChatEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        chatStmt.setLong(1, matchId);
        chatStmt.setInt(2, time);
        chatStmt.setObject(3, entry.slot);
        chatStmt.setString(4, entry.unit);
        chatStmt.setString(5, entry.key);
        chatStmt.setTimestamp(6, timestamp);
        chatStmt.addBatch();
    }
    
    private void insertChatwheelEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        chatwheelStmt.setLong(1, matchId);
        chatwheelStmt.setInt(2, time);
        chatwheelStmt.setObject(3, entry.slot);
        chatwheelStmt.setString(4, entry.key);
        chatwheelStmt.setTimestamp(5, timestamp);
        chatwheelStmt.addBatch();
    }
    
    private void insertCosmeticsEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        cosmeticsStmt.setLong(1, matchId);
        cosmeticsStmt.setInt(2, time);
        cosmeticsStmt.setString(3, entry.key);
        cosmeticsStmt.setTimestamp(4, timestamp);
        cosmeticsStmt.addBatch();
    }
    
    private void insertDotaplusEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        dotaplusStmt.setLong(1, matchId);
        dotaplusStmt.setInt(2, time);
        dotaplusStmt.setString(3, entry.key);
        dotaplusStmt.setTimestamp(4, timestamp);
        dotaplusStmt.addBatch();
    }
    
    private void insertEpilogueEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        epilogueStmt.setLong(1, matchId);
        epilogueStmt.setInt(2, time);
        epilogueStmt.setString(3, entry.key);
        epilogueStmt.setTimestamp(4, timestamp);
        epilogueStmt.addBatch();
    }
    
    private void insertNeutralTokenEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        neutralTokenStmt.setLong(1, matchId);
        neutralTokenStmt.setInt(2, time);
        neutralTokenStmt.setObject(3, entry.slot);
        neutralTokenStmt.setString(4, entry.key);
        neutralTokenStmt.setTimestamp(5, timestamp);
        neutralTokenStmt.addBatch();
    }
    
    private void insertNeutralItemHistoryEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        neutralItemHistoryStmt.setLong(1, matchId);
        neutralItemHistoryStmt.setInt(2, time);
        neutralItemHistoryStmt.setObject(3, entry.slot);
        neutralItemHistoryStmt.setString(4, entry.key);
        neutralItemHistoryStmt.setObject(5, entry.isNeutralActiveDrop);
        neutralItemHistoryStmt.setObject(6, entry.isNeutralPassiveDrop);
        neutralItemHistoryStmt.setTimestamp(7, timestamp);
        neutralItemHistoryStmt.addBatch();
    }
    
    private void insertPlayerSlotEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        playerSlotStmt.setLong(1, matchId);
        playerSlotStmt.setInt(2, time);
        playerSlotStmt.setString(3, entry.key);
        playerSlotStmt.setObject(4, entry.value);
        playerSlotStmt.setTimestamp(5, timestamp);
        playerSlotStmt.addBatch();
    }
    
    private void insertDraftStartEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        draftStartStmt.setLong(1, matchId);
        draftStartStmt.setInt(2, time);
        draftStartStmt.setTimestamp(3, timestamp);
        draftStartStmt.addBatch();
    }
    
    private void insertDraftTimingEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        draftTimingStmt.setLong(1, matchId);
        draftTimingStmt.setInt(2, time);
        draftTimingStmt.setObject(3, entry.draft_order);
        draftTimingStmt.setObject(4, entry.pick);
        draftTimingStmt.setObject(5, entry.hero_id);
        draftTimingStmt.setObject(6, entry.draft_active_team);
        draftTimingStmt.setObject(7, entry.draft_extime0);
        draftTimingStmt.setObject(8, entry.draft_extime1);
        draftTimingStmt.setTimestamp(9, timestamp);
        draftTimingStmt.addBatch();
    }
    
    private void insertIntervalEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        intervalStmt.setLong(1, matchId);
        intervalStmt.setInt(2, time);
        intervalStmt.setObject(3, entry.slot);
        intervalStmt.setString(4, entry.unit);
        intervalStmt.setObject(5, entry.hero_id);
        intervalStmt.setObject(6, entry.variant);
        intervalStmt.setObject(7, entry.facet_hero_id);
        intervalStmt.setObject(8, entry.level);
        intervalStmt.setObject(9, entry.x);
        intervalStmt.setObject(10, entry.y);
        intervalStmt.setObject(11, entry.life_state);
        intervalStmt.setObject(12, entry.gold);
        intervalStmt.setObject(13, entry.lh);
        intervalStmt.setObject(14, entry.xp);
        intervalStmt.setObject(15, entry.stuns);
        intervalStmt.setObject(16, entry.kills);
        intervalStmt.setObject(17, entry.deaths);
        intervalStmt.setObject(18, entry.assists);
        intervalStmt.setObject(19, entry.denies);
        intervalStmt.setObject(20, entry.obs_placed);
        intervalStmt.setObject(21, entry.sen_placed);
        intervalStmt.setObject(22, entry.creeps_stacked);
        intervalStmt.setObject(23, entry.camps_stacked);
        intervalStmt.setObject(24, entry.rune_pickups);
        intervalStmt.setObject(25, entry.towers_killed);
        intervalStmt.setObject(26, entry.roshans_killed);
        intervalStmt.setObject(27, entry.observers_placed);
        intervalStmt.setObject(28, entry.networth);
        intervalStmt.setObject(29, entry.repicked);
        intervalStmt.setObject(30, entry.randomed);
        intervalStmt.setObject(31, entry.pred_vict);
        intervalStmt.setObject(32, entry.firstblood_claimed);
        intervalStmt.setObject(33, entry.teamfight_participation);
        intervalStmt.setObject(34, entry.stage);
        intervalStmt.setTimestamp(35, timestamp);
        intervalStmt.addBatch();
    }
    
    private void insertAbilityLevelEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        abilityLevelStmt.setLong(1, matchId);
        abilityLevelStmt.setInt(2, time);
        abilityLevelStmt.setString(3, entry.targetname);
        abilityLevelStmt.setString(4, entry.valuename);
        abilityLevelStmt.setObject(5, entry.abilitylevel);
        abilityLevelStmt.setTimestamp(6, timestamp);
        abilityLevelStmt.addBatch();
    }
    
    private void insertStartingItemEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        startingItemStmt.setLong(1, matchId);
        startingItemStmt.setInt(2, time);
        startingItemStmt.setObject(3, entry.slot);
        startingItemStmt.setString(4, entry.targetname);
        startingItemStmt.setString(5, entry.valuename);
        startingItemStmt.setObject(6, entry.value);
        startingItemStmt.setObject(7, entry.itemslot);
        startingItemStmt.setObject(8, entry.charges);
        startingItemStmt.setObject(9, entry.secondary_charges);
        startingItemStmt.setTimestamp(10, timestamp);
        startingItemStmt.addBatch();
    }
    
    private void insertGamePausedEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        gamePausedStmt.setLong(1, matchId);
        gamePausedStmt.setInt(2, time);
        gamePausedStmt.setString(3, entry.key);
        gamePausedStmt.setObject(4, entry.value);
        gamePausedStmt.setTimestamp(5, timestamp);
        gamePausedStmt.addBatch();
    }
    
    private void insertWardEvent(Parse.Entry entry, Timestamp timestamp, int time) throws SQLException {
        wardStmt.setLong(1, matchId);
        wardStmt.setInt(2, time);
        wardStmt.setString(3, entry.type);
        wardStmt.setObject(4, entry.slot);
        wardStmt.setObject(5, entry.x);
        wardStmt.setObject(6, entry.y);
        wardStmt.setObject(7, entry.z);
        wardStmt.setObject(8, entry.entityleft);
        wardStmt.setObject(9, entry.ehandle);
        wardStmt.setString(10, entry.attackername);
        wardStmt.setTimestamp(11, timestamp);
        wardStmt.addBatch();
    }
    
    public void executeBatch() throws SQLException {
        // Execute all batches
        combatLogStmt.executeBatch();
        actionStmt.executeBatch();
        pingStmt.executeBatch();
        chatTypeStmt.executeBatch();
        chatStmt.executeBatch();
        chatwheelStmt.executeBatch();
        cosmeticsStmt.executeBatch();
        dotaplusStmt.executeBatch();
        epilogueStmt.executeBatch();
        neutralTokenStmt.executeBatch();
        neutralItemHistoryStmt.executeBatch();
        playerSlotStmt.executeBatch();
        draftStartStmt.executeBatch();
        draftTimingStmt.executeBatch();
        intervalStmt.executeBatch();
        abilityLevelStmt.executeBatch();
        startingItemStmt.executeBatch();
        gamePausedStmt.executeBatch();
        wardStmt.executeBatch();
    }
    
    public void close() throws SQLException {
        // Close all prepared statements
        if (combatLogStmt != null) combatLogStmt.close();
        if (actionStmt != null) actionStmt.close();
        if (pingStmt != null) pingStmt.close();
        if (chatTypeStmt != null) chatTypeStmt.close();
        if (chatStmt != null) chatStmt.close();
        if (chatwheelStmt != null) chatwheelStmt.close();
        if (cosmeticsStmt != null) cosmeticsStmt.close();
        if (dotaplusStmt != null) dotaplusStmt.close();
        if (epilogueStmt != null) epilogueStmt.close();
        if (neutralTokenStmt != null) neutralTokenStmt.close();
        if (neutralItemHistoryStmt != null) neutralItemHistoryStmt.close();
        if (playerSlotStmt != null) playerSlotStmt.close();
        if (draftStartStmt != null) draftStartStmt.close();
        if (draftTimingStmt != null) draftTimingStmt.close();
        if (intervalStmt != null) intervalStmt.close();
        if (abilityLevelStmt != null) abilityLevelStmt.close();
        if (startingItemStmt != null) startingItemStmt.close();
        if (gamePausedStmt != null) gamePausedStmt.close();
        if (wardStmt != null) wardStmt.close();
        
        if (connection != null) {
            connection.close();
        }
    }
}
