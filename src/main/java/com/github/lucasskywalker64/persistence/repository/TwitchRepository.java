package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService.TokenBundle;
import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.data.ShoutoutData;
import com.github.lucasskywalker64.persistence.data.TokenData;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import org.apache.commons.lang3.tuple.Pair;
import org.tinylog.Logger;

import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TwitchRepository {

    private final Connection conn = Database.getInstance().getConnection();
    private final List<TwitchData> localTwitchData;
    private final List<ShoutoutData> localShoutoutData;

    public List<TwitchData> loadAll() {
        return new ArrayList<>(localTwitchData);
    }

    public void saveAll(List<TwitchData> twitchData) throws IOException {
         saveAll(twitchData, true);
    }

    public void saveAll(List<TwitchData> twitchData, boolean append) throws IOException {
        if (!localTwitchData.equals(twitchData)) {
            try {
                if (!append) conn.createStatement().executeUpdate("DELETE FROM twitch");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO twitch (channel, message, username, roleId, announcementId, " +
                                "timestamp, gameName, boxArtUrl, streamId) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    for (TwitchData d : twitchData) {
                        ps.setString(1, d.channel());
                        ps.setString(2, d.message());
                        ps.setString(3, d.username().toLowerCase());
                        ps.setString(4, d.roleId());
                        ps.setString(5, d.announcementId());
                        ps.setLong(6, d.timestamp());
                        ps.setString(7, d.gameName());
                        ps.setString(8, d.boxArtUrl());
                        ps.setString(9, d.streamId());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }
            if (!append)
                localTwitchData.clear();
            localTwitchData.addAll(twitchData);
            Logger.info("Twitch data saved.");
        }
    }

    public List<ShoutoutData> loadAllShoutout() {
        return new ArrayList<>(localShoutoutData);
    }

    public void saveAllShoutout(List<ShoutoutData> shoutoutData) throws IOException {
        saveAllShoutout(shoutoutData, true);
    }

    public void saveAllShoutout(List<ShoutoutData> shoutoutData, boolean append) throws IOException {
        if (!localShoutoutData.equals(shoutoutData)) {
            try {
                if (!append) conn.createStatement().executeUpdate("DELETE FROM shoutout");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO shoutout (username) VALUES (?)")) {
                    for (ShoutoutData d : shoutoutData) {
                        ps.setString(1, d.username().toLowerCase());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }
            if (!append)
                localShoutoutData.clear();
            localShoutoutData.addAll(shoutoutData);
            Logger.info("Shoutout data saved.");
        }
    }

    public List<String> loadAllShoutedOutNames() {
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM shoutedout");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) names.add(rs.getString(1));
        } catch (SQLException e) {
            Logger.error(e);
        }
        return names;
    }

    public void clearShoutedOutNames() {
        try {
            conn.createStatement().executeUpdate("DELETE FROM shoutedout");
        } catch (SQLException e) {
            Logger.error(e);
        }
    }

    public void saveShoutedOutNames(List<String> names) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO shoutedout(name) VALUES(?)")) {
            for (String n : names) { ps.setString(1, n.toLowerCase()); ps.addBatch(); }
            ps.executeBatch();
        } catch (SQLException e) {
            Logger.error(e);
        }
    }

    public void saveRedemption(String redemptionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO twitch_redemption (redemptionId) VALUES (?)")) {
            ps.setString(1, redemptionId);
            ps.executeUpdate();
        }
    }

    public List<String> loadRedemptions() throws SQLException {
        List<String> redemptions = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT redemptionId FROM twitch_redemption")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    redemptions.add(rs.getString(1));
            }
        }
        return redemptions;
    }

    public void deleteRedemption(String redemptionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM twitch_redemption WHERE redemptionId = ?")) {
            ps.setString(1, redemptionId);
            ps.executeUpdate();
        }
    }

    public void incrementRedemptionCount(String redemptionId, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO twitch_redemption_leaderboard (redemptionId, userId, count) " +
                        "VALUES (?,?,1) ON CONFLICT (redemptionId, userId) " +
                        "DO UPDATE SET count = twitch_redemption_leaderboard.count + 1")) {
            ps.setString(1, redemptionId);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    public List<Pair<String, Integer>> loadRedemptionLeaderboard(String redemptionId) throws SQLException {
        List<Pair<String, Integer>> leaderboard = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT userId, count FROM twitch_redemption_leaderboard WHERE redemptionId = ? ORDER BY count DESC")) {
            ps.setString(1, redemptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    leaderboard.add(Pair.of(rs.getString(1), rs.getInt(2)));
            }
        }
        return leaderboard;
    }

    public void saveToken(TokenData data) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO token_data (user_id, login, access_token, refresh_token, expires_at, streamer) " +
                        "VALUES (?, ?, ?, ?, ?,?) " +
                        "ON CONFLICT (user_id) DO UPDATE SET access_token = excluded.access_token, " +
                        "refresh_token = excluded.refresh_token, expires_at = excluded.expires_at")) {
            ps.setString(1, data.userId());
            ps.setString(2, data.login());
            ps.setString(3, data.bundle().accessToken());
            ps.setString(4, data.bundle().refreshToken());
            ps.setLong(5, data.bundle().expiresAt().toEpochMilli());
            ps.setBoolean(6, data.streamer());
            ps.executeUpdate();
        }
    }

    public TokenData loadToken() throws SQLException {
        return loadToken(false);
    }

    public TokenData loadToken(boolean streamer) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM token_data WHERE streamer = ?")) {
            ps.setBoolean(1, streamer);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    TokenBundle bundle = new TokenBundle(
                            rs.getString("access_token"),
                            rs.getString("refresh_token"),
                            Instant.ofEpochMilli(rs.getLong("expires_at"))
                    );
                    return new TokenData(
                            bundle,
                            rs.getString("user_id"),
                            rs.getString("login"),
                            rs.getBoolean("streamer")
                    );
                }
            }
        }
        return null;
    }

    public void deleteToken() throws SQLException {
        conn.createStatement().executeUpdate("DELETE FROM token_data");
    }

    private TwitchRepository() throws SQLException {
        localTwitchData = new ArrayList<>();
        localShoutoutData = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT channel, message, username, roleId, " +
                "announcementId, timestamp, gameName, boxArtUrl, streamId FROM twitch");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                localTwitchData.add(new TwitchData(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getLong(6),
                        rs.getString(7),
                        rs.getString(8),
                        rs.getString(9)
                ));
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT username FROM shoutout");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) localShoutoutData.add(new ShoutoutData(rs.getString(1)));
        }
        Logger.info("Twitch and shoutout data loaded.");
    }

    private static class Holder {
        private static final TwitchRepository INSTANCE;

        static {
            try {
                INSTANCE = new TwitchRepository();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static TwitchRepository getInstance() {
        return Holder.INSTANCE;
    }
}
