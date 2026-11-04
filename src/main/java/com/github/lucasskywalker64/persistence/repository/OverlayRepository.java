package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.data.OverlayData;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OverlayRepository {

    private final Connection conn = Database.getInstance().getConnection();

    public Optional<OverlayData> getOverlayByToken(String token) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT * FROM overlays WHERE token = ?")) {
            ps.setString(1, token);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToOverlay(rs));
            }
        }
        return Optional.empty();
    }

    public List<OverlayData> getByStreamerId(String streamerId) throws SQLException {
        List<OverlayData> overlays = new ArrayList<>();
        try (var ps = conn.prepareStatement("SELECT * FROM overlays WHERE streamerId = ?")) {
            ps.setString(1, streamerId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    overlays.add(mapRowToOverlay(rs));
                }
            }
        }
        return overlays;
    }

    public void createOverlay(OverlayData overlay) throws SQLException {
        try (var ps = conn.prepareStatement("INSERT INTO overlays(id, streamerId, type, token, settings) VALUES(?, ?, ?, ?, ?)")) {
            ps.setString(1, overlay.id());
            ps.setString(2, overlay.streamerId());
            ps.setString(3, overlay.type());
            ps.setString(4, overlay.token());
            ps.setString(5, overlay.settings());
            ps.executeUpdate();
        }
    }

    public boolean updateOverlaySettings(String id, String streamerId, String settings) throws SQLException {
        try (var ps = conn.prepareStatement("UPDATE overlays SET settings = ? WHERE id = ? AND streamerId = ?")) {
            ps.setString(1, settings);
            ps.setString(2, id);
            ps.setString(3, streamerId);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        }
    }

    public boolean deleteOverlay(String id, String streamerId) throws SQLException {
        try (var ps = conn.prepareStatement("DELETE FROM overlays WHERE id = ? AND streamerId = ?")) {
            ps.setString(1, id);
            ps.setString(2, streamerId);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        }
    }

    public String regenerateToken(String id) throws SQLException {
        String newToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        try (var ps = conn.prepareStatement("UPDATE overlays SET token = ? WHERE id = ?")) {
            ps.setString(1, newToken);
            ps.setString(2, id);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No overlay found with ID " + id);
            }
            return newToken;
        }
    }

    private OverlayData mapRowToOverlay(ResultSet rs) throws SQLException {
        return new OverlayData(
                rs.getString("id"),
                rs.getString("streamerId"),
                rs.getString("type"),
                rs.getString("token"),
                rs.getString("settings")
        );
    }

    private OverlayRepository() throws SQLException {}

    private static class Holder {
        private static final OverlayRepository INSTANCE;

        static {
            try {
                INSTANCE = new OverlayRepository();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static OverlayRepository getInstance() {
        return Holder.INSTANCE;
    }
}
