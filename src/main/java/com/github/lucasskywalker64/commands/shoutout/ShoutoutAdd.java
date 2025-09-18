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
public class ShoutoutAdd implements SubcommandModule {

    private final TwitchRepository repo = TwitchRepository.getInstance();

    @Override public String getRootName() { return "shoutout"; }
    @Override public String getSubcommandName() { return "add"; }
    @Override public String getDescription() { return "Add users to be shouted out on Twitch"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.STRING, "username", "Usernames separated by ;", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        TwitchImpl twitch = BotMain.getContext().twitch();
        event.deferReply(true).queue();
        if (twitch == null) {
            CommandUtil.handleNoTwitchService(event);
            return;
        }
        List<ShoutoutData> shoutoutData = Arrays.stream(event.getOption("username").getAsString().split(";"))
                .map(ShoutoutData::new).toList();

        List<ShoutoutData> oldShoutoutData = repo.loadAllShoutout();
        if (oldShoutoutData.stream().anyMatch(shoutoutData::contains)) {
            List<ShoutoutData> names = oldShoutoutData.stream().filter(shoutoutData::contains).toList();
            event.getHook().sendMessage(String.format("The following names are already in the list " +
                    "please remove them and try again\n%s", String.join("\n", names.stream()
                    .map(ShoutoutData::username).toList()))).queue();
            return;
        }
        try {
            repo.saveAllShoutout(shoutoutData);
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to add Shout out! Please contact the developer.").queue();
        }
        twitch.load();
        event.getHook().sendMessage("Shout out added.").queue();
    }
}
