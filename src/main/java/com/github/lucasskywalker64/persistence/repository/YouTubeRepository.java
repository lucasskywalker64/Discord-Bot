package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import org.tinylog.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class YouTubeRepository {

    private final Connection conn = Database.getInstance().getConnection();
    private final List<YouTubeData> localYouTubeData;

    public List<YouTubeData> loadAll() {
        return new ArrayList<>(localYouTubeData);
    }

    public void saveAll(List<YouTubeData> youtubeData) throws IOException {
        saveAll(youtubeData, true);
    }

    public void saveAll(List<YouTubeData> youtubeData, boolean append) throws IOException {
        if (!localYouTubeData.equals(youtubeData)) {
            try {
                if (!append) {
                    conn.createStatement().executeUpdate("DELETE FROM youtube");
                }
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO youtube (channel, message, name, " +
                        "playlistId, roleId, videoId, streamId) VALUES (?,?,?,?,?,?,?)")) {
                    for (YouTubeData d : youtubeData) {
                        ps.setString(1, d.channel());
                        ps.setString(2, d.message());
                        ps.setString(3, d.name());
                        ps.setString(4, d.playlistId());
                        ps.setString(5, d.roleId());
                        ps.setString(6, d.videoId());
                        ps.setString(7, d.streamId());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }
            if (!append)
                localYouTubeData.clear();
            localYouTubeData.addAll(youtubeData);
            Logger.info("YouTube data saved.");
        }
    }

    private YouTubeRepository() throws SQLException {
        localYouTubeData = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT channel, message, name, playlistId, " +
                "roleId, videoId, streamId FROM youtube");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                localYouTubeData.add(new YouTubeData(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7)
                ));
            }
        }
        Logger.info("YouTube data loaded.");
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
