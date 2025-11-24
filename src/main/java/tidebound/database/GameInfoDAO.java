package tidebound.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class GameInfoDAO {

    private static final String RAW_SCHEMA = "replay_raw";
    private final Connection connection;
    private final PreparedStatement upsertStmt;
    private final PreparedStatement insertPlayerStmt;
    private final PreparedStatement insertPickBanStmt;
    private final PreparedStatement deletePlayersStmt;
    private final PreparedStatement deletePicksBansStmt;

    public GameInfoDAO() throws SQLException {
        this.connection = DatabaseConfig.getConnection();
        String sql =
            "INSERT INTO " + qualifiedTable("game_info") + " (" +
                "match_id, playback_time, playback_ticks, playback_frames, " +
                "game_mode, game_winner, league_id, radiant_team_id, dire_team_id, " +
                "radiant_team_tag, dire_team_tag, end_time, players, picks_bans, raw_file_info" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb) " +
            "ON CONFLICT (match_id) DO UPDATE SET " +
                "playback_time = EXCLUDED.playback_time, " +
                "playback_ticks = EXCLUDED.playback_ticks, " +
                "playback_frames = EXCLUDED.playback_frames, " +
                "game_mode = EXCLUDED.game_mode, " +
                "game_winner = EXCLUDED.game_winner, " +
                "league_id = EXCLUDED.league_id, " +
                "radiant_team_id = EXCLUDED.radiant_team_id, " +
                "dire_team_id = EXCLUDED.dire_team_id, " +
                "radiant_team_tag = EXCLUDED.radiant_team_tag, " +
                "dire_team_tag = EXCLUDED.dire_team_tag, " +
                "end_time = EXCLUDED.end_time, " +
                "players = EXCLUDED.players, " +
                "picks_bans = EXCLUDED.picks_bans, " +
                "raw_file_info = EXCLUDED.raw_file_info";
        this.upsertStmt = connection.prepareStatement(sql);
        
        // Prepared statements for normalized tables
        String insertPlayerSql = 
            "INSERT INTO " + qualifiedTable("game_players") + " (" +
                "match_id, player_slot, steam_id, player_name, hero_name, game_team, is_fake_client" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?)";
        this.insertPlayerStmt = connection.prepareStatement(insertPlayerSql);
        
        String insertPickBanSql = 
            "INSERT INTO " + qualifiedTable("game_picks_bans") + " (" +
                "match_id, is_pick, team, hero_id" +
            ") VALUES (?, ?, ?, ?)";
        this.insertPickBanStmt = connection.prepareStatement(insertPickBanSql);
        
        String deletePlayersSql = "DELETE FROM " + qualifiedTable("game_players") + " WHERE match_id = ?";
        this.deletePlayersStmt = connection.prepareStatement(deletePlayersSql);
        
        String deletePicksBansSql = "DELETE FROM " + qualifiedTable("game_picks_bans") + " WHERE match_id = ?";
        this.deletePicksBansStmt = connection.prepareStatement(deletePicksBansSql);
    }

    public void upsertGameInfo(
        long matchId,
        Float playbackTime,
        Integer playbackTicks,
        Integer playbackFrames,
        Integer gameMode,
        Integer gameWinner,
        Integer leagueId,
        Integer radiantTeamId,
        Integer direTeamId,
        String radiantTeamTag,
        String direTeamTag,
        Integer endTime,
        String playersJson,
        String picksBansJson,
        String rawFileInfoJson
    ) throws SQLException {

        upsertStmt.setLong(1, matchId);
        if (playbackTime != null) {
            upsertStmt.setFloat(2, playbackTime);
        } else {
            upsertStmt.setNull(2, Types.REAL);
        }
        if (playbackTicks != null) {
            upsertStmt.setInt(3, playbackTicks);
        } else {
            upsertStmt.setNull(3, Types.INTEGER);
        }
        if (playbackFrames != null) {
            upsertStmt.setInt(4, playbackFrames);
        } else {
            upsertStmt.setNull(4, Types.INTEGER);
        }
        if (gameMode != null) {
            upsertStmt.setInt(5, gameMode);
        } else {
            upsertStmt.setNull(5, Types.INTEGER);
        }
        if (gameWinner != null) {
            upsertStmt.setInt(6, gameWinner);
        } else {
            upsertStmt.setNull(6, Types.INTEGER);
        }
        if (leagueId != null) {
            upsertStmt.setInt(7, leagueId);
        } else {
            upsertStmt.setNull(7, Types.INTEGER);
        }
        if (radiantTeamId != null) {
            upsertStmt.setInt(8, radiantTeamId);
        } else {
            upsertStmt.setNull(8, Types.INTEGER);
        }
        if (direTeamId != null) {
            upsertStmt.setInt(9, direTeamId);
        } else {
            upsertStmt.setNull(9, Types.INTEGER);
        }
        if (radiantTeamTag != null) {
            upsertStmt.setString(10, radiantTeamTag);
        } else {
            upsertStmt.setNull(10, Types.VARCHAR);
        }
        if (direTeamTag != null) {
            upsertStmt.setString(11, direTeamTag);
        } else {
            upsertStmt.setNull(11, Types.VARCHAR);
        }
        if (endTime != null) {
            upsertStmt.setInt(12, endTime);
        } else {
            upsertStmt.setNull(12, Types.INTEGER);
        }
        if (playersJson != null) {
            upsertStmt.setObject(13, playersJson, Types.OTHER);
        } else {
            upsertStmt.setNull(13, Types.OTHER);
        }
        if (picksBansJson != null) {
            upsertStmt.setObject(14, picksBansJson, Types.OTHER);
        } else {
            upsertStmt.setNull(14, Types.OTHER);
        }
        upsertStmt.setObject(15, rawFileInfoJson, Types.OTHER);

        upsertStmt.executeUpdate();
    }

    public void insertPlayers(long matchId, List<PlayerInfo> players) throws SQLException {
        // Delete existing players for this match
        deletePlayersStmt.setLong(1, matchId);
        deletePlayersStmt.executeUpdate();
        
        // Insert new players
        for (PlayerInfo player : players) {
            insertPlayerStmt.setLong(1, matchId);
            if (player.playerSlot != null) {
                insertPlayerStmt.setInt(2, player.playerSlot);
            } else {
                insertPlayerStmt.setNull(2, Types.INTEGER);
            }
            if (player.steamId != null) {
                insertPlayerStmt.setLong(3, player.steamId);
            } else {
                insertPlayerStmt.setNull(3, Types.BIGINT);
            }
            if (player.playerName != null) {
                insertPlayerStmt.setString(4, player.playerName);
            } else {
                insertPlayerStmt.setNull(4, Types.VARCHAR);
            }
            if (player.heroName != null) {
                insertPlayerStmt.setString(5, player.heroName);
            } else {
                insertPlayerStmt.setNull(5, Types.VARCHAR);
            }
            if (player.gameTeam != null) {
                insertPlayerStmt.setInt(6, player.gameTeam);
            } else {
                insertPlayerStmt.setNull(6, Types.INTEGER);
            }
            if (player.isFakeClient != null) {
                insertPlayerStmt.setBoolean(7, player.isFakeClient);
            } else {
                insertPlayerStmt.setNull(7, Types.BOOLEAN);
            }
            insertPlayerStmt.addBatch();
        }
        insertPlayerStmt.executeBatch();
        insertPlayerStmt.clearBatch();
    }
    
    public void insertPicksBans(long matchId, List<PickBanInfo> picksBans) throws SQLException {
        // Delete existing picks_bans for this match
        deletePicksBansStmt.setLong(1, matchId);
        deletePicksBansStmt.executeUpdate();
        
        // Insert new picks_bans
        for (PickBanInfo pickBan : picksBans) {
            insertPickBanStmt.setLong(1, matchId);
            if (pickBan.isPick != null) {
                insertPickBanStmt.setBoolean(2, pickBan.isPick);
            } else {
                insertPickBanStmt.setNull(2, Types.BOOLEAN);
            }
            if (pickBan.team != null) {
                insertPickBanStmt.setInt(3, pickBan.team);
            } else {
                insertPickBanStmt.setNull(3, Types.INTEGER);
            }
            if (pickBan.heroId != null) {
                insertPickBanStmt.setInt(4, pickBan.heroId);
            } else {
                insertPickBanStmt.setNull(4, Types.INTEGER);
            }
            insertPickBanStmt.addBatch();
        }
        insertPickBanStmt.executeBatch();
        insertPickBanStmt.clearBatch();
    }

    public void close() throws SQLException {
        if (insertPickBanStmt != null) {
            insertPickBanStmt.close();
        }
        if (insertPlayerStmt != null) {
            insertPlayerStmt.close();
        }
        if (deletePicksBansStmt != null) {
            deletePicksBansStmt.close();
        }
        if (deletePlayersStmt != null) {
            deletePlayersStmt.close();
        }
        if (upsertStmt != null) {
            upsertStmt.close();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private String qualifiedTable(String tableName) {
        return RAW_SCHEMA + "." + tableName;
    }
    
    // Helper classes for normalized data
    public static class PlayerInfo {
        public Integer playerSlot;
        public Long steamId;
        public String playerName;
        public String heroName;
        public Integer gameTeam;
        public Boolean isFakeClient;
    }
    
    public static class PickBanInfo {
        public Boolean isPick;
        public Integer team;
        public Integer heroId;
    }
}

