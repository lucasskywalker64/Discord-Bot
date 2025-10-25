package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.data.YouTubeData;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YouTubeRepository {

    private final Connection conn = Database.getInstance().getConnection();

    public List<YouTubeData> loadAll() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM youtube ORDER BY guildId")) {
            return createYouTubeData(ps.executeQuery());
        }
    }

    public void save(YouTubeData data) throws SQLException {
        saveAll(Collections.singletonList(data));
    }

    public void saveAll(List<YouTubeData> youtubeData) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO youtube (" +
                        "channelId, " +
                        "name, " +
                        "guildId, " +
                        "discordChannelId, " +
                        "message, " +
                        "roleId, " +
                        "secret, " +
                        "expirationTime, " +
                        "videoId, " +
                        "streamId) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            for (YouTubeData d : youtubeData) {
                ps.setString(1, d.channelId());
                ps.setString(2, d.name());
                ps.setString(3, d.guildId());
                ps.setString(4, d.discordChannelId());
                ps.setString(5, d.message());
                ps.setString(6, d.roleId());
                ps.setString(7, d.secret());
                ps.setLong(8, d.expirationTime());
                ps.setString(9, d.videoId());
                ps.setString(10, d.streamId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Nullable
    public YouTubeData get(String channelId, String guildId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM youtube WHERE channelId = ? AND guildId = ?")) {
            ps.setString(1, channelId);
            ps.setString(2, guildId);
            List<YouTubeData> list = createYouTubeData(ps.executeQuery());
            if (list.isEmpty()) {
                return null;
            }
            return list.getFirst();
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

    private List<YouTubeData> createYouTubeData(ResultSet rs) throws SQLException {
        List<YouTubeData> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new YouTubeData(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getString(7),
                    rs.getLong(8),
                    rs.getString(9),
                    rs.getString(10)
            ));
        }
        return list;
    }

    private YouTubeRepository() throws SQLException {}

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
