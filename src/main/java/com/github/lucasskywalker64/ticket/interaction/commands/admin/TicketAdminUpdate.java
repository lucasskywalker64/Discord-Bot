package com.github.lucasskywalker64.ticket.interaction.commands.admin;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.ticket.TicketModule;
import com.github.lucasskywalker64.ticket.model.TicketConfig;
import com.github.lucasskywalker64.ticket.persistence.TicketRepository;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.sql.SQLException;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

@SuppressWarnings("DataFlowIssue")
public class TicketAdminUpdate implements SubcommandModule {

    private final TicketRepository repository = TicketRepository.getInstance();
    private final TicketModule module;

    public TicketAdminUpdate() {
        this.module = BotMain.getContext().ticketModule();
    }

    @Override
    public String getRootName() { return "ticket-admin"; }

    @Override
    public String getSubcommandName() { return "update"; }

    @Override
    public String getDescription() { return "Update the ticket system config. " +
            "Any option that isn't provided will retain the previous value."; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOptions(
                        new OptionData(
                                OptionType.CHANNEL,
                                "category",
                                "The Discord category where the tickets should be created")
                                .setChannelTypes(ChannelType.CATEGORY),
                        new OptionData(
                                OptionType.ROLE,
                                "support-role",
                                "The role that should be pinged in a new ticket"),
                        new OptionData(
                                OptionType.CHANNEL,
                                "log-channel",
                                "The channel where logs should be posted to")
                                .setChannelTypes(ChannelType.TEXT),
                        new OptionData(
                                OptionType.INTEGER,
                                "max-open-tickets-per-user",
                                "The maximum number of open tickets per user"),
                        new OptionData(
                                OptionType.INTEGER,
                                "auto-close-after",
                                "Auto close after x time (only supports full hour values)"));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        if (!module.isSetup()) {
            event.getHook().sendMessage("Config does not exist please create one.").queue();
            return;
        }
        TicketConfig oldConf = module.getConfigHolder().get();

        long supportRoleId;
        if (event.getOption("support-role") != null) {
            supportRoleId = event.getOption("support-role").getAsRole().getIdLong();
        } else supportRoleId = oldConf.supportRoleId();

        long categoryId;
        if (event.getOption("category") != null) {
            categoryId = event.getOption("category").getAsChannel().getIdLong();
        } else categoryId = oldConf.ticketsCategoryId();

        long logChannelId;
        if (event.getOption("log-channel") != null) {
            logChannelId = event.getOption("log-channel").getAsChannel().getIdLong();
        } else logChannelId = oldConf.logChannelId();

        int maxTickets;
        if (event.getOption("max-open-tickets-per-user") != null) {
            maxTickets = event.getOption("max-open-tickets-per-user").getAsInt();
        } else maxTickets = oldConf.maxOpenTicketsPerUser();

        int autoCloseAfter;
        if (event.getOption("auto-close-after") != null) {
            autoCloseAfter = event.getOption("auto-close-after").getAsInt();
        } else autoCloseAfter = oldConf.autoCloseAfter();

        TicketConfig config = new TicketConfig(
                categoryId,
                supportRoleId,
                logChannelId,
                maxTickets,
                autoCloseAfter
        );
        try {
            repository.saveConfig(config);
        } catch (SQLException e) {
            Logger.error(e);
            event.getHook().sendMessage(INTERNAL_ERROR).queue();
            return;
        }
        module.getConfigHolder().set(config);
        event.getHook().sendMessage("Config successfully updated!").queue();
    }
}
