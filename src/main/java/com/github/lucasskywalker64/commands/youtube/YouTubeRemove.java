package com.github.lucasskywalker64.commands.youtube;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;

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
        YouTubeData data = toBeRemoved.get();
        boolean unsubscribed = false;
        YouTubeImpl yt = BotMain.getContext().youTube();
        try {
            String token = yt.generateRandomToken();
            unsubscribed = yt.unsubscribeWithRetry(5, 200, token, data);
        } catch (ExecutionException | InterruptedException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to remove Youtube notification. Please contact the developer.").queue();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to remove Youtube notification. Please contact the developer.").queue();
            return;
        }

        try {
            if (unsubscribed) {
                repo.delete(data);
                yt.load();
                event.getHook().sendMessage(String.format("Youtube notification for %s removed.", name)).queue();
            } else {
                event.getHook().sendMessage(String.format("Failed to remove Youtube notification for %s.", name)).queue();
            }
        } catch (SQLException e) {
            Logger.error(e);
            String token = yt.generateRandomToken();
            try {
                if (yt.subscribeWithRetry(5, 200, token, data) == null) {
                    Logger.error("Failed to resubscribe to channel.");
                }
            } catch (IOException | InterruptedException | SQLException | ExecutionException ex) {
                Logger.error(ex);
            }
            event.getHook().sendMessage("ERROR: Failed to remove Youtube notification. Please contact the developer.").queue();
        }
    }
}
