package com.github.lucasskywalker64;

import com.github.lucasskywalker64.apis.YoutubeImpl;
import com.github.lucasskywalker64.listeners.ReactionRoleManager;
import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import com.github.lucasskywalker64.apis.TwitchImpl;
import com.github.lucasskywalker64.listeners.SlashCommandManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.tinylog.Logger;

public class BotMain {

  private static final File botFile = new File(BotMain.class.getProtectionDomain()
      .getCodeSource()
      .getLocation().getPath());
  private static final Dotenv CONFIG =
      Dotenv.configure().directory(botFile.getParentFile().getAbsolutePath()).load();
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
  private static File reactionRolesFile;
  private static File youtubeFile;
  private static File twitchFile;
  private static File shoutoutFile;
  private static File moderatorFile;
  private static File memberCountFile;
  private static JDA discordAPI;
  private static TwitchImpl twitch;
  private static YoutubeImpl youtube;
  private static String channel;
  private static ReactionRoleManager reactionRoleManager;

  public static Dotenv getConfig() {
    return CONFIG;
  }

  public static File getReactionRolesFile() {
    return reactionRolesFile;
  }

  public static File getYoutubeFile() {
    return youtubeFile;
  }

  public static File getTwitchFile() {
    return twitchFile;
  }

  public static File getShoutoutFile() {
    return shoutoutFile;
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

  public static ReactionRoleManager getReactionRoleManager() {
    return reactionRoleManager;
  }


  public static void scheduleUpdateMemberCount() {
    try (FileReader fileReader = new FileReader(memberCountFile);
         BufferedReader bufferedReader = new BufferedReader(fileReader)) {
      channel = bufferedReader.readLine();
    } catch (IOException e) {
      Logger.error(e);
      return;
    }
    scheduler.scheduleAtFixedRate(BotMain::updateMemberCount, 0, 1, TimeUnit.HOURS);
    Logger.info("Set up member count scheduler");
  }

  private static void updateMemberCount() {
    discordAPI.getVoiceChannelById(channel).getManager().setName("Member count: " +
        discordAPI.getGuilds().get(0).getMemberCount()).queue();
  }

  private static boolean init() throws InvalidTokenException, URISyntaxException, IOException {
    discordAPI = JDABuilder.createDefault(CONFIG.get("BOT_TOKEN"))
        .enableIntents(GatewayIntent.GUILD_EMOJIS_AND_STICKERS, GatewayIntent.GUILD_MEMBERS)
        .build();
    Logger.info("Discord API builder executed");
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
    youtubeFile = new File(botFiles + "/youtube.csv");
    if (!youtubeFile.exists()) {
      youtubeFile.createNewFile();
    }
    twitchFile = new File(botFiles + "/twitch.csv");
    if (!twitchFile.exists()) {
      twitchFile.createNewFile();
    }
    shoutoutFile = new File(botFiles + "/shoutout.csv");
    if (!shoutoutFile.exists()) {
      shoutoutFile.createNewFile();
    }
    moderatorFile = new File(botFiles + "/moderator.csv");
    if (!moderatorFile.exists()) {
      moderatorFile.createNewFile();
    }
    memberCountFile = new File(botFiles + "/membercount.txt");
    if (!memberCountFile.exists()) {
      memberCountFile.createNewFile();
    }
    Logger.info("File path to bot files setup");
    youtube = new YoutubeImpl(discordAPI);
    twitch = new TwitchImpl(discordAPI);
    reactionRoleManager = new ReactionRoleManager();
    discordAPI.addEventListener(reactionRoleManager, new SlashCommandManager());
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

  // TODO:first redeem counter (twitch channel point api), twitch quote system, change update stream announcement to work on all streams
  public static void main(String[] args) throws InterruptedException {
    Logger.info("Starting Discord API...");
    try {
      if (init()) {
        discordAPI.awaitReady();
        Logger.info("Discord API ready");
      } else {
        Logger.error("Unexpected folder error aborting startup");
        return;
      }
      scheduleUpdateMemberCount();
    } catch (InvalidTokenException e) {
      Logger.error("Invalid bot token!");
    } catch (URISyntaxException | IOException e) {
      Logger.error(e);
    }
    scheduler.schedule(() -> {
      twitch.cleanUp();
      youtube.cleanUp();
      reactionRoleManager.cleanUp();
      discordAPI.shutdown();
      ProcessBuilder restartBuilder = new ProcessBuilder("bash", "-c", "sleep 10 && "
          + "nohup java -jar " + botFile.getName() + " > nohup.out 2>&1");
      restartBuilder.directory(botFile.getParentFile());
      try {
        Files.delete(Path.of(botFile.getParentFile() + "/nohup.out"));
        restartBuilder.start();
        System.exit(0);
      } catch (IOException e) {
        Logger.info(e);
      }

    }, computeNextDelay(4, 0, 0), TimeUnit.SECONDS);
  }
}
