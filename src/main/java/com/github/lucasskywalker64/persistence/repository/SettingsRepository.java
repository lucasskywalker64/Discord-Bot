package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.persistence.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SettingsRepository {

    private final Connection conn = Database.getInstance().getConnection();

    public String get(String key) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException ignored) {}
        return "";
    }

    public void set(String key, String value) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO settings(key,value) VALUES(?,?) " +
                     "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private static class Holder {
        private static final SettingsRepository INSTANCE = new SettingsRepository();
    }
    public static SettingsRepository getInstance() {
        return Holder.INSTANCE;
    }
}
