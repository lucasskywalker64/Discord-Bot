package com.github.lucasskywalker64.commands.twitch;

import com.github.lucasskywalker64.BotContext;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("DataFlowIssue")
public class TwitchAdd implements SubcommandModule {

    private final TwitchRepository repository = TwitchRepository.getInstance();
    private final TwitchImpl twitch = BotMain.getContext().twitch();

    @Override public String getRootName() { return "twitch"; }
    @Override public String getSubcommandName() { return "add"; }
    @Override public String getDescription() { return "Add a Twitch notification"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOptions(new OptionData(OptionType.CHANNEL, "channel",
                        "Target channel", true).setChannelTypes(ChannelType.TEXT, ChannelType.NEWS))
                .addOption(OptionType.STRING,  "message",  "Message (use \\n for newline)", true)
                .addOption(OptionType.STRING,  "username", "Twitch username", true)
                .addOption(OptionType.ROLE,    "role",     "Role to ping");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        if (twitch == null) {
            CommandUtil.handleNoTwitchService(event);
            return;
        }
        try {
            String role = "";
            if (event.getOption("role") != null) {
                role = event.getOption("role").getAsRole().getId();
            }
            TwitchData data = new TwitchData(
                    event.getOption("channel").getAsString(),
                    event.getOption("message").getAsString(),
                    event.getOption("username").getAsString(),
                    role,
                    null,
                    0L,
                    null,
                    null
            );
            if (repository.loadAll().contains(data)) {
                event.getHook().sendMessage(String.format("The user %s is already in the list.",
                        event.getOption("username").getAsString())).queue();
                return;
            }
            repository.saveAll(Collections.singletonList(data));
            twitch.load();
            event.getHook().sendMessage("Twitch notification added.").queue();
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("Something went wrong while saving the twitch notification. " +
                            "Please contact the developer.").queue();
        }
    }
}
