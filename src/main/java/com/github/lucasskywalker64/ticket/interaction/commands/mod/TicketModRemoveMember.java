package com.github.lucasskywalker64.ticket.interaction.commands.mod;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.exceptions.InvalidParameterException;
import com.github.lucasskywalker64.ticket.TicketModule;
import com.github.lucasskywalker64.ticket.service.TicketService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class TicketModRemoveMember implements SubcommandModule {

    private final TicketModule module;
    private final TicketService service;

    public TicketModRemoveMember() {
        module = BotMain.getContext().ticketModule();
        service = module.getService();
    }

    @Override public String getRootName() { return "ticket-mod"; }
    @Override public String getSubcommandName() { return "remove-member"; }
    @Override public String getDescription() { return "Remove a Discord member from the ticket"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription()).addOptions(
                new OptionData(
                        OptionType.CHANNEL,
                        "ticket-channel",
                        "The channel of the ticket",
                        true).setChannelTypes(ChannelType.TEXT),
                new OptionData(
                        OptionType.STRING,
                        "member",
                        "The member that should be removed from the ticket",
                        true)
        );
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (!module.isSetup()) {
            event.reply("The ticket system is not set up.").setEphemeral(true).queue();
            return;
        }

        List<Member> members = CommandUtil.getMembers(event);
        try {
            service.removeMembers(event.getOption("ticket-channel").getAsChannel().asTextChannel(), members);
        } catch (InvalidParameterException e) {
            event.reply("Channel is not a ticket channel").setEphemeral(true).queue();
            return;
        } catch (SQLException e) {
            Logger.error(e);
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }
        event.replyFormat("The following members have been removed from the ticket\n%s",
                        members.stream()
                                .map(Member::getEffectiveName)
                                .collect(Collectors.joining("\n")))
                .setEphemeral(true).queue();
    }
}
