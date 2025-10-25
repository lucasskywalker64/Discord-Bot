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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
        String token = generateRandomSecret();
        String secret = generateRandomSecret();
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
                    null,
                    null
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
            try {
                CompletableFuture<Integer> challengeFuture = new CompletableFuture<>();
                pendingChallenges.put(token, challengeFuture);

                youtube.subscribeToChannel(
                        channelId,
                        event.getGuild().getId(),
                        secret,
                        token
                );

                int leaseSeconds = challengeFuture.get(15, TimeUnit.SECONDS);
                long expirationTime = System.currentTimeMillis() + leaseSeconds * 1000L;
                data.set(data.get().withExpirationTime(expirationTime));
                repo.save(data.get());
                youtube.load();

                hook.sendMessage("Youtube notification added.").queue();
            } catch (TimeoutException e) {
                Logger.error(e);
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

    private static String generateRandomSecret() {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[64];
        random.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
