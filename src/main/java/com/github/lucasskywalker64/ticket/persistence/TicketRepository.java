package com.github.lucasskywalker64.ticket.persistence;

import com.github.lucasskywalker64.ticket.model.Ticket;
import com.github.lucasskywalker64.ticket.model.TicketConfig;

import java.util.Optional;

public interface TicketRepository {
    Ticket save(Ticket ticket);
    TicketConfig saveConfig(TicketConfig ticketConfig);
    Optional<Ticket> findById(long id);
    Optional<Ticket> findByChannelId(long channelId);
    Optional<Ticket> findOpenByUserId(long userId);
}
