package com.github.lucasskywalker64.ticket.model;

public record TicketScheduleData(
        int ticketId,
        long closeTimeEpoch,
        long channelId
) {
}
