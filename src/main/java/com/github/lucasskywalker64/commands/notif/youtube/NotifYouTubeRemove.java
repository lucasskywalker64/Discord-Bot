package com.github.lucasskywalker64.commands.notif.youtube;

import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("DataFlowIssue")
public class NotifYouTubeRemove implements SubcommandModule {

    private final YouTubeRepository repo = YouTubeRepository.getInstance();

    @Override public String getRootName() { return "notif"; }
    @Override public String getGroupName() { return "youtube"; }
    @Override public String getSubcommandName() { return "remove"; }
    @Override public String getDescription() { return "Remove a YouTube notification"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.STRING, "name", "The @ handle or legacy username of the YouTube channel.", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String name = event.getOption("name").getAsString();
        List<YouTubeData> list = repo.loadAll();
        var toBeRemoved = list.stream().filter(d -> d.name().equalsIgnoreCase(name)).findFirst();
        if (toBeRemoved.isEmpty()) {
            event.getHook().sendMessage(String.format("The user %s is not in the list.", name)).queue();
            return;
        }
        list.remove(toBeRemoved.get());
        try {
            repo.saveAll(list, false);
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to remove Youtube notification. Please contact the developer.").queue();
        }
        event.getHook().sendMessage(String.format("Youtube notification for %s removed.", name)).queue();
    }
}
