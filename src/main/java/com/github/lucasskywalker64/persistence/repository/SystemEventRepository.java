package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.data.SystemEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SystemEventRepository {

    private final Connection conn = Database.getInstance().getConnection();



    private SystemEventRepository() throws SQLException {
        try (var ps = conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_sys_events_status ON system_events(status)");
            var ps2 = conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_sys_events_ref ON system_events(eventType, referenceId)")) {
            ps.executeUpdate();
            ps2.executeUpdate();
        }
    }

    public void saveEvent(SystemEvent event) throws SQLException {
        try (var ps = conn.prepareStatement("INSERT INTO system_events (id, referenceId, streamerId, domain, eventType, status, payload) " +
                "VALUES (?,?,?,?,?,?,?)" +
                "ON CONFLICT (id, domain) DO NOTHING")) {
            ps.setString(1, event.id());
            ps.setString(2, event.referenceId());
            ps.setString(3, event.streamerId());
            ps.setString(4, event.domain());
            ps.setString(5, event.eventType());
            ps.setString(6, event.status());
            ps.setString(7, event.payload());
            ps.executeUpdate();
        }
    }

    public void updateStatus(String id, String domain, String status) throws SQLException {
        try (var ps = conn.prepareStatement("UPDATE system_events SET status = ? WHERE id = ? AND domain = ?")) {
            ps.setString(1, status);
            ps.setString(2, id);
            ps.setString(3, domain);
            ps.executeUpdate();
        }
    }

    public List<SystemEvent> getPendingEvents() throws SQLException {
        String sql = "SELECT * FROM system_events WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 50";
        return fetchEvents(sql);
    }

    public List<SystemEvent> getUnfulfilledTwitchRedemptions(String streamerId) throws SQLException {
        String sql = """
                SELECT * FROM system_events
                WHERE eventType = 'channel.channel_points_custom_reward_redemption.add'
                AND streamerId = ?
                AND referenceId NOT IN (
                    SELECT referenceId FROM system_events
                    WHERE eventType = 'channel.channel_points_custom_reward_redemption.update'
                )
                ORDER BY createdAt ASC
            """;
        return fetchEvents(sql, streamerId);
    }

    public boolean eventExists(String id, String domain) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT 1 FROM system_events WHERE id = ? AND domain = ? LIMIT 1")) {
            ps.setString(1, id);
            ps.setString(2, domain);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<SystemEvent> fetchEvents(String sql, String... params) throws SQLException {
        List<SystemEvent> events = new ArrayList<>();
        try (var ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new SystemEvent(
                            rs.getString("id"), rs.getString("referenceId"), rs.getString("streamerId"),
                            rs.getString("domain"), rs.getString("eventType"), rs.getString("status"), rs.getString("payload")
                    ));
                }
            }
        }
        return events;
    }

    private static class Holder {
        private static final SystemEventRepository INSTANCE;

        static {
            try {
                INSTANCE = new SystemEventRepository();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static SystemEventRepository getInstance() {
        return Holder.INSTANCE;
    }
}
