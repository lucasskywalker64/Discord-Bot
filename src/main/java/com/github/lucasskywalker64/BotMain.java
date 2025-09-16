package com.github.lucasskywalker64;

import com.github.lucasskywalker64.persistence.repository.SettingsRepository;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.tinylog.Logger;

@SuppressWarnings({"DataFlowIssue"})
public class BotMain {

    private static final File botFile = new File(BotMain.class.getProtectionDomain()
            .getCodeSource()
            .getLocation().getPath());
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static ScheduledFuture<?> memberCountFuture;
    private static String channel;
    private static JDA jda;
    private static BotContext context;

    public static void setContext(BotContext context) {
        BotMain.context = context;
    }

    public static BotContext getContext() {
        return context;
    }

    public static void scheduleUpdateMemberCount() throws IOException {
        channel = SettingsRepository.getInstance().get("member_count_channel");
        if (channel.isEmpty())
            return;
        memberCountFuture = scheduler.scheduleAtFixedRate(BotMain::updateMemberCount, 0, 1, TimeUnit.HOURS);
        Logger.info("Set up member count scheduler");
    }

    public static void removeMemberCount() {
        jda.getVoiceChannelById(channel).delete().queue();
        channel = null;
        memberCountFuture.cancel(true);
    }

    private static void updateMemberCount() {
        jda.getVoiceChannelById(channel).getManager().setName("Member count: " +
                jda.getGuilds().getFirst().getMemberCount()).queue();
    }

    // TODO:first redeem counter (twitch channel point api), twitch quote system, ticket system
    public static void main(String[] args) {
        long startTime = System.nanoTime();
        Logger.info("Starting Discord API...");
        try {
            BotInitializer init = new BotInitializer(botFile, scheduler);
            init.start();
            jda = context.jda();
            scheduleUpdateMemberCount();
        } catch (InvalidTokenException e) {
            Logger.error("Invalid bot token!");
        } catch (Exception e) {
            Logger.error(e);
        }
        long elapsedNano = System.nanoTime() - startTime;
        long seconds = elapsedNano / 1_000_000_000L;
        long millis = (elapsedNano / 1_000_000) % 1000;
        Logger.info(String.format("Bot started in %d.%03d seconds", seconds, millis));
    }
}
