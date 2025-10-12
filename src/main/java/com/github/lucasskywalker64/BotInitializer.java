package com.github.lucasskywalker64;

import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService;
import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.buttons.ButtonRegistry;
import com.github.lucasskywalker64.commands.CommandRegistry;
import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.general.GeneralHelp;
import com.github.lucasskywalker64.commands.general.GeneralMemberCountAdd;
import com.github.lucasskywalker64.commands.general.GeneralMemberCountRemove;
import com.github.lucasskywalker64.commands.general.GeneralPing;
import com.github.lucasskywalker64.commands.message.MessageCreate;
import com.github.lucasskywalker64.commands.message.MessageEdit;
import com.github.lucasskywalker64.commands.message.MessageRemove;
import com.github.lucasskywalker64.commands.twitch.*;
import com.github.lucasskywalker64.commands.youtube.YouTubeAdd;
import com.github.lucasskywalker64.commands.youtube.YouTubeDisplay;
import com.github.lucasskywalker64.commands.youtube.YouTubeEdit;
import com.github.lucasskywalker64.commands.youtube.YouTubeRemove;
import com.github.lucasskywalker64.commands.reaction.ReactionRoleAdd;
import com.github.lucasskywalker64.commands.reaction.ReactionRoleDisplay;
import com.github.lucasskywalker64.commands.reaction.ReactionRoleRemove;
import com.github.lucasskywalker64.commands.shoutout.ShoutoutAdd;
import com.github.lucasskywalker64.commands.shoutout.ShoutoutDisplay;
import com.github.lucasskywalker64.commands.shoutout.ShoutoutRemove;
import com.github.lucasskywalker64.commands.shoutout.ShoutoutRemoveAll;
import com.github.lucasskywalker64.listener.button.ButtonListener;
import com.github.lucasskywalker64.listener.command.SlashCommandListener;
import com.github.lucasskywalker64.listener.message.MessageListener;
import com.github.lucasskywalker64.listener.modal.ModalListener;
import com.github.lucasskywalker64.listener.role.ReactionRoleListener;
import com.github.lucasskywalker64.modals.ModalModule;
import com.github.lucasskywalker64.modals.ModalRegistry;
import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import com.github.lucasskywalker64.ticket.TicketModule;
import com.github.lucasskywalker64.ticket.interaction.buttons.*;
import com.github.lucasskywalker64.ticket.interaction.commands.TicketCreate;
import com.github.lucasskywalker64.ticket.interaction.commands.TicketList;
import com.github.lucasskywalker64.ticket.interaction.commands.admin.TicketAdminPanel;
import com.github.lucasskywalker64.ticket.interaction.commands.admin.TicketAdminSetTranscriptVersion;
import com.github.lucasskywalker64.ticket.interaction.commands.admin.TicketAdminSetup;
import com.github.lucasskywalker64.ticket.interaction.commands.admin.TicketAdminUpdate;
import com.github.lucasskywalker64.ticket.interaction.commands.mod.TicketModAddMembers;
import com.github.lucasskywalker64.ticket.interaction.commands.mod.TicketModRemoveMember;
import com.github.lucasskywalker64.ticket.interaction.modals.TicketCloseModal;
import com.github.lucasskywalker64.web.WebServer;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
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
import java.util.concurrent.*;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class BotInitializer {

    private final File botFile;
    private final ScheduledExecutorService scheduler;
    private TwitchOAuthService oAuthService;
    private Dotenv config;
    private JDA jda;
    private YouTubeImpl youTube;
    private ReactionRoleListener reactionRoleListener;
    private TicketModule ticketModule;

    public BotInitializer(File botFile, ScheduledExecutorService scheduler) {
        this.botFile = botFile;
        this.scheduler = scheduler;
    }

    public void start() throws Exception {
        setupFiles();
        jda = JDABuilder.createDefault(config.get("BOT_TOKEN"))
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableCache(CacheFlag.MEMBER_OVERRIDES)
                .build();
        BotMain.setContext(new BotContext(jda, config, botFile, null));

        oAuthService = new TwitchOAuthService();
        CompletableFuture<TwitchImpl> twitchFuture;
        if (TwitchRepository.getInstance().loadToken() != null) {
            ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("twitch-init").factory());

            twitchFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return new TwitchImpl(jda);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor).whenComplete((twitch, err) -> {
                if (err != null) {
                    Logger.error(err);
                }  else
                    BotMain.getContext().setTwitch(twitch);
            });
        } else {
            twitchFuture = null;
        }
        BotMain.getContext().setTwitchFuture(twitchFuture);

        youTube = new YouTubeImpl(jda);
        reactionRoleListener = new ReactionRoleListener();
        ticketModule = new TicketModule();
        ticketModule.init();
        BotMain.getContext().setTicketModule(ticketModule);
        CommandRegistry registry = createCommands();
        jda.addEventListener(reactionRoleListener);
        jda.addEventListener(new SlashCommandListener(registry));
        jda.addEventListener(new ButtonListener(createButtons()));
        jda.addEventListener(new ModalListener(createModals()));
        jda.addEventListener(new MessageListener());
        jda.awaitReady();
        List<Command> existingCommands = jda.getGuilds().getFirst().retrieveCommands().complete();
        if (!CommandUtil.commandListsMatch(existingCommands, registry.definitions())) {
            jda.getGuilds().getFirst().updateCommands().addCommands(registry.definitions()).queue();
        }
        WebServer webServer = new WebServer();
        webServer.start();
        Logger.info("Discord API ready");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (twitchFuture != null) {
                    TwitchImpl twitch = twitchFuture.getNow(null);
                    if (twitch != null)
                        twitch.shutdown();
                    else twitchFuture.cancel(true);
                } else {
                    TwitchImpl twitch = BotMain.getContext().twitch();
                    if (twitch != null)
                        twitch.shutdown();
                }
                youTube.shutdown();
                reactionRoleListener.shutdown();
                jda.shutdown();
                jda.awaitShutdown(3, TimeUnit.SECONDS);
                Database.getInstance().shutdown();
                Files.deleteIfExists(Path.of(botFile.getParentFile() + "/nohup.out"));
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }));
        scheduler.schedule(() -> {
            try {
                ProcessBuilder restartBuilder = new ProcessBuilder("bash", "-c", "sleep 10 && "
                        + "nohup java -jar " + botFile.getName() + " > nohup.out 2>&1");
                restartBuilder.directory(botFile.getParentFile());
                restartBuilder.start();
                System.exit(0);
            } catch (IOException e) {
                Logger.error(e);
            }
        }, computeNextDelay(4, 0, 0), TimeUnit.SECONDS);
    }

    private @NotNull CommandRegistry createCommands() {
        var modules = List.of(
                new TicketAdminSetup(),
                new TicketAdminPanel(),
                new TicketAdminUpdate(),
                new TicketAdminSetTranscriptVersion(),
                new TicketModAddMembers(),
                new TicketModRemoveMember(),
                new TicketCreate(),
                new TicketList(),

                new ReactionRoleAdd(reactionRoleListener),
                new ReactionRoleRemove(reactionRoleListener),
                new ReactionRoleDisplay(),

                new TwitchAdd(),
                new TwitchEdit(),
                new TwitchRemove(),
                new TwitchDisplay(),
                new TwitchAuth(oAuthService),
                new TwitchRevoke(oAuthService),

                new YouTubeAdd(youTube),
                new YouTubeEdit(),
                new YouTubeRemove(),
                new YouTubeDisplay(),

                new MessageCreate(reactionRoleListener),
                new MessageEdit(),
                new MessageRemove(reactionRoleListener),

                new ShoutoutAdd(),
                new ShoutoutRemove(),
                new ShoutoutDisplay(),
                new ShoutoutRemoveAll(),

                new GeneralMemberCountAdd(),
                new GeneralMemberCountRemove(),
                new GeneralPing(),
                new GeneralHelp()
        );
        return new CommandRegistry(modules);
    }

    private @NotNull ButtonRegistry createButtons() {
        var modules = List.of(
                new TicketCreateButton(ticketModule.getService()),
                new TicketCloseButton(),
                new TicketCloseConfirmButton(ticketModule.getService()),
                new TicketCloseReasonButton(),
                new TicketClaimButton(ticketModule.getService())
        );
        return new ButtonRegistry(modules);
    }

    private @NotNull ModalRegistry createModals() {
        List<ModalModule> modules = List.of(
                new TicketCloseModal(ticketModule.getService())
        );
        return new ModalRegistry(modules);
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
