package com.github.lucasskywalker64.commands.shoutout;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.ArrayList;

@SuppressWarnings("DataFlowIssue")
public class ShoutoutRemoveAll implements SubcommandModule {

    private final TwitchRepository repo = TwitchRepository.getInstance();

    @Override public String getRootName() { return "shoutout"; }
    @Override public String getSubcommandName() { return "removeall"; }
    @Override public String getDescription() { return "Removes all users from the shoutout list"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.BOOLEAN, "are-you-sure", "Select true to confirm the removal of all shoutouts.", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        TwitchImpl twitch = BotMain.getContext().twitch();
        event.deferReply(true).queue();
        if (twitch == null) {
            CommandUtil.handleNoTwitchService(event);
            return;
        }
        if (!event.getOption("are-you-sure").getAsBoolean()) {
            event.getHook().sendMessage("Please confirm that you want to remove all shoutouts.").queue();
            return;
        }
        try {
            repo.saveAllShoutout(new ArrayList<>(), false);
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to remove shoutouts! Please contact the developer.")
                    .queue();
        }
        twitch.load();
        event.getHook().sendMessage("All shoutouts removed.").queue();
    }
}
