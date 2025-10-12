package com.github.lucasskywalker64.ticket.model;

public record TicketConfig(
        long ticketsCategoryId,
        long supportRoleId,
        long logChannelId,
        int maxOpenTicketsPerUser,
        int autoCloseAfter
) {
}
