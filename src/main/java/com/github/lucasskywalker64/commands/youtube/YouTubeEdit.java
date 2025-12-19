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
import java.util.stream.IntStream;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

@SuppressWarnings("DataFlowIssue")
public class YouTubeEdit implements SubcommandModule {

    private final YouTubeRepository repo = YouTubeRepository.getInstance();

    @Override public String getRootName() { return "youtube"; }
    @Override public String getSubcommandName() { return "edit"; }
    @Override public String getDescription() { return "Edit a YouTube notification"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.STRING, "name", "The @ handle or legacy username of the YouTube channel.", true)
                .addOption(OptionType.STRING, "message", "The new message.")
                .addOption(OptionType.ROLE, "role", "The new role.");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        List<YouTubeData> list;
        try {
            list = repo.loadAll();
        } catch (SQLException e) {
            Logger.error(e);
            event.getHook().sendMessage(INTERNAL_ERROR).queue();
            return;
        }
        if (list.isEmpty()) {
            event.getHook().sendMessage("There are no YouTube notifications.").queue();
            return;
        }
        String name = event.getOption("name").getAsString();
        int idx = IntStream.range(0, list.size())
                .filter(i -> list.get(i).name().equalsIgnoreCase(name))
                .findFirst().orElse(-1);
        if (idx == -1) {
            event.getHook().sendMessage(String.format("There is no notification for the user %s", name)).queue();
            return;
        }
        YouTubeData data = list.get(idx);
        data.updateList(event, list, idx);
        try {
            repo.saveAll(list);
        } catch (SQLException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to edit YouTube notification. " +
                    "Please contact the developer.").queue();
        }
        event.getHook().sendMessage("YouTube notification edited.").queue();
    }
}
