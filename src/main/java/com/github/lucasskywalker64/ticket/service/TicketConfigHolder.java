package com.github.lucasskywalker64.ticket.service;

import com.github.lucasskywalker64.ticket.model.TicketConfig;

public class TicketConfigHolder {

    private TicketConfig ticketConfig;
    public TicketConfigHolder(TicketConfig ticketConfig) {
        this.ticketConfig = ticketConfig;
    }
    public TicketConfig get() {
        return ticketConfig;
    }
    public void set(TicketConfig ticketConfig) {
        this.ticketConfig = ticketConfig;
    }
}
