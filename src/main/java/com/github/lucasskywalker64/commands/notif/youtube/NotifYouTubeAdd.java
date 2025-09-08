package com.github.lucasskywalker64.commands.notif.youtube;

import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.exceptions.InvalidParameterException;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

@SuppressWarnings("DataFlowIssue")
public class NotifYouTubeAdd implements SubcommandModule {

    private final YouTubeRepository repo = YouTubeRepository.getInstance();
    private final YouTubeImpl youtube;

    public NotifYouTubeAdd(YouTubeImpl youtube) {
        this.youtube = youtube;
    }

    @Override public String getRootName() { return "notif"; }
    @Override public String getGroupName() { return "youtube"; }
    @Override public String getSubcommandName() { return "add"; }
    @Override public String getDescription() { return "Add a YouTube notification"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "The channel that the notification should be posted to.", true)
                        .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD))
                .addOption(OptionType.STRING, "message", "The message sent with the notification. Insert a \\n for a new line.", true)
                .addOption(OptionType.STRING, "name", "The @ handle or legacy username of the YouTube channel.", true)
                .addOption(OptionType.ROLE, "role", "The role that will be pinged with the notification.", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        try {
            String playlistId = youtube.getPlaylistIdFromChannelName(event.getOption("name").getAsString());
            YouTubeData data = new YouTubeData(
                    event.getOption("channel").getAsString(),
                    event.getOption("message").getAsString(),
                    event.getOption("name").getAsString(),
                    playlistId,
                    event.getOption("role").getAsRole().getId(),
                    "",
                    ""
            );

            if (!repo.loadAll().contains(data)) {
                event.getHook().sendMessage(String.format(
                        "The user %s is already in the list.", event.getOption("name").getAsString())).queue();
                return;
            }
            repo.saveAll(Collections.singletonList(data));
            event.getHook().sendMessage("Youtube notification added.").queue();
        } catch (InvalidParameterException e) {
            event.getHook().sendMessage(String.format("ERROR: Failed to find upload playlist for channel: %s.",
                    event.getOption("name").getAsString())).queue();
            if (e.getCause() != null) Logger.error(e.getCause());
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to add Youtube notification. " +
                    "Please contact the developer.").queue();
        }
    }
}
