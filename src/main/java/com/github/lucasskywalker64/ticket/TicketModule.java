package com.github.lucasskywalker64.ticket;

import com.github.lucasskywalker64.ticket.persistence.TicketRepository;
import com.github.lucasskywalker64.ticket.service.TicketChannelManager;
import com.github.lucasskywalker64.ticket.service.TicketConfigHolder;
import com.github.lucasskywalker64.ticket.service.TicketLogger;
import com.github.lucasskywalker64.ticket.service.TicketService;

import java.sql.SQLException;

public class TicketModule {

    private final TicketRepository repository = TicketRepository.getInstance();

    private TicketService service;
    private TicketConfigHolder configHolder;
    private boolean isSetup;

    public void init() throws SQLException {
        configHolder = new TicketConfigHolder(repository.loadConfig());
        TicketChannelManager channelManager = new TicketChannelManager(configHolder);
        service = new TicketService(channelManager, configHolder, new TicketLogger(configHolder));
        isSetup = configHolder.get() != null;
    }

    public TicketService getService() {
        return service;
    }

    public TicketConfigHolder getConfigHolder() {
        return configHolder;
    }

    public boolean isSetup() { return isSetup; }

    public void setSetup(boolean setup) { isSetup = setup; }
}
