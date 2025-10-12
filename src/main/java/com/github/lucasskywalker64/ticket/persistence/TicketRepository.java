package com.github.lucasskywalker64.ticket.persistence;

import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.ticket.model.Ticket;
import com.github.lucasskywalker64.ticket.model.TicketConfig;
import com.github.lucasskywalker64.ticket.model.TicketScheduleData;
import com.github.lucasskywalker64.ticket.model.TicketStatus;
import net.dv8tion.jda.api.entities.Member;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;

public class TicketRepository {

    private final Connection conn = Database.getInstance().getConnection();

    public void saveTicket(Ticket ticket) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tickets (id, openerId, channelId, status, createdAt) " +
                        "VALUES (?,?,?,?,?)")) {
            ps.setInt(1, ticket.id());
            ps.setLong(2, ticket.openerId());
            ps.setLong(3, ticket.channelId());
            ps.setString(4, ticket.status().name());
            ps.setString(5, ticket.createdAt().toString());
            ps.executeUpdate();
        }
    }

    public void closeTicket(Ticket ticket) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE tickets SET status=?, closedAt=?, closerId=?, reason=?, transcriptContent=?, transcriptJson=? WHERE id=?")) {
            ps.setString(1, ticket.status().name());
            ps.setString(2, ticket.closedAt().toString());
            ps.setLong(3, ticket.closerId());
            ps.setString(4, ticket.reason());
            ps.setString(5, ticket.transcriptContent());
            ps.setString(6, ticket.transcriptJson());
            ps.setInt(7, ticket.id());
            ps.executeUpdate();
        }
    }

    public void updateStatus(Ticket ticket) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE tickets SET status=? WHERE id=?")) {
            ps.setString(1, ticket.status().name());
            ps.setInt(2, ticket.id());
            ps.executeUpdate();
        }
    }

    public void updateClaimerId(Ticket ticket) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE tickets SET claimerId=? WHERE id=?")) {
            ps.setLong(1, ticket.claimerId());
            ps.setInt(2, ticket.id());
            ps.executeUpdate();
        }
    }

    public void updateTranscriptContent(Ticket ticket) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE tickets SET transcriptContent=? WHERE id=?")) {
            ps.setString(1, ticket.transcriptContent());
            ps.setInt(2, ticket.id());
            ps.executeUpdate();
        }
    }

    public List<Ticket> getTickets() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM tickets ORDER BY id")) {
            return createTickets(ps.executeQuery());
        }
    }

    @Nullable
    public Ticket findById(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM tickets WHERE id = ?")) {
            ps.setLong(1, id);
            List<Ticket> tickets = createTickets(ps.executeQuery());
            if (tickets.isEmpty()) return null;
            return tickets.getFirst();
        }
    }

    public Optional<Ticket> findByChannelId(long channelId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM tickets WHERE channelId = ?")) {
            ps.setLong(1, channelId);
            List<Ticket> tickets = createTickets(ps.executeQuery());
            if (tickets.isEmpty()) return Optional.empty();
            return Optional.of(tickets.getFirst());
        }
    }

    public List<Ticket> findByOpenerId(long openerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM tickets WHERE openerId = ?")) {
            ps.setLong(1, openerId);
            List<Ticket> tickets = createTickets(ps.executeQuery());
            if (tickets.isEmpty()) return new ArrayList<>();
            return tickets;
        }
    }

    public TicketStatus getTicketStatus(long ticketId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM tickets WHERE id = ?")) {
            ps.setLong(1, ticketId);
            return TicketStatus.valueOf(ps.executeQuery().getString(1));
        }
    }

    public int getNewTicketId() {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT max(id) FROM tickets")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) + 1;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    public void saveScheduledClosure(TicketScheduleData data) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO scheduled_ticket_closures (ticketId, closeTimeEpoch, channelId) VALUES (?, ?, ?)")) {
            ps.setInt(1, data.ticketId());
            ps.setLong(2, data.closeTimeEpoch());
            ps.setLong(3, data.channelId());
            ps.executeUpdate();
        }
    }

    public List<TicketScheduleData> getAllScheduledClosures() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM scheduled_ticket_closures")) {
            ResultSet rs = ps.executeQuery();
            List<TicketScheduleData> scheduledClosures = new ArrayList<>();
            while (rs.next()) {
                scheduledClosures.add(new TicketScheduleData(
                        rs.getInt(1),
                        rs.getLong(2),
                        rs.getLong(3)
                ));
            }
            return scheduledClosures;
        }
    }

    public void deleteScheduledClosure(long ticketId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM scheduled_ticket_closures WHERE ticketId = ?")) {
            ps.setLong(1, ticketId);
            ps.executeUpdate();
        }
    }

    public void saveAttachmentKeysForTicket(List<String> keys, long ticketId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ticket_attachment_keys (ticketId, attachmentKey) VALUES (?,?)")) {
            for (String key : keys) {
                ps.setLong(1, ticketId);
                ps.setString(2, key);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<String> getAttachmentKeysForTicket(long ticketId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT attachmentKey FROM ticket_attachment_keys WHERE ticketId = ?")) {
            ps.setLong(1, ticketId);
            List<String> attachmentKeys = new ArrayList<>();
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                attachmentKeys.add(rs.getString(1));
            }
            return attachmentKeys;
        }
    }

    public void saveAuthorizedUsers(long ticketId, Set<Member> authorizedMembers) throws SQLException {
        conn.createStatement().executeUpdate("DELETE FROM ticket_authorized_users WHERE ticketId = " + ticketId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ticket_authorized_users (ticketId, userId) VALUES (?,?)")) {
            for (Member member : authorizedMembers) {
                ps.setLong(1, ticketId);
                ps.setLong(2, member.getIdLong());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<Long> getAuthorizedUsers(long ticketId) throws SQLException {
        List<Long> authorizedIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT userId FROM ticket_authorized_users WHERE ticketId = ?")) {
            ps.setLong(1, ticketId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                authorizedIds.add(rs.getLong(1));
            }
        }
        return authorizedIds;
    }

    public void saveConfig(TicketConfig ticketConfig) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ticket_config (ticketsCategoryId, supportRoleId, logChannelId, " +
                        "maxOpenTicketsPerUser, autoCloseAfter) VALUES (?,?,?,?,?) " +
                        "ON CONFLICT DO UPDATE SET " +
                        "ticketsCategoryId = excluded.ticketsCategoryId, " +
                        "supportRoleId = excluded.supportRoleId, " +
                        "logChannelId = excluded.logChannelId," +
                        "maxOpenTicketsPerUser = excluded.maxOpenTicketsPerUser," +
                        "autoCloseAfter = excluded.autoCloseAfter")) {
            ps.setLong(1, ticketConfig.ticketsCategoryId());
            ps.setLong(2, ticketConfig.supportRoleId());
            ps.setLong(3, ticketConfig.logChannelId());
            ps.setInt(4, ticketConfig.maxOpenTicketsPerUser());
            ps.setInt(5, ticketConfig.autoCloseAfter());
            ps.executeUpdate();
        }
    }

    public TicketConfig loadConfig() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM ticket_config")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new TicketConfig(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getInt(4),
                        rs.getInt(5)
                );
            }
        }
        return null;
    }

    private List<Ticket> createTickets(ResultSet rs) throws SQLException {
        List<Ticket> tickets = new ArrayList<>();
        while (rs.next()) {
            tickets.add(new Ticket(
                    rs.getInt(1),
                    rs.getLong(2),
                    rs.getLong(3),
                    TicketStatus.valueOf(rs.getString(4)),
                    ZonedDateTime.parse(rs.getString(5)),
                    rs.getString(6) != null
                            ? ZonedDateTime.parse(rs.getString(6))
                            : null,
                    rs.getLong(7),
                    rs.getString(8),
                    rs.getLong(9),
                    rs.getString(10),
                    rs.getString(11)
            ));
        }
        return tickets;
    }

    private TicketRepository() throws SQLException {}

    private static class Holder {
        private static final TicketRepository INSTANCE;

        static {
            try {
                INSTANCE = new TicketRepository();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static TicketRepository getInstance() {
        return Holder.INSTANCE;
    }
}
