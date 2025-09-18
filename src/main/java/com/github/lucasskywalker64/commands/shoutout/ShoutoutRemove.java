package com.github.lucasskywalker64.commands.shoutout;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.ShoutoutData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.*;

@SuppressWarnings("DataFlowIssue")
public class ShoutoutRemove implements SubcommandModule {

    private final TwitchRepository repo = TwitchRepository.getInstance();

    @Override public String getRootName() { return "shoutout"; }
    @Override public String getSubcommandName() { return "remove"; }
    @Override public String getDescription() { return "Removes users from the shoutout list"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.STRING, "username", "Usernames seperated by ;", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        TwitchImpl twitch = BotMain.getContext().twitch();
        event.deferReply(true).queue();
        if (twitch == null) {
            CommandUtil.handleNoTwitchService(event);
            return;
        }
        List<ShoutoutData> toRemove = new ArrayList<>(Arrays.stream(event.getOption("username").getAsString()
                        .split(";")).map(ShoutoutData::new).toList());
        List<ShoutoutData> oldShoutoutData = repo.loadAllShoutout();
        List<ShoutoutData> removed = new ArrayList<>(oldShoutoutData);
        removed.retainAll(toRemove);
        if (!oldShoutoutData.removeAll(toRemove)) {
            event.getHook().sendMessage(String.format("No username was removed. Ensure that the following names are in the " +
                                    "list and spelled correctly\n%s",
                            String.join("\n", toRemove.stream().map(ShoutoutData::username).toList()))).queue();
            return;
        }
        try {
            repo.saveAllShoutout(oldShoutoutData, false);
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to add Shout out! Please contact the developer.")
                    .queue();
        }
        twitch.load();
        event.getHook().sendMessage(String.format("Following usernames were removed\n%s", String.join("\n",
                        removed.stream().map(ShoutoutData::username).toList()))).queue();
    }
}
