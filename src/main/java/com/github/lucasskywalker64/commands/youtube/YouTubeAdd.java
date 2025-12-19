package com.github.lucasskywalker64.commands.youtube;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.exceptions.InvalidParameterException;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

@SuppressWarnings("DataFlowIssue")
public class YouTubeAdd implements SubcommandModule {

    private final YouTubeRepository repo = YouTubeRepository.getInstance();
    private final YouTubeImpl youtube;
    private final Map<String, CompletableFuture<Integer>> pendingChallenges;
    private final ExecutorService executor;

    public YouTubeAdd(YouTubeImpl youtube) {
        this.youtube = youtube;
        pendingChallenges = BotMain.getContext().pendingChallenges();
        executor = BotMain.getContext().taskExecutor();
    }

    @Override public String getRootName() { return "youtube"; }
    @Override public String getSubcommandName() { return "add"; }
    @Override public String getDescription() { return "Add a YouTube notification"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOptions(
                        new OptionData(
                                OptionType.CHANNEL,
                                "channel",
                                "The channel that the notification should be posted to.",
                                true
                        ).setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD))
                .addOption(
                        OptionType.STRING,
                        "message",
                        "The message sent with the notification. Insert a \\n for a new line.",
                        true
                )
                .addOption(
                        OptionType.STRING,
                        "name",
                        "The @ handle or legacy username of the YouTube channel.",
                        true
                )
                .addOption(
                        OptionType.ROLE,
                        "role",
                        "The role that will be pinged with the notification.",
                        true
                );
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        InteractionHook hook = event.getHook();
        String token = youtube.generateRandomToken();
        String secret = youtube.generateRandomToken();
        String channelId;
        AtomicReference<YouTubeData> data = new AtomicReference<>();
        String name = event.getOption("name").getAsString();
        try {
            channelId = youtube.getChannelId(name);
            data.set(new YouTubeData(
                    channelId,
                    name,
                    event.getGuild().getId(),
                    event.getOption("channel").getAsChannel().getId(),
                    event.getOption("message").getAsString(),
                    event.getOption("role").getAsRole().getId(),
                    secret,
                    null,
                    new ArrayList<>()
            ));

            if (repo.contains(data.get())) {
                event.getHook().sendMessage(String.format(
                        "The user %s is already in the list.", event.getOption("name").getAsString())).queue();
                return;
            }
        } catch (InvalidParameterException e) {
            event.getHook().sendMessage(String.format("ERROR: Failed to find id for channel: %s.",
                    event.getOption("name").getAsString())).queue();
            if (e.getCause() != null) Logger.error(e.getCause());
            return;
        } catch (Exception e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to add Youtube notification. " +
                    "Please contact the developer.").queue();
            return;
        }

        executor.submit(() -> {
            final int MAX_RETRIES = 5;
            final long initialDelay = 200;

            try {
                data.set(youtube.subscribeWithRetry(MAX_RETRIES, initialDelay, token, data.get()));
                if (data.get() != null) {
                    hook.sendMessage("Youtube notification added.").queue();
                } else {
                    hook.sendMessage("ERROR: Googles servers did not respond after " +
                            MAX_RETRIES + " attempts. Please try again.").queue();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.warn("Subscription retry was interrupted.");
                hook.sendMessage(INTERNAL_ERROR).queue();
            } catch (Exception e) {
                Logger.error(e);
                hook.sendMessage("ERROR: Failed to add Youtube notification. " +
                        "Please contact the developer.").queue();
            } finally {
                pendingChallenges.remove(token);
            }
        });
    }
}
