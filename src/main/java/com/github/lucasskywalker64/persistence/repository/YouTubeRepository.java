package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import org.tinylog.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YouTubeRepository {

    private final Connection conn = Database.getInstance().getConnection();
    private final List<YouTubeData> localYouTubeData;

    public List<YouTubeData> loadAll() {
        return new ArrayList<>(localYouTubeData);
    }

    public void save(YouTubeData data) throws SQLException {
        saveAll(Collections.singletonList(data));
    }

    public void saveAll(List<YouTubeData> youtubeData) throws SQLException {
        saveAll(youtubeData, true);
    }

    public void saveAll(List<YouTubeData> youtubeData, boolean append) throws SQLException {
        if (!localYouTubeData.equals(youtubeData)) {
            if (!append) {
                conn.createStatement().executeUpdate("DELETE FROM youtube");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO youtube (channelId, name, guildId, channel, message, roleId, secret, expirationTime) " +
                            "VALUES (?,?,?,?,?,?,?,?)")) {
                for (YouTubeData d : youtubeData) {
                    ps.setString(1, d.channelId());
                    ps.setString(2, d.name());
                    ps.setString(3, d.guildId());
                    ps.setString(4, d.channel());
                    ps.setString(5, d.message());
                    ps.setString(6, d.roleId());
                    ps.setString(7, d.secret());
                    ps.setLong(8, d.expirationTime());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            if (!append)
                localYouTubeData.clear();
            localYouTubeData.addAll(youtubeData);
            Logger.info("YouTube data saved.");
        }
    }

    public String getSecret(String channelId, String guildId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT secret FROM youtube WHERE channelId = ? AND guildId = ?")) {
            ps.setString(1, channelId);
            ps.setString(2, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("secret");
            }
        }
    }

    public Long getExpirationTime(String channelId, String guildId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT expirationTime FROM youtube WHERE channelId = ? AND guildId = ?")) {
            ps.setString(1, channelId);
            ps.setString(2, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("expirationTime");
            }
        }
    }

    private YouTubeRepository() throws SQLException {
        localYouTubeData = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT channelId, name, guildId, channel, message, roleId, secret, expirationTime FROM youtube");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                localYouTubeData.add(new YouTubeData(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7),
                        rs.getLong(8)
                ));
            }
        }
        Logger.info("YouTube data loaded.");
    }

    public boolean contains(YouTubeData data) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM youtube WHERE channelId = ? AND guildId = ? LIMIT 1")) {
            ps.setString(1, data.channelId());
            ps.setString(2, data.guildId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static class Holder {
        private static final YouTubeRepository INSTANCE;

        static {
            try {
                INSTANCE = new YouTubeRepository();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static YouTubeRepository getInstance() {
        return Holder.INSTANCE;
    }
}
