package com.github.lucasskywalker64.apis;

import com.github.lucasskywalker64.BotMain;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.RaidEvent;
import com.github.twitch4j.common.util.CryptoUtils;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.helix.domain.Game;
import com.github.twitch4j.helix.domain.Video;
import com.github.twitch4j.pubsub.events.RewardRedeemedEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.tinylog.Logger;

@SuppressWarnings("java:S1192")
public class TwitchImpl {

  private static final File twitchFile = BotMain.getTwitchFile();
  private static final File shoutoutFile = BotMain.getShoutoutFile();
  private static final File moderatorFile = BotMain.getModeratorFile();
  private final List<String> channel = new ArrayList<>();
  private final List<String> message = new ArrayList<>();
  private final List<String> username = new ArrayList<>();
  private final List<String> role = new ArrayList<>();
  private final List<Long> timestamp = new ArrayList<>();
  private final List<String> shoutoutNames = new ArrayList<>();
  private final List<String> shoutedoutNames = new ArrayList<>();
  private final Map<String, String> lastAnnouncementMessageId = new HashMap<>();
  private TwitchClient twitchClient;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final JDA discordAPI;
  private static final String HTTPS_TWITCH_TV = "https://twitch.tv/";
  private Long shoutoutTimestamp = 0L;
  private Game lastPlayedGame;
  private String streamerId;
  private String moderatorName;
  private String moderatorId;

  public List<String> getShoutoutNames() {
    return shoutoutNames;
  }

  public void updateLists() {
    Logger.info("Setting up the lists...");
    try (FileReader fileReaderTwitch = new FileReader(twitchFile);
         FileReader fileReaderShoutout = new FileReader(shoutoutFile);
         BufferedReader fileReaderModerator = new BufferedReader(new FileReader(moderatorFile))) {
      channel.clear();
      message.clear();
      username.clear();
      role.clear();
      shoutoutNames.clear();

      Logger.info("Old lists cleared");

      CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
          .setDelimiter(";")
          .setHeader("channel", "message", "username", "role")
          .setSkipHeaderRecord(true)
          .build();

      Iterable<CSVRecord> csvIterable = csvFormat.parse(fileReaderTwitch);

      for (CSVRecord csvRecord : csvIterable) {
        channel.add(csvRecord.get("channel"));
        message.add(csvRecord.get("message"));
        username.add(csvRecord.get("username"));
          if (!csvRecord.get("role").contains("@")) {
              role.add(csvRecord.get("role"));
          } else {
              role.add(csvRecord.get("role")
                  .substring(csvRecord.get("role").indexOf("&") + 1, csvRecord.get("role")
                      .lastIndexOf(">")));
          }
      }

        while (timestamp.size() < username.size()) {
            timestamp.add(0L);
        }

      csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
          .setHeader("username")
          .setSkipHeaderRecord(true)
          .build();

      csvIterable = csvFormat.parse(fileReaderShoutout);

      for (CSVRecord csvRecord : csvIterable) {
        shoutoutNames.add(csvRecord.get("username"));
      }

      for (String s : username) {
          if (!lastAnnouncementMessageId.containsKey(s)) {
              lastAnnouncementMessageId.put(s, null);
          }
      }

      moderatorName = fileReaderModerator.readLine();

      Logger.info("Lists updated");

      if (!username.isEmpty()) {
        twitchClient.getClientHelper().disableStreamEventListener(username);
        twitchClient.getClientHelper().enableStreamEventListener(username);
        Logger.info("Stream listener updated");
        if (streamerId == null) {
          streamerId = twitchClient.getHelix().getUsers(null, null,
                  Collections.singletonList(username.get(0)))
              .execute().getUsers().get(0).getId();
          Logger.info("Streamer ID setup");
        }
      }
    } catch (IOException e) {
      Logger.error(e);
    }
  }

  public void scheduleUpdateLists() {
    scheduler.scheduleAtFixedRate(this::updateLists, 1, 1, TimeUnit.DAYS);
  }

  private void handleChannelGoLiveEvent(ChannelGoLiveEvent event) {
    Logger.info("Caught Live Event from: {}", event.getChannel().getName());
    int index = username.indexOf(event.getChannel().getName().toLowerCase());
    if (timestamp.get(index) + 10800000L < System.currentTimeMillis()) {
      postStreamAnnouncement(event, index);
      if (index == 0) {
        shoutedoutNames.clear();
        lastPlayedGame = twitchClient.getHelix().getGames(null,
                Collections.singletonList(event.getStream().getGameId()), null, null)
            .execute().getGames().get(0);
      }
    }
  }

