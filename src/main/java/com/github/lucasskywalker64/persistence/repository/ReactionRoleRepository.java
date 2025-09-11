package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.data.ReactionRoleData;
import org.tinylog.Logger;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReactionRoleRepository {

    private final List<ReactionRoleData> localReactionRoleData;
    private final Connection conn = Database.getInstance().getConnection();

    public List<ReactionRoleData> loadAll() {
        return new ArrayList<>(localReactionRoleData);
    }

    public void saveAll(List<ReactionRoleData> reactionRoleData) throws IOException {
        saveAll(reactionRoleData, true);
    }

    public void saveAll(List<ReactionRoleData> reactionRoleData, boolean append) throws IOException {
        if (!localReactionRoleData.equals(reactionRoleData)) {
            try {
                if (!append) conn.createStatement().executeUpdate("DELETE FROM reaction_roles");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO reaction_roles (channelId, messageId, roleId, roleName, emoji) VALUES (?,?,?,?,?)")) {
                    for (ReactionRoleData d : reactionRoleData) {
                        ps.setString(1, d.channelId());
                        ps.setString(2, d.messageId());
                        ps.setString(3, d.roleId());
                        ps.setString(4, d.roleName());
                        ps.setString(5, d.emoji());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }
            if (!append)
                localReactionRoleData.clear();
            localReactionRoleData.addAll(reactionRoleData);
            Logger.info("Reaction role data saved.");
        }
    }

    private ReactionRoleRepository() throws IOException {
        localReactionRoleData = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT channelId, messageId, roleId, roleName, emoji FROM reaction_roles");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                localReactionRoleData.add(new ReactionRoleData(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5)
                ));
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        Logger.info("Reaction role data loaded.");
    }

    private static class Holder {
        private static final ReactionRoleRepository INSTANCE;

        static {
            try {
                INSTANCE = new ReactionRoleRepository();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static ReactionRoleRepository getInstance() {
        return Holder.INSTANCE;
    }
}
