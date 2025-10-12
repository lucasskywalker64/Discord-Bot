package com.github.lucasskywalker64.ticket.interaction.commands;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.ticket.TicketModule;
import com.github.lucasskywalker64.ticket.model.Ticket;
import com.github.lucasskywalker64.ticket.persistence.TicketRepository;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.tinylog.Logger;

import java.sql.SQLException;
import java.util.List;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class TicketList implements SubcommandModule {

    private final TicketRepository repository;
    private final Dotenv config;
    private final TicketModule module;

    public TicketList() {
        repository = TicketRepository.getInstance();
        config = BotMain.getContext().config();
        this.module = BotMain.getContext().ticketModule();
    }

    @Override public String getRootName() { return "ticket"; }
    @Override public String getSubcommandName() { return "list"; }
    @Override public String getDescription() { return "List all your tickets"; }

    @Override
    public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (!module.isSetup()) {
            event.reply("The ticket system is not set up.").setEphemeral(true).queue();
            return;
        }

        List<Ticket> tickets;
        try {
            tickets = repository.findByOpenerId(event.getMember().getIdLong());
        } catch (SQLException e) {
            Logger.error(e);
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }

        if (tickets.isEmpty()) {
            event.reply("No tickets found.").setEphemeral(true).queue();
            return;
        }

        String url;
        if ("prod".equals(System.getProperty("app.env", "prod"))) {
            url = String.format("%s/tickets?guild_id=%d",
                    config.get("SERVER_BASE_URL"), event.getGuild().getIdLong());
        } else
            url = String.format("%s:%s/tickets?guild_id=%d",
                    config.get("SERVER_BASE_URL"), config.get("SERVER_PORT"), event.getGuild().getIdLong());

        ActionRow link = ActionRow.of(
                Button.link(url, "View Your Tickets")
        );
        event.replyComponents(link).setEphemeral(true).queue();
    }
}
