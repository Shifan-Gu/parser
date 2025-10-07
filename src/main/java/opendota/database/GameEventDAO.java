package opendota.database;

import opendota.Parse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

public class GameEventDAO {
    
    private static final String INSERT_EVENT_SQL = 
        "INSERT INTO game_events (" +
        "match_id, time, type, team, unit, key, value, slot, player_slot, " +
        "player1, player2, attackername, targetname, sourcename, targetsourcename, " +
        "attackerhero, targethero, attackerillusion, targetillusion, abilitylevel, " +
        "inflictor, gold_reason, xp_reason, valuename, gold, lh, xp, x, y, z, " +
        "stuns, hero_id, variant, facet_hero_id, itemslot, charges, secondary_charges, " +
        "life_state, level, kills, deaths, assists, denies, entityleft, ehandle, " +
        "isNeutralActiveDrop, isNeutralPassiveDrop, obs_placed, sen_placed, " +
        "creeps_stacked, camps_stacked, rune_pickups, repicked, randomed, pred_vict, " +
        "stun_duration, slow_duration, tracked_death, greevils_greed_stack, " +
        "tracked_sourcename, firstblood_claimed, teamfight_participation, " +
        "towers_killed, roshans_killed, observers_placed, draft_order, pick, " +
        "draft_active_team, draft_extime0, draft_extime1, networth, stage, " +
        "created_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    
    private Connection connection;
    private PreparedStatement insertStatement;
    private Long matchId;
    
    public GameEventDAO(Long matchId) throws SQLException {
        this.matchId = matchId;
        this.connection = DatabaseConfig.getConnection();
        this.insertStatement = connection.prepareStatement(INSERT_EVENT_SQL);
    }
    
    public void insertEvent(Parse.Entry entry) throws SQLException {
        insertStatement.setLong(1, matchId);
        insertStatement.setInt(2, entry.time != null ? entry.time : 0);
        insertStatement.setString(3, entry.type);
        insertStatement.setObject(4, entry.team);
        insertStatement.setString(5, entry.unit);
        insertStatement.setString(6, entry.key);
        insertStatement.setObject(7, entry.value);
        insertStatement.setObject(8, entry.slot);
        insertStatement.setObject(9, entry.player_slot);
        insertStatement.setObject(10, entry.player1);
        insertStatement.setObject(11, entry.player2);
        insertStatement.setString(12, entry.attackername);
        insertStatement.setString(13, entry.targetname);
        insertStatement.setString(14, entry.sourcename);
        insertStatement.setString(15, entry.targetsourcename);
        insertStatement.setObject(16, entry.attackerhero);
        insertStatement.setObject(17, entry.targethero);
        insertStatement.setObject(18, entry.attackerillusion);
        insertStatement.setObject(19, entry.targetillusion);
        insertStatement.setObject(20, entry.abilitylevel);
        insertStatement.setString(21, entry.inflictor);
        insertStatement.setObject(22, entry.gold_reason);
        insertStatement.setObject(23, entry.xp_reason);
        insertStatement.setString(24, entry.valuename);
        insertStatement.setObject(25, entry.gold);
        insertStatement.setObject(26, entry.lh);
        insertStatement.setObject(27, entry.xp);
        insertStatement.setObject(28, entry.x);
        insertStatement.setObject(29, entry.y);
        insertStatement.setObject(30, entry.z);
        insertStatement.setObject(31, entry.stuns);
        insertStatement.setObject(32, entry.hero_id);
        insertStatement.setObject(33, entry.variant);
        insertStatement.setObject(34, entry.facet_hero_id);
        insertStatement.setObject(35, entry.itemslot);
        insertStatement.setObject(36, entry.charges);
        insertStatement.setObject(37, entry.secondary_charges);
        insertStatement.setObject(38, entry.life_state);
        insertStatement.setObject(39, entry.level);
        insertStatement.setObject(40, entry.kills);
        insertStatement.setObject(41, entry.deaths);
        insertStatement.setObject(42, entry.assists);
        insertStatement.setObject(43, entry.denies);
        insertStatement.setObject(44, entry.entityleft);
        insertStatement.setObject(45, entry.ehandle);
        insertStatement.setObject(46, entry.isNeutralActiveDrop);
        insertStatement.setObject(47, entry.isNeutralPassiveDrop);
        insertStatement.setObject(48, entry.obs_placed);
        insertStatement.setObject(49, entry.sen_placed);
        insertStatement.setObject(50, entry.creeps_stacked);
        insertStatement.setObject(51, entry.camps_stacked);
        insertStatement.setObject(52, entry.rune_pickups);
        insertStatement.setObject(53, entry.repicked);
        insertStatement.setObject(54, entry.randomed);
        insertStatement.setObject(55, entry.pred_vict);
        insertStatement.setObject(56, entry.stun_duration);
        insertStatement.setObject(57, entry.slow_duration);
        insertStatement.setObject(58, entry.tracked_death);
        insertStatement.setObject(59, entry.greevils_greed_stack);
        insertStatement.setString(60, entry.tracked_sourcename);
        insertStatement.setObject(61, entry.firstblood_claimed);
        insertStatement.setObject(62, entry.teamfight_participation);
        insertStatement.setObject(63, entry.towers_killed);
        insertStatement.setObject(64, entry.roshans_killed);
        insertStatement.setObject(65, entry.observers_placed);
        insertStatement.setObject(66, entry.draft_order);
        insertStatement.setObject(67, entry.pick);
        insertStatement.setObject(68, entry.draft_active_team);
        insertStatement.setObject(69, entry.draft_extime0);
        insertStatement.setObject(70, entry.draft_extime1);
        insertStatement.setObject(71, entry.networth);
        insertStatement.setObject(72, entry.stage);
        insertStatement.setTimestamp(73, new Timestamp(new Date().getTime()));
        
        insertStatement.addBatch();
    }
    
    public void executeBatch() throws SQLException {
        insertStatement.executeBatch();
    }
    
    public void close() throws SQLException {
        if (insertStatement != null) {
            insertStatement.close();
        }
        if (connection != null) {
            connection.close();
        }
    }
}
