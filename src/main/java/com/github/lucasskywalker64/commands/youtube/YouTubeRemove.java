package com.github.lucasskywalker64.commands.youtube;

import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.sql.SQLException;
import java.util.List;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

@SuppressWarnings("DataFlowIssue")
public class YouTubeRemove implements SubcommandModule {

    private final YouTubeRepository repo = YouTubeRepository.getInstance();

    @Override public String getRootName() { return "youtube"; }
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
        List<YouTubeData> list;
        try {
            list = repo.loadAll();
        } catch (SQLException e) {
            Logger.error(e);
            event.getHook().sendMessage(INTERNAL_ERROR).queue();
            return;
        }
        var toBeRemoved = list.stream().filter(d -> d.name().equalsIgnoreCase(name)).findFirst();
        if (toBeRemoved.isEmpty()) {
            event.getHook().sendMessage(String.format("The user %s is not in the list.", name)).queue();
            return;
        }
        list.remove(toBeRemoved.get());
        try {
            repo.saveAll(list);
        } catch (SQLException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to remove Youtube notification. Please contact the developer.").queue();
        }
        event.getHook().sendMessage(String.format("Youtube notification for %s removed.", name)).queue();
    }
}
