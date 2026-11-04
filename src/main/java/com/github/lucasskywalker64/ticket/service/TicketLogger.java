package com.github.lucasskywalker64.ticket.service;

import com.github.lucasskywalker64.BotContext;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.ticket.model.Ticket;
import com.github.lucasskywalker64.web.Routes;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.tinylog.Logger;

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
                .addField("Created By", UserSnowflake.fromId(ticket.openerId()).getAsMention(), true);

        TextChannel logChannel = guild.getTextChannelById(configHolder.get().logChannelId());
        if (logChannel != null) {
            logChannel.sendMessageEmbeds(embed.build()).queue(
                    null,
                    err -> Logger.error(err, "Failed to send ticket created log message")
            );
        } else {
            Logger.warn("Ticket log channel {} not found", configHolder.get().logChannelId());
        }
    }

    public void logTicketClosed(Ticket ticket) {
        Guild guild = jda.getGuilds().getFirst();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Ticket Closed")
                .setColor(Color.RED)
                .addField("Ticket ID", String.valueOf(ticket.id()), true)
                .addField("Created By", UserSnowflake.fromId(ticket.openerId()).getAsMention(), true)
                .addField("Closed By", UserSnowflake.fromId(ticket.closerId()).getAsMention(), true)
                .addField("Created At", String.format("<t:%d:F>", ticket.createdAt().toEpochSecond()), true)
                .addField("Claimed By", ticket.claimerId() != 0
                        ? UserSnowflake.fromId(ticket.claimerId()).getAsMention()
                        : "Not claimed", true)
                .addBlankField(true)
                .addField("Reason", ticket.reason() != null
                        ? ticket.reason()
                        : "No reason specified", true);

        String transcriptUri;
        if ("prod".equals(System.getProperty("app.env", "prod"))) {
            transcriptUri = Routes.buildTranscriptUrl(botContext.config().get("SERVER_BASE_URL"), ticket.id());
        } else {
            transcriptUri = Routes.buildTranscriptUrl(botContext.config().get("SERVER_BASE_URL")
                    + ":" + botContext.config().get("SERVER_PORT"), ticket.id());
        }

        ActionRow actionRow = ActionRow.of(
                Button.link(transcriptUri, "View Online Transcript")
        );

        TextChannel logChannel = guild.getTextChannelById(configHolder.get().logChannelId());
        if (logChannel != null) {
            logChannel.sendMessageEmbeds(embed.build()).setComponents(actionRow).queue(
                    null,
                    err -> Logger.error(err, "Failed to send ticket closed embed to log channel")
            );
        } else {
            Logger.warn("Ticket log channel {} not found", configHolder.get().logChannelId());
        }

        jda.retrieveUserById(ticket.openerId()).queue(
                user -> user.openPrivateChannel().queue(
                        channel -> channel.sendMessageEmbeds(embed.build()).setComponents(actionRow).queue(
                                null,
                                err -> Logger.warn("Could not DM ticket closed notice to user {}: {}", ticket.openerId(), err.getMessage())
                        ),
                        err -> Logger.warn("Could not open DM channel with user {}: {}", ticket.openerId(), err.getMessage())
                ),
                err -> Logger.warn("Could not retrieve user {} for ticket closed notice: {}", ticket.openerId(), err.getMessage())
        );
    }
}
