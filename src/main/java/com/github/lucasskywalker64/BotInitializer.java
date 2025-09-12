package com.github.lucasskywalker64;

import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.commands.CommandUtil;
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
import com.github.lucasskywalker64.listener.command.SlashCommandListener;
import com.github.lucasskywalker64.listener.role.ReactionRoleListener;
import com.github.lucasskywalker64.persistence.Database;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class BotInitializer {

    private final File botFile;
    private final ScheduledExecutorService scheduler;
    private Dotenv config;
    private JDA jda;
    private YouTubeImpl youTube;
    private TwitchImpl twitch;
    private ReactionRoleListener reactionRoleListener;

    public BotInitializer(File botFile, ScheduledExecutorService scheduler) {
        this.botFile = botFile;
        this.scheduler = scheduler;
    }

    public void start() throws Exception {
        setupFiles();
        jda = JDABuilder.createDefault(config.get("BOT_TOKEN"))
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .build();
        BotMain.setContext(new BotContext(jda, config, botFile));
        youTube = new YouTubeImpl(jda);
        twitch = new  TwitchImpl(jda);
        reactionRoleListener = new ReactionRoleListener();
        RootRegistry registry = newRegistry();
        jda.addEventListener(reactionRoleListener);
        jda.addEventListener(new SlashCommandListener(registry));
        jda.awaitReady();
        List<Command> existingCommands = jda.getGuilds().getFirst().retrieveCommands().complete();
        if (!CommandUtil.commandListsMatch(existingCommands, registry.definitions())) {
            jda.getGuilds().getFirst().updateCommands().addCommands(registry.definitions()).queue();
        }
        Logger.info("Discord API ready");
        scheduler.schedule(() -> {
            try {
                twitch.shutdown();
                youTube.shutdown();
                reactionRoleListener.shutdown();
                jda.shutdown();
                Database.getInstance().shutdown();
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

    private @NotNull RootRegistry newRegistry() {
        var modules = List.of(
                new ReactionRoleAdd(reactionRoleListener),
                new ReactionRoleRemove(reactionRoleListener),
                new ReactionRoleDisplay(),

                new NotifTwitchAdd(twitch),
                new NotifTwitchEdit(twitch),
                new NotifTwitchRemove(twitch),
                new NotifTwitchDisplay(),

                new NotifYouTubeAdd(youTube),
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

    private void setupFiles() throws IOException {
        File botFiles = new File(botFile.getParentFile(), "bot_files");
        if (!botFiles.exists()) {
            botFiles.mkdirs();
        }
        config = Dotenv.configure().directory(botFiles.getAbsolutePath()).load();
        File dbFile = new File(botFiles.getAbsolutePath(), "bot.db");
        if (!dbFile.exists()) {
            dbFile.createNewFile();
        }
        Logger.info("Bot files setup");
    }

    private long computeNextDelay(int targetHour, int targetMin, int targetSec) {
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
}
