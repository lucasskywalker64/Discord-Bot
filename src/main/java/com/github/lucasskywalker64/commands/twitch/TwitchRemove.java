package com.github.lucasskywalker64.commands.twitch;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("DataFlowIssue")
public class TwitchRemove implements SubcommandModule {

    private final TwitchRepository repository = TwitchRepository.getInstance();

    @Override public String getRootName() { return "twitch"; }
    @Override public String getSubcommandName() { return "remove"; }
    @Override public String getDescription() { return "Remove a Twitch notification"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.STRING, "username", "The username of the Twitch channel", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        TwitchImpl twitch = BotMain.getContext().twitch();
        event.deferReply(true).queue();
        if (twitch == null) {
            CommandUtil.handleNoTwitchService(event);
            return;
        }
        String user = event.getOption("username").getAsString();
        List<TwitchData> list = repository.loadAll();
        var toBeRemoved = list.stream()
                .filter(d -> d.username().equalsIgnoreCase(user))
                .findFirst();
        if (toBeRemoved.isEmpty()) {
            event.getHook().sendMessage(String.format("The user %s is not in the list.", user)).queue();
            return;
        }
        list.remove(toBeRemoved.get());
        try {
            repository.saveAll(list, false);
            twitch.load();
            event.getHook().sendMessage(String.format("Twitch notification for %s removed.", user)).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to remove Twitch notification. Please contact the developer.").queue();
        }
    }
}
