package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.data.ShoutoutData;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import org.tinylog.Logger;

import java.io.IOException;
import java.sql.*;
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
                                "timestamp, gameName, boxArtUrl) VALUES (?,?,?,?,?,?,?,?)")) {
                    for (TwitchData d : twitchData) {
                        ps.setString(1, d.channel());
                        ps.setString(2, d.message());
                        ps.setString(3, d.username().toLowerCase());
                        ps.setString(4, d.roleId());
                        ps.setString(5, d.announcementId());
                        ps.setLong(6, d.timestamp());
                        ps.setString(7, d.gameName());
                        ps.setString(8, d.boxArtUrl());
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

    public String readModeratorName() {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM settings WHERE key = 'moderator_name'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) {
            Logger.error(e);
        }
        return "";
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

    private TwitchRepository() throws IOException {
        localTwitchData = new ArrayList<>();
        localShoutoutData = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT channel, message, username, roleId, " +
                "announcementId, timestamp, gameName, boxArtUrl FROM twitch");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                localTwitchData.add(new TwitchData(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8)
                ));
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT username FROM shoutout");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) localShoutoutData.add(new ShoutoutData(rs.getString(1)));
        } catch (SQLException e) {
            throw new IOException(e);
        }
        Logger.info("Twitch and shoutout data loaded.");
    }

    private static class Holder {
        private static final TwitchRepository INSTANCE;

        static {
            try {
                INSTANCE = new TwitchRepository();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static TwitchRepository getInstance() {
        return Holder.INSTANCE;
    }
}
