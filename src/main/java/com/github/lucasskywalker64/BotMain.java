package com.github.lucasskywalker64;

import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.commands.RootRegistry;
import com.github.lucasskywalker64.commands.general.GeneralHelp;
import com.github.lucasskywalker64.commands.general.GeneralMemberCountAdd;
import com.github.lucasskywalker64.commands.general.GeneralMemberCountRemove;
import com.github.lucasskywalker64.commands.general.GeneralPing;
import com.github.lucasskywalker64.commands.message.MessageCreate;
import com.github.lucasskywalker64.commands.message.MessageEdit;
import com.github.lucasskywalker64.commands.message.MessageRemove;
import com.github.lucasskywalker64.commands.notif.twitch.NotifTwitchAdd;
import com.github.lucasskywalker64.commands.notif.twitch.NotifTwitchDisplay;
import com.github.lucasskywalker64.commands.notif.twitch.NotifTwitchEdit;
import com.github.lucasskywalker64.commands.notif.twitch.NotifTwitchRemove;
import com.github.lucasskywalker64.commands.notif.youtube.NotifYouTubeAdd;
import com.github.lucasskywalker64.commands.notif.youtube.NotifYouTubeDisplay;
import com.github.lucasskywalker64.commands.notif.youtube.NotifYouTubeEdit;
import com.github.lucasskywalker64.commands.notif.youtube.NotifYouTubeRemove;
import com.github.lucasskywalker64.commands.reaction.ReactionRoleAdd;
import com.github.lucasskywalker64.commands.reaction.ReactionRoleDisplay;
import com.github.lucasskywalker64.commands.reaction.ReactionRoleRemove;
import com.github.lucasskywalker64.commands.shoutout.ShoutoutAdd;
import com.github.lucasskywalker64.commands.shoutout.ShoutoutDisplay;
import com.github.lucasskywalker64.commands.shoutout.ShoutoutRemove;
import com.github.lucasskywalker64.commands.shoutout.ShoutoutRemoveAll;
import com.github.lucasskywalker64.listener.role.ReactionRoleListener;
import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.repository.SettingsRepository;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.listener.command.SlashCommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.Command.Option;
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand;
import net.dv8tion.jda.api.interactions.commands.Command.SubcommandGroup;
import net.dv8tion.jda.api.interactions.commands.build.*;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.jetbrains.annotations.NotNull;
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
    private static JDA discordAPI;
    private static TwitchImpl twitch;
    private static YouTubeImpl youtube;
    private static String channel;
    private static ReactionRoleListener reactionRoleListener;
    private static File dbFile;
    private static Database db;
    private static Connection conn;

    public static Dotenv getConfig() {
        return CONFIG;
    }

    public static TwitchImpl getTwitch() {
        return twitch;
    }

    public static YouTubeImpl getYouTube() {
        return youtube;
    }

    public static ReactionRoleListener getReactionRoleManager() {
        return reactionRoleListener;
    }

    public static File getDbFile() {
        return dbFile;
    }

    public static Connection getConnection() {
        return conn;
    }

    public static void scheduleUpdateMemberCount() throws IOException {
        channel = SettingsRepository.getInstance().get("member_count_channel");
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
                .getCodeSource().getLocation().toURI()).getParentFile().getPath(), "bot_files");
        if (!botFiles.exists()) {
            botFiles.mkdir();
        }
        dbFile = new File(botFiles, "bot.db");
        if (!dbFile.exists()) {
            dbFile.createNewFile();
        }
        Logger.info("Bot files setup");

        db = new Database();
        conn = db.getConnection();
        youtube = new YouTubeImpl(discordAPI);
        twitch = new TwitchImpl(discordAPI);
        reactionRoleListener = new ReactionRoleListener();
        RootRegistry registry = newRegistry();
        commandDataList.addAll(registry.definitions());
        discordAPI.addEventListener(reactionRoleListener, new SlashCommandListener(registry));
        Logger.info("Discord event listeners added");
        return botFiles.exists();
    }

    private static @NotNull RootRegistry newRegistry() {
        var modules = List.of(
                new ReactionRoleAdd(reactionRoleListener),
                new ReactionRoleRemove(reactionRoleListener),
                new ReactionRoleDisplay(),

                new NotifTwitchAdd(twitch),
                new NotifTwitchEdit(twitch),
                new NotifTwitchRemove(twitch),
                new NotifTwitchDisplay(),

                new NotifYouTubeAdd(youtube),
                new NotifYouTubeEdit(),
                new NotifYouTubeRemove(),
                new NotifYouTubeDisplay(),

                new MessageCreate(reactionRoleListener),
                new MessageEdit(),
                new MessageRemove(reactionRoleListener),

                new ShoutoutAdd(twitch),
                new ShoutoutRemove(twitch),
                new ShoutoutDisplay(),
                new ShoutoutRemoveAll(twitch),

                new GeneralMemberCountAdd(),
                new GeneralMemberCountRemove(),
                new GeneralPing(),
                new GeneralHelp()
        );
        return new RootRegistry(modules);
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
        if (existing.size() != updated.size()) return false;

        existing.sort(Comparator.comparing(Command::getName));
        updated.sort(Comparator.comparing(CommandData::getName));

        for (int i = 0; i < existing.size(); i++) {
            Command exRoot = existing.get(i);
            CommandData upRootData = updated.get(i);
            if (!(upRootData instanceof SlashCommandData upRoot)) return false; // We only use slash commands

            if (!Objects.equals(exRoot.getName(), upRoot.getName())) return false;
            if (!Objects.equals(exRoot.getDescription(), upRoot.getDescription())) return false;

            // Compare ungrouped subcommands
            List<Subcommand> exUngrouped = new ArrayList<>(exRoot.getSubcommands());
            List<SubcommandData> upUngrouped = new ArrayList<>(upRoot.getSubcommands());
            if (exUngrouped.size() != upUngrouped.size()) return false;
            exUngrouped.sort(Comparator.comparing(Subcommand::getName));
            upUngrouped.sort(Comparator.comparing(SubcommandData::getName));
            for (int s = 0; s < exUngrouped.size(); s++) {
                if (!subcommandMatches(exUngrouped.get(s), upUngrouped.get(s))) return false;
            }

            // Compare subcommand groups
            List<SubcommandGroup> exGroups = new ArrayList<>(exRoot.getSubcommandGroups());
            List<SubcommandGroupData> upGroups = new ArrayList<>(upRoot.getSubcommandGroups());
            if (exGroups.size() != upGroups.size()) return false;
            exGroups.sort(Comparator.comparing(SubcommandGroup::getName));
            upGroups.sort(Comparator.comparing(SubcommandGroupData::getName));

            for (int g = 0; g < exGroups.size(); g++) {
                SubcommandGroup exG = exGroups.get(g);
                SubcommandGroupData upG = upGroups.get(g);

                if (!Objects.equals(exG.getName(), upG.getName())) return false;
                if (!Objects.equals(exG.getDescription(), upG.getDescription())) return false;

                List<Subcommand> exSubs = new ArrayList<>(exG.getSubcommands());
                List<SubcommandData> upSubs = new ArrayList<>(upG.getSubcommands());
                if (exSubs.size() != upSubs.size()) return false;
                exSubs.sort(Comparator.comparing(Subcommand::getName));
                upSubs.sort(Comparator.comparing(SubcommandData::getName));

                for (int s = 0; s < exSubs.size(); s++) {
                    if (!subcommandMatches(exSubs.get(s), upSubs.get(s))) return false;
                }
            }

            // Root-level options (rare when using subcommands, but keep parity if any are present)
            List<Option> exRootOpts = new ArrayList<>(exRoot.getOptions());
            List<OptionData> upRootOpts = new ArrayList<>(upRoot.getOptions());
            if (exRootOpts.size() != upRootOpts.size()) return false;
            exRootOpts.sort(Comparator.comparing(Option::getName));
            upRootOpts.sort(Comparator.comparing(OptionData::getName));
            for (int o = 0; o < exRootOpts.size(); o++) {
                if (!optionMatches(exRootOpts.get(o), upRootOpts.get(o))) return false;
            }
        }
        return true;
    }

    private static boolean subcommandMatches(Subcommand ex, SubcommandData up) {
        if (!Objects.equals(ex.getName(), up.getName())) return false;
        if (!Objects.equals(ex.getDescription(), up.getDescription())) return false;

        // Options inside subcommand
        List<Option> exOpts = new ArrayList<>(ex.getOptions());
        List<OptionData> upOpts = new ArrayList<>(up.getOptions());
        if (exOpts.size() != upOpts.size()) return false;
        exOpts.sort(Comparator.comparing(Option::getName));
        upOpts.sort(Comparator.comparing(OptionData::getName));

        for (int i = 0; i < exOpts.size(); i++) {
            if (!optionMatches(exOpts.get(i), upOpts.get(i))) return false;
        }
        return true;
    }

    private static boolean optionMatches(Option ex, OptionData up) {
        if (!Objects.equals(ex.getName(), up.getName())) return false;
        if (!Objects.equals(ex.getDescription(), up.getDescription())) return false;
        if (!Objects.equals(ex.getType(), up.getType())) return false;
        if (ex.isRequired() != up.isRequired()) return false;

        // Autocomplete
        if (ex.isAutoComplete() != up.isAutoComplete()) return false;

        // Channel types (order-independent)
        var exCh = ex.getChannelTypes();
        var upCh = up.getChannelTypes();
        if (!exCh.isEmpty() || !upCh.isEmpty()) {
            var exSet = new java.util.HashSet<>(exCh);
            var upSet = new java.util.HashSet<>(upCh);
            if (!exSet.equals(upSet)) return false;
        }

        // Choices (order-independent by name+value)
        List<Choice> exChoices = ex.getChoices();
        List<Choice> upChoices = up.getChoices();
        if (!exChoices.isEmpty() || !upChoices.isEmpty()) {
            if (exChoices.size() != upChoices.size()) return false;
            var exSet = exChoices.stream()
                    .map(c -> c.getName() + "::" + c.getAsString())
                    .collect(java.util.stream.Collectors.toSet());
            var upSet = upChoices.stream()
                    .map(c -> c.getName() + "::" + c.getAsString())
                    .collect(java.util.stream.Collectors.toSet());
            if (!exSet.equals(upSet)) return false;
        }

        // Numeric min/max (if present)
        if (ex.getMinValue() != null || up.getMinValue() != null) {
            if (!Objects.equals(ex.getMinValue(), up.getMinValue())) return false;
        }
        if (ex.getMaxValue() != null || up.getMaxValue() != null) {
            if (!Objects.equals(ex.getMaxValue(), up.getMaxValue())) return false;
        }

        // String length min/max (if present in your JDA version)
        if (ex.getMinLength() != null || up.getMinLength() != null) {
            if (!Objects.equals(ex.getMinLength(), up.getMinLength())) return false;
        }
        if (ex.getMaxLength() != null || up.getMaxLength() != null) {
            if (!Objects.equals(ex.getMaxLength(), up.getMaxLength())) return false;
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
                twitch.shutdown();
                youtube.shutdown();
                reactionRoleListener.shutdown();
                discordAPI.shutdown();
                db.shutdown();
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
