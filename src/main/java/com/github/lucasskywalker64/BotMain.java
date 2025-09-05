package com.github.lucasskywalker64;

import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.listener.role.ReactionRoleManager;
import com.github.lucasskywalker64.persistence.PersistenceUtil;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.listener.command.SlashCommandManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Option;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.tinylog.Logger;

@SuppressWarnings({"DataFlowIssue", "ResultOfMethodCallIgnored"})
public class BotMain {

    private static final File botFile = new File(BotMain.class.getProtectionDomain()
            .getCodeSource()
            .getLocation().getPath());
    private static final Dotenv CONFIG =
            Dotenv.configure().directory(botFile.getParentFile().getAbsolutePath()).load();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static ScheduledFuture<?> memberCountFuture;
    private static File reactionRolesFile;
    private static File youTubeFile;
    private static File twitchFile;
    private static File shoutoutFile;
    private static File shoutedOutFile;
    private static File moderatorFile;
    private static File memberCountFile;
    private static JDA discordAPI;
    private static TwitchImpl twitch;
    private static YouTubeImpl youtube;
    private static String channel;
    private static ReactionRoleManager reactionRoleManager;

    public static Dotenv getConfig() {
        return CONFIG;
    }

    public static File getReactionRolesFile() {
        return reactionRolesFile;
    }

    public static File getYouTubeFile() {
        return youTubeFile;
    }

    public static File getTwitchFile() {
        return twitchFile;
    }

    public static File getShoutoutFile() {
        return shoutoutFile;
    }

    public static File getShoutedOutFile() {
        return shoutedOutFile;
    }

    public static File getModeratorFile() {
        return moderatorFile;
    }

    public static File getMemberCountFile() {
        return memberCountFile;
    }

    public static TwitchImpl getTwitch() {
        return twitch;
    }

    public static YouTubeImpl getYouTube() {
        return youtube;
    }

    public static ReactionRoleManager getReactionRoleManager() {
        return reactionRoleManager;
    }


    public static void scheduleUpdateMemberCount() throws IOException {
        channel = PersistenceUtil.readFileAsString(memberCountFile.toPath());
        if (channel.isEmpty())
            return;
        memberCountFuture = scheduler.scheduleAtFixedRate(BotMain::updateMemberCount, 0, 1, TimeUnit.HOURS);
        Logger.info("Set up member count scheduler");
    }

    public static void removeMemberCount() {
        discordAPI.getVoiceChannelById(channel).delete().queue();
        channel = null;
        memberCountFuture.cancel(true);
    }

    private static void updateMemberCount() {
        discordAPI.getVoiceChannelById(channel).getManager().setName("Member count: " +
                discordAPI.getGuilds().getFirst().getMemberCount()).queue();
    }

