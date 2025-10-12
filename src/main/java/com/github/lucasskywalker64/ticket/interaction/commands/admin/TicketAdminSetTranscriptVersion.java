package com.github.lucasskywalker64.ticket.interaction.commands.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.ticket.TicketModule;
import com.github.lucasskywalker64.ticket.model.Ticket;
import com.github.lucasskywalker64.ticket.persistence.TicketRepository;
import com.github.lucasskywalker64.ticket.service.TranscriptService;
import com.github.lucasskywalker64.web.VersionReader;
import com.github.zafarkhaja.semver.Version;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.sql.SQLException;
import java.util.List;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class TicketAdminSetTranscriptVersion implements SubcommandModule {

    private final TicketModule module;
    private final TicketRepository repository;
    private final TranscriptService transcriptService;

    public TicketAdminSetTranscriptVersion() {
        module = BotMain.getContext().ticketModule();
        repository = TicketRepository.getInstance();
        transcriptService = module.getService().getTranscriptService();
    }

    @Override public String getRootName() { return "ticket-admin"; }
    @Override public String getSubcommandName() { return "set-transcript-version"; }
    @Override public String getDescription() { return "Sets the transcript version and re-renders all outdated transcripts."; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
            .addOptions(
                    new OptionData(
                            OptionType.STRING,
                            "version",
                            "Version number in Semver format",
                            true));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (!module.isSetup()) {
            event.reply("Please set up the ticket system first.").setEphemeral(true).queue();
            return;
        }

        List<Ticket> tickets;
        try {
            tickets = repository.getTickets();
        } catch (SQLException e) {
            Logger.error(e);
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }
        if (tickets.isEmpty()) {
            event.reply("No tickets found.").setEphemeral(true).queue();
            return;
        }
        int updatedCount = 0;
        for (Ticket ticket : tickets) {
            if (ticket.transcriptContent() == null || ticket.transcriptContent().isEmpty()) {
                continue;
            }
            Version transcriptVersion = VersionReader.readVersion(ticket.transcriptContent());
            if (transcriptVersion == null) {
                event.replyFormat("Could not read transcript version for ticket %d", ticket.id()).setEphemeral(true).queue();
                return;
            }
            Version newVersion = Version.parse(event.getOption("version").getAsString());
            if (newVersion.isHigherThan(transcriptVersion)) {
                String html;
                try {
                    html = transcriptService.generateTranscriptFromJson(ticket.transcriptJson());
                    repository.updateTranscriptContent(ticket.withTranscriptContent(html));
                } catch (JsonProcessingException | SQLException e) {
                    Logger.error(e);
                    event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
                    return;
                }
                updatedCount++;
            }
        }
        event.replyFormat("%d transcripts were outdated and have been updated", updatedCount).setEphemeral(true).queue();
    }
}
