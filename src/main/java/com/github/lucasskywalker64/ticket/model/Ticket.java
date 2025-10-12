package com.github.lucasskywalker64.ticket.model;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;

public record Ticket(
        int id,
        long openerId,
        long channelId,
        TicketStatus status,
        ZonedDateTime createdAt,
        ZonedDateTime closedAt,
        long closerId,
        String reason,
        long claimerId,
        String transcriptContent,
        String transcriptJson
) {
    public boolean isOpen() {
        return status == TicketStatus.OPEN;
    }

    public boolean isClosed() {
        return status == TicketStatus.CLOSED;
    }

    public Ticket withStatus(TicketStatus status) {
        return  new Ticket(id, openerId, channelId, status, createdAt, closedAt, closerId, reason, claimerId, transcriptContent, transcriptJson);
    }

    public Ticket withTranscriptContent(String transcriptContent) {
        return new Ticket(id, openerId, channelId, status, createdAt, closedAt, closerId, reason, claimerId, transcriptContent, transcriptJson);
    }

    public Ticket closeTicket(ZonedDateTime closedAt, long closerId, @Nullable String reason, String transcriptContent, String transcriptJson) {
        return new Ticket(id, openerId, channelId, TicketStatus.CLOSED, createdAt, closedAt, closerId, reason, claimerId, transcriptContent, transcriptJson);
    }

    public Ticket claimTicket(long claimerId) {
        return new Ticket(id, openerId, channelId, status, createdAt, closedAt, closerId, reason, claimerId, transcriptContent, transcriptJson);
    }

    public Ticket(int id, long openerId, long channelId) {
        this(id, openerId, channelId, TicketStatus.PENDING, ZonedDateTime.now(), null, 0L, null, 0L, null, null);
    }
}