    private static boolean init(List<CommandData> commandDataList) throws Exception {
        discordAPI = JDABuilder.createDefault(CONFIG.get("BOT_TOKEN"),
                        GatewayIntent.GUILD_EXPRESSIONS,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS,
                        GatewayIntent.GUILD_MEMBERS)
                .build();
        File botFiles = new File(new File(BotMain.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                + "/bot_files");
        if (!botFiles.exists()) {
            botFiles.mkdir();
        }
        reactionRolesFile = new File(botFiles + "/reaction-roles.csv");
        if (!reactionRolesFile.exists()) {
            reactionRolesFile.createNewFile();
        }
        youTubeFile = new File(botFiles + "/youtube.csv");
        if (!youTubeFile.exists()) {
            youTubeFile.createNewFile();
        }
        twitchFile = new File(botFiles + "/twitch.csv");
        if (!twitchFile.exists()) {
            twitchFile.createNewFile();
        }
        shoutoutFile = new File(botFiles + "/shoutout.csv");
        if (!shoutoutFile.exists()) {
            shoutoutFile.createNewFile();
        }
        shoutedOutFile = new File(botFiles + "/shoutedout.txt");
        if (!shoutedOutFile.exists()) {
            shoutedOutFile.createNewFile();
        }
        moderatorFile = new File(botFiles + "/moderator.txt");
        if (!moderatorFile.exists()) {
            moderatorFile.createNewFile();
        }
        memberCountFile = new File(botFiles + "/membercount.txt");
        if (!memberCountFile.exists()) {
            memberCountFile.createNewFile();
        }
        Logger.info("Bot files setup");
        youtube = new YouTubeImpl(discordAPI);
        twitch = new TwitchImpl(discordAPI);
        reactionRoleManager = new ReactionRoleManager();
        discordAPI.addEventListener(reactionRoleManager, new SlashCommandManager(commandDataList));
        Logger.info("Discord event listeners added");
        return botFiles.exists();
    }

    private static long computeNextDelay(int targetHour, int targetMin, int targetSec) {
        LocalDateTime localNow = LocalDateTime.now();
        ZoneId currentZone = ZoneId.systemDefault();
        ZonedDateTime zonedNow = ZonedDateTime.of(localNow, currentZone);
        ZonedDateTime zonedNextTarget = zonedNow.withHour(targetHour).withMinute(targetMin)
                .withSecond(targetSec);
        if (zonedNow.compareTo(zonedNextTarget) > 0) {
            zonedNextTarget = zonedNextTarget.plusDays(1);
        }

        return Duration.between(zonedNow, zonedNextTarget).getSeconds();
    }

    private static boolean commandListsMatch(List<Command> existing, List<CommandData> updated) {
        if (existing.size() != updated.size())
            return false;

        existing.sort(Comparator.comparing(Command::getName));
        updated.sort(Comparator.comparing(CommandData::getName));

        for (int i = 0; i < existing.size(); i++) {
            Command ex = existing.get(i);
            CommandData up = updated.get(i);

            if (!ex.getName().equals(up.getName())) return false;
            if (!ex.getDescription().equals(up.toData().get("description").toString())) return false;

            List<Option> exOpts = ex.getOptions();
            List<OptionData> upOpts = ((SlashCommandData) up).getOptions();

            if (exOpts.size() != upOpts.size()) return false;

            for (int j = 0; j < exOpts.size(); j++) {
                Option exOpt = exOpts.get(j);
                OptionData upOpt = upOpts.get(j);

                if (!exOpt.getName().equals(upOpt.getName())) return false;
                if (!exOpt.getDescription().equals(upOpt.getDescription())) return false;
                if (!exOpt.getType().equals(upOpt.getType())) return false;
                if (exOpt.isRequired() != upOpt.isRequired()) return false;
            }
        }

        return true;
    }

    // TODO:first redeem counter (twitch channel point api), twitch quote system, ticket system
    public static void main(String[] args) {
        Logger.info("Starting Discord API...");
        try {
            List<CommandData> commandDataList = new ArrayList<>();
            if (init(commandDataList)) {
                discordAPI.awaitReady();
                List<Command> commands = discordAPI.getGuilds().getFirst().retrieveCommands().complete();
                CommandListUpdateAction updateAction = discordAPI.getGuilds().getFirst().updateCommands();
                if (!commandListsMatch(commands, commandDataList)) {
                    updateAction.addCommands(commandDataList);
                    updateAction.queue();
                }
                Logger.info("Discord API ready");
            } else {
                Logger.error("Unexpected folder error aborting startup");
                return;
            }
            scheduleUpdateMemberCount();
        } catch (InvalidTokenException e) {
            Logger.error("Invalid bot token!");
            return;
        } catch (Exception e) {
            Logger.error(e);
            return;
        }
        scheduler.schedule(() -> {
            try {
                twitch.cleanUp();
                youtube.cleanUp();
                reactionRoleManager.cleanUp();
                discordAPI.shutdown();
                ProcessBuilder restartBuilder = new ProcessBuilder("bash", "-c", "sleep 10 && "
                        + "nohup java -jar " + botFile.getName() + " > nohup.out 2>&1");
                restartBuilder.directory(botFile.getParentFile());
                Files.delete(Path.of(botFile.getParentFile() + "/nohup.out"));
                restartBuilder.start();
                System.exit(0);
            } catch (IOException | InterruptedException e) {
                Logger.error(e);
            }

        }, computeNextDelay(4, 0, 0), TimeUnit.SECONDS);
    }
}
