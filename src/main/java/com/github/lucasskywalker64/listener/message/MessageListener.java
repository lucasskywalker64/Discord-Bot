package com.github.lucasskywalker64.listener.message;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.ticket.service.TicketService;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MessageListener extends ListenerAdapter {

    private final TicketService ticketService;

    public MessageListener() {
        ticketService = BotMain.getContext().ticketModule().getService();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        ticketService.ticketMessageReceived(event.getChannel().getIdLong(), event.getMember().getIdLong());
    }
}
