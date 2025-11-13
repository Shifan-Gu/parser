package tidebound.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class GameInfoDAO {

    private final Connection connection;
    private final PreparedStatement upsertStmt;

    public GameInfoDAO() throws SQLException {
        this.connection = DatabaseConfig.getConnection();
        this.upsertStmt = connection.prepareStatement(
            "INSERT INTO game_info (" +
                "match_id, replay_match_id, playback_time, playback_ticks, playback_frames, " +
                "game_mode, game_winner, league_id, radiant_team_id, dire_team_id, " +
                "radiant_team_tag, dire_team_tag, end_time, players, picks_bans, raw_file_info" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb) " +
            "ON CONFLICT (match_id) DO UPDATE SET " +
                "replay_match_id = EXCLUDED.replay_match_id, " +
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
                "raw_file_info = EXCLUDED.raw_file_info"
        );
    }

    public void upsertGameInfo(
        long matchId,
        Long replayMatchId,
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
        if (replayMatchId != null) {
            upsertStmt.setLong(2, replayMatchId);
        } else {
            upsertStmt.setNull(2, Types.BIGINT);
        }
        if (playbackTime != null) {
            upsertStmt.setFloat(3, playbackTime);
        } else {
            upsertStmt.setNull(3, Types.REAL);
        }
        if (playbackTicks != null) {
            upsertStmt.setInt(4, playbackTicks);
        } else {
            upsertStmt.setNull(4, Types.INTEGER);
        }
        if (playbackFrames != null) {
            upsertStmt.setInt(5, playbackFrames);
        } else {
            upsertStmt.setNull(5, Types.INTEGER);
        }
        if (gameMode != null) {
            upsertStmt.setInt(6, gameMode);
        } else {
            upsertStmt.setNull(6, Types.INTEGER);
        }
        if (gameWinner != null) {
            upsertStmt.setInt(7, gameWinner);
        } else {
            upsertStmt.setNull(7, Types.INTEGER);
        }
        if (leagueId != null) {
            upsertStmt.setInt(8, leagueId);
        } else {
            upsertStmt.setNull(8, Types.INTEGER);
        }
        if (radiantTeamId != null) {
            upsertStmt.setInt(9, radiantTeamId);
        } else {
            upsertStmt.setNull(9, Types.INTEGER);
        }
        if (direTeamId != null) {
            upsertStmt.setInt(10, direTeamId);
        } else {
            upsertStmt.setNull(10, Types.INTEGER);
        }
        if (radiantTeamTag != null) {
            upsertStmt.setString(11, radiantTeamTag);
        } else {
            upsertStmt.setNull(11, Types.VARCHAR);
        }
        if (direTeamTag != null) {
            upsertStmt.setString(12, direTeamTag);
        } else {
            upsertStmt.setNull(12, Types.VARCHAR);
        }
        if (endTime != null) {
            upsertStmt.setInt(13, endTime);
        } else {
            upsertStmt.setNull(13, Types.INTEGER);
        }
        if (playersJson != null) {
            upsertStmt.setObject(14, playersJson, Types.OTHER);
        } else {
            upsertStmt.setNull(14, Types.OTHER);
        }
        if (picksBansJson != null) {
            upsertStmt.setObject(15, picksBansJson, Types.OTHER);
        } else {
            upsertStmt.setNull(15, Types.OTHER);
        }
        upsertStmt.setObject(16, rawFileInfoJson, Types.OTHER);

        upsertStmt.executeUpdate();
    }

    public void close() throws SQLException {
        if (upsertStmt != null) {
            upsertStmt.close();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}