  private void postStreamAnnouncement(ChannelGoLiveEvent event, int index) {
    Logger.info("Posting stream announcement...");
    timestamp.set(index, System.currentTimeMillis());
    try {
      MessageChannel textChannel =
          Objects.requireNonNull(discordAPI.getChannelById(MessageChannel.class,
              channel.get(index)), "Channel must not be null");

      EmbedBuilder embedBuilder = new EmbedBuilder();
      embedBuilder.addField("Game", event.getStream().getGameName(), false);
      embedBuilder.setAuthor(event.getChannel().getName(), HTTPS_TWITCH_TV
          + event.getChannel().getName(), twitchClient.getHelix().getUsers(null,
              Collections.singletonList(event.getChannel().getId()),
              Collections.singletonList(event.getChannel().getName())).execute().getUsers().get(0)
          .getProfileImageUrl());
      embedBuilder.setTitle(event.getStream().getTitle());
      embedBuilder.setImage(event.getStream().getThumbnailUrl(852, 480)
          + "?t=" + CryptoUtils.generateNonce(4));
      embedBuilder.setThumbnail(twitchClient.getHelix().getGames(null,
              Collections.singletonList(event.getStream().getGameId()), null, null).execute()
          .getGames().get(0).getBoxArtUrl(600, 800));
      embedBuilder.setFooter(discordAPI.getSelfUser().getName());
      embedBuilder.setTimestamp(Instant.now());
      Logger.info("Embed builder set up");

      String messagePart2 =
          message.get(index).substring(message.get(index).indexOf("\\n") + 2).strip();
      String tempMessage =
          message.get(index).substring(0, message.get(index).lastIndexOf("\\n") + 2)
              .replace("\\n", "\n")
              + messagePart2;
      Logger.info("Message set up");

      String messageId;
      if (!role.isEmpty() && !role.get(index).isBlank()) {
        Logger.info("Role matches for main stream");
        messageId =
            textChannel.sendMessage(Objects.requireNonNull(discordAPI.getRoleById(role.get(index))
                    , "Role must not be null").getAsMention() + " " + tempMessage)
                .addEmbeds(embedBuilder.build())
                .addActionRow(Button.link(HTTPS_TWITCH_TV + username.get(index), "Watch Stream"))
                .complete().getId();
        Logger.info("Announcement posted for main stream");
      } else {
        Logger.info("Role doesn't match for main stream");
        messageId = textChannel.sendMessage(tempMessage).addEmbeds(embedBuilder.build())
            .addActionRow(Button.link(HTTPS_TWITCH_TV + username.get(index), "Watch Stream"))
            .complete().getId();
        Logger.info("Announcement posted for secondary stream");
      }

      lastAnnouncementMessageId.put(event.getChannel().getName(), messageId);

      Timer timer = new Timer();
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          embedBuilder.setImage(event.getStream().getThumbnailUrl(852, 480) + "?t="
              + CryptoUtils.generateNonce(4));
          textChannel.editMessageEmbedsById(messageId, embedBuilder.build()).queue();
        }
      }, 300000);
    } catch (Exception e) {
      Logger.error(e);
    }

  }

  private void handleChannelGoOfflineEvent(ChannelGoOfflineEvent event) {
    Logger.info("Caught Offline Event from: {}", event.getChannel().getName());
    try {
      Video lastVod = twitchClient.getHelix().getVideos(BotMain.getConfig()
              .get("TWITCH_ACCESS_TOKEN"), (List<String>) null, event.getChannel().getId(),
          null, null, null, null, null, null,
          null, null).execute().getVideos().get(0);

      EmbedBuilder embedBuilder = new EmbedBuilder();
      embedBuilder.setAuthor(event.getChannel().getName(), HTTPS_TWITCH_TV
          + event.getChannel().getName(), twitchClient.getHelix()
          .getUsers(null, Collections.singletonList(event.getChannel().getId()),
              Collections.singletonList(event.getChannel().getName())).execute().getUsers()
          .get(0).getProfileImageUrl());
      embedBuilder.setTitle(lastVod.getTitle());
      embedBuilder.addField("Game", lastPlayedGame.getName(), true);
      embedBuilder.addField("Views", lastVod.getViewCount().toString(), true);
      embedBuilder.addField("Duration", lastVod.getDuration(), true);
      embedBuilder.setImage(lastVod.getThumbnailUrl(852, 480)
          + "?t=" + CryptoUtils.generateNonce(4));
      embedBuilder.setThumbnail(lastPlayedGame.getBoxArtUrl(600, 800));
      embedBuilder.setFooter("Last online");
      embedBuilder.setTimestamp(event.getFiredAtInstant());
      Logger.info("Embed builder set up");

      MessageChannel textChannel = Objects.requireNonNull(discordAPI.getChannelById(
          MessageChannel.class, channel.get(0)), "Channel must not be null");
      textChannel.editMessageById(lastAnnouncementMessageId.get(event.getChannel().getName()),
              event.getChannel().getName() + " was live")
          .and(textChannel.editMessageEmbedsById(
                  lastAnnouncementMessageId.get(event.getChannel().getName()),
                  embedBuilder.build())
              .setActionRow(Button.link(lastVod.getUrl(), "Watch VOD"))).queue();
      Logger.info("Announcement updated");
    } catch (Exception e) {
      Logger.error(e);
    }
  }

  private void shoutout(ChannelMessageEvent event) {
    if (!event.getUser().getName().equalsIgnoreCase("freudnim")
        && shoutoutNames.contains(event.getUser().getName().toLowerCase())) {
      twitchClient.getChat().sendMessage(username.get(0),
          "!so " + event.getUser().getName());
      shoutedoutNames.add(event.getUser().getName().toLowerCase());
    } else if (List.of("1kirigiri", "freudnim").contains(event.getUser().getName())) {
      twitchClient.getChat().sendMessage(username.get(0), "!so freudnim");
      shoutedoutNames.add("1kirigiri");
      shoutedoutNames.add("freudnim");
    }
  }

  private void handleRaidEvent(RaidEvent raidEvent) {
    if (shoutoutTimestamp + 120000L < System.currentTimeMillis()) {
      twitchClient.getHelix().sendShoutout(
          BotMain.getConfig().get("TWITCH_ACCESS_TOKEN"),
          streamerId,
          raidEvent.getRaider().getId(),
          moderatorId).queue();
      shoutoutTimestamp = System.currentTimeMillis();
    } else {
      Timer timer = new Timer();
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          RaidEvent delayedRaidEvent = new RaidEvent(raidEvent.getChannel(),
              raidEvent.getRaider(), raidEvent.getViewers());
          twitchClient.getEventManager().publish(delayedRaidEvent);
        }
      }, (shoutoutTimestamp + 120000L) - System.currentTimeMillis() + 5000);
    }
  }

  private void handleChannelMessageEvent(ChannelMessageEvent event) {
      if (!shoutedoutNames.contains(event.getUser().getName().toLowerCase())) {
          shoutout(event);
      }
  }

  private void handleRewardRedeemEvent(RewardRedeemedEvent event) {
    //TODO
  }

  private void setup() {
    twitchClient = TwitchClientBuilder.builder()
        .withClientId(BotMain.getConfig().get("TWITCH_CLIENT_ID"))
        .withClientSecret(BotMain.getConfig().get("TWITCH_CLIENT_SECRET"))
        .withEnableChat(true)
        .withChatAccount(new OAuth2Credential("twitch",
            BotMain.getConfig().get("TWITCH_ACCESS_TOKEN")))
        .withEnableHelix(true)
        .withEnablePubSub(true)
        .build();

    updateLists();

    if (!username.isEmpty()) {
      streamerId = twitchClient.getHelix().getUsers(null, null,
              Collections.singletonList(username.get(0)))
          .execute().getUsers().get(0).getId();
      Logger.info("Streamer ID setup");

      moderatorId = twitchClient.getHelix().getUsers(null, null,
          List.of(moderatorName)).execute().getUsers().get(0).getId();
      Logger.info("Current moderator ID setup");

      twitchClient.getChat().joinChannel(username.get(0));
      twitchClient.getPubSub().listenForChannelPointsRedemptionEvents(null, streamerId);
    }
  }

  public void cleanUp() {
    twitchClient.close();
    scheduler.shutdownNow();
    channel.clear();
    message.clear();
    username.clear();
    role.clear();
    shoutoutNames.clear();
  }

  public TwitchImpl(JDA discordAPI) {
    Logger.info("Starting Twitch API...");

    this.discordAPI = discordAPI;

    setup();

    twitchClient.getEventManager()
        .onEvent(ChannelMessageEvent.class, this::handleChannelMessageEvent);
    twitchClient.getEventManager().onEvent(RaidEvent.class, this::handleRaidEvent);
    twitchClient.getEventManager()
        .onEvent(ChannelGoLiveEvent.class, this::handleChannelGoLiveEvent);
    twitchClient.getEventManager()
        .onEvent(ChannelGoOfflineEvent.class, this::handleChannelGoOfflineEvent);
    twitchClient.getEventManager()
        .onEvent(RewardRedeemedEvent.class, this::handleRewardRedeemEvent);

    scheduleUpdateLists();
    Logger.info("Twitch API started");
  }
}
