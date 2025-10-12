package com.github.lucasskywalker64.ticket.service;

import com.github.lucasskywalker64.BotContext;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.ticket.model.Ticket;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.*;

import static com.github.lucasskywalker64.BotConstants.GREEN;

public class TicketLogger {

    private final JDA jda = BotMain.getContext().jda();
    private final BotContext botContext = BotMain.getContext();
    private final TicketConfigHolder configHolder;

    public TicketLogger(TicketConfigHolder configHolder) {
        this.configHolder = configHolder;
    }

    public void logTicketCreated(Ticket ticket) {
        Guild guild = jda.getGuilds().getFirst();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Ticket Created")
                .setColor(GREEN)
                .addField("Ticket ID", String.valueOf(ticket.id()), true)
                .addField("Created By", guild.retrieveMemberById(ticket.openerId()).complete().getAsMention(), true);
        guild.getTextChannelById(configHolder.get().logChannelId()).sendMessageEmbeds(embed.build()).queue();
    }

    public void logTicketClosed(Ticket ticket) {
        Guild guild = jda.getGuilds().getFirst();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Ticket Closed")
                .setColor(Color.RED)
                .addField("Ticket ID", String.valueOf(ticket.id()), true)
                .addField("Created By", guild.retrieveMemberById(ticket.openerId()).complete().getAsMention(), true)
                .addField("Closed By", guild.retrieveMemberById(ticket.closerId()).complete().getAsMention(), true)
                .addField("Created At", String.format("<t:%d:F>", ticket.createdAt().toEpochSecond()), true)
                .addField("Claimed By", ticket.claimerId() != 0
                        ? guild.retrieveMemberById(ticket.claimerId()).complete().getAsMention()
                        : "Not claimed", true)
                .addBlankField(true)
                .addField("Reason", ticket.reason() != null
                        ? ticket.reason()
                        : "No reason specified", true);

        String transcriptUri;
        if ("prod".equals(System.getProperty("app.env", "prod"))) {
            transcriptUri = String.format("%s/tickets/transcripts/%d", botContext.config().get("SERVER_BASE_URL"), ticket.id());
        } else
            transcriptUri = String.format("%s:%s/tickets/transcripts/%d",
                    botContext.config().get("SERVER_BASE_URL"), botContext.config().get("SERVER_PORT"), ticket.id());

        ActionRow actionRow = ActionRow.of(
                Button.link(transcriptUri, "View Online Transcript")
        );
        guild.getTextChannelById(configHolder.get().logChannelId()).sendMessageEmbeds(embed.build())
                .setComponents(actionRow).queue();
        jda.retrieveUserById(ticket.openerId()).complete().openPrivateChannel().queue(channel ->
                channel.sendMessageEmbeds(embed.build()).setComponents(actionRow).queue());
    }
}
