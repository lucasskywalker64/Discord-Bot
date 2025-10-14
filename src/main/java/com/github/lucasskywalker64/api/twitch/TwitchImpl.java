package com.github.lucasskywalker64.api.twitch;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService;
import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService.TokenBundle;
import com.github.lucasskywalker64.persistence.data.ShoutoutData;
import com.github.lucasskywalker64.persistence.data.TokenData;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
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

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.tinylog.Logger;

@SuppressWarnings({"java:S1192", "DataFlowIssue"})
public class TwitchImpl {

    private static final String HTTPS_TWITCH_TV = "https://twitch.tv/";
    private static final TwitchRepository twitchRepo = TwitchRepository.getInstance();
    private static final Dotenv config = BotMain.getContext().config();
    private final TwitchOAuthService oAuthService;
    private final List<TwitchData> twitchDataList = new ArrayList<>();
    private final List<ShoutoutData> shoutoutNames = new ArrayList<>();
    private final List<String> shoutedoutNames = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final JDA discordAPI;
    private TwitchClient twitchClient;
    private Long shoutoutTimestamp = 0L;
    private String streamerId;
    private String moderatorId;
    private TokenData tokenData;

    public void load() {
        twitchDataList.clear();
        twitchDataList.addAll(twitchRepo.loadAll());
        shoutoutNames.clear();
        shoutoutNames.addAll(twitchRepo.loadAllShoutout());
        shoutedoutNames.clear();
        shoutedoutNames.addAll(twitchRepo.loadAllShoutedOutNames());

        if (!twitchDataList.isEmpty()) {
            twitchDataList.forEach(data -> {
                twitchClient.getClientHelper().disableStreamEventListener(data.username());
                twitchClient.getClientHelper().enableStreamEventListener(data.username());
            });
            Logger.info("Stream listener updated");
        }
    }

    public void scheduleLoad() {
        scheduler.scheduleAtFixedRate(this::load, 1, 1, TimeUnit.DAYS);
    }

    private synchronized String getValidAccessToken() throws Exception {
        Instant now = Instant.now();
        if (tokenData.bundle().expiresAt().isBefore(now.plusSeconds(60))) {
            Logger.info("Access token expired, refreshing...");
            TokenBundle refreshed = oAuthService.refreshToken(tokenData.bundle().refreshToken());
            tokenData = tokenData.withTokenBundle(refreshed);
            twitchRepo.saveToken(tokenData);
        }

        return tokenData.bundle().accessToken();
    }

    private void handleChannelGoLiveEvent(ChannelGoLiveEvent event) {
        Logger.info("Caught Live Event from: {}", event.getChannel().getName());
        int index = IntStream.range(0, twitchDataList.size())
                .filter(i -> twitchDataList.get(i).username().equalsIgnoreCase(event.getChannel().getName()))
                .findFirst()
                .orElse(-1);
        if (index > -1 && twitchDataList.get(index).timestamp() + TimeUnit.HOURS.toMillis(3)
                < System.currentTimeMillis()) {
            postStreamAnnouncement(event, index);
            Game lastPlayed = twitchClient.getHelix().getGames(null,
                            Collections.singletonList(event.getStream().getGameId()), null, null)
                    .execute().getGames().getFirst();
            twitchDataList.set(index, twitchDataList.get(index).withLastPlayed(lastPlayed.getName(),
                    lastPlayed.getBoxArtUrl(600, 800)));
            if (index == 0) {
                shoutedoutNames.clear();
                twitchRepo.clearShoutedOutNames();
            }
        }
    }

    private void postStreamAnnouncement(ChannelGoLiveEvent event, int index) {
        Logger.info("Posting stream announcement...");
        twitchDataList.set(index, twitchDataList.get(index).withTimestamp(System.currentTimeMillis()));
        try {
            MessageChannel textChannel = discordAPI.getChannelById(MessageChannel.class,
                            twitchDataList.get(index).channel());

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.addField("Game", event.getStream().getGameName(), false);
            embedBuilder.setAuthor(event.getChannel().getName(), HTTPS_TWITCH_TV
                    + event.getChannel().getName(), twitchClient.getHelix().getUsers(null,
                            Collections.singletonList(event.getChannel().getId()),
                            Collections.singletonList(event.getChannel().getName())).execute().getUsers().getFirst()
                    .getProfileImageUrl());
            embedBuilder.setTitle(event.getStream().getTitle());
            embedBuilder.setImage(event.getStream().getThumbnailUrl(852, 480)
                    + "?t=" + CryptoUtils.generateNonce(4));
            embedBuilder.setThumbnail(twitchClient.getHelix().getGames(null,
                            Collections.singletonList(event.getStream().getGameId()), null, null).execute()
                    .getGames().getFirst().getBoxArtUrl(600, 800));
            embedBuilder.setFooter(discordAPI.getSelfUser().getName());
            embedBuilder.setTimestamp(Instant.now());
            Logger.info("Embed builder set up");

            String message = twitchDataList.get(index).message();
            String messagePart2 =
                    message.substring(message.indexOf("\\n") + 2).strip();
            String tempMessage =
                    message.substring(0, message.lastIndexOf("\\n") + 2)
                            .replace("\\n", "\n")
                            + messagePart2;
            Logger.info("Message set up");

            String messageId;
            if (twitchDataList.get(index).roleId() != null) {
                messageId = textChannel.sendMessage(discordAPI.getRoleById(
                        twitchDataList.get(index).roleId()).getAsMention() + " " + tempMessage)
                                .addEmbeds(embedBuilder.build())
                                .addActionRow(Button.link(HTTPS_TWITCH_TV + twitchDataList.get(index).username(),
                                        "Watch Stream"))
                                .complete().getId();
            } else {
                messageId = textChannel.sendMessage(tempMessage).addEmbeds(embedBuilder.build())
                        .addActionRow(Button.link(HTTPS_TWITCH_TV + twitchDataList.get(index).username(), "Watch Stream"))
                        .complete().getId();
            }

            twitchDataList.set(index, twitchDataList.get(index).withAnnouncementId(messageId));

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
            int index = IntStream.range(0, twitchDataList.size())
                    .filter(i -> twitchDataList.get(i).username().equalsIgnoreCase(event.getChannel().getName()))
                    .findFirst()
                    .orElse(-1);

            if (index > -1 && twitchDataList.get(index).announcementId() != null) {
                Video lastVod = twitchClient.getHelix().getVideos(getValidAccessToken(),
                        (List<String>) null, event.getChannel().getId(), null, null,
                        null, null, null, null, null, null)
                        .execute().getVideos().getFirst();

                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setAuthor(event.getChannel().getName(), HTTPS_TWITCH_TV
                        + event.getChannel().getName(), twitchClient.getHelix()
                        .getUsers(null, Collections.singletonList(event.getChannel().getId()),
                                Collections.singletonList(event.getChannel().getName())).execute().getUsers()
                        .getFirst().getProfileImageUrl());
                embedBuilder.setTitle(lastVod.getTitle());
                embedBuilder.addField("Game", twitchDataList.get(index).gameName(), true);
                embedBuilder.addField("Duration", lastVod.getDuration(), true);
                embedBuilder.setImage(lastVod.getThumbnailUrl(852, 480)
                        + "?t=" + CryptoUtils.generateNonce(4));
                embedBuilder.setThumbnail(twitchDataList.get(index).boxArtUrl());
                embedBuilder.setFooter("Last online");
                embedBuilder.setTimestamp(event.getFiredAtInstant());
                Logger.info("Embed builder set up");

                MessageChannel textChannel = discordAPI.getChannelById(MessageChannel.class,
                        twitchDataList.get(index).channel());
                textChannel.editMessageById(twitchDataList.get(index).announcementId(),
                                event.getChannel().getName() + " was live")
                        .and(textChannel.editMessageEmbedsById(
                                        twitchDataList.get(index).announcementId(),
                                        embedBuilder.build())
                                .setActionRow(Button.link(lastVod.getUrl(), "Watch VOD"))).queue();
                Logger.info("Announcement updated");
            }
            Logger.error("Failed to locate %s in the data list " +
                    "or we didn't yet catch a live event from this channel.", event.getChannel().getName());
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    private void shoutout(ChannelMessageEvent event) {
        if (!event.getUser().getName().equalsIgnoreCase("freudnim")
                && shoutoutNames.stream().anyMatch(data -> data.username()
                .equalsIgnoreCase(event.getUser().getName()))) {
            twitchClient.getChat().sendMessage(twitchDataList.getFirst().username(),
                    "!so " + event.getUser().getName());
            shoutedoutNames.add(event.getUser().getName().toLowerCase());
        } else if (List.of("1kirigiri", "freudnim").contains(event.getUser().getName().toLowerCase())) {
            twitchClient.getChat().sendMessage(twitchDataList.getFirst().username(), "!so freudnim");
            shoutedoutNames.add("1kirigiri");
            shoutedoutNames.add("freudnim");
        }
    }

    private void handleRaidEvent(RaidEvent raidEvent) {
        if (shoutoutTimestamp + 120000L < System.currentTimeMillis()) {
            try {
                twitchClient.getHelix().sendShoutout(
                        getValidAccessToken(),
                        streamerId,
                        raidEvent.getRaider().getId(),
                        moderatorId).queue();
            } catch (Exception e) {
                Logger.error(e);
            }
            shoutoutTimestamp = System.currentTimeMillis();
        } else {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    RaidEvent delayedRaidEvent = new RaidEvent(raidEvent.getMessageEvent(), raidEvent.getChannel(),
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

    private void setup() throws Exception {
        twitchClient = TwitchClientBuilder.builder()
                .withClientId(config.get("TWITCH_CLIENT_ID"))
                .withClientSecret(config.get("TWITCH_CLIENT_SECRET"))
                .withEnableChat(true)
                .withChatAccount(new OAuth2Credential("twitch", getValidAccessToken()))
                .withEnableHelix(true)
                .build();

        load();

        if (!twitchDataList.isEmpty()) {
            streamerId = twitchClient.getHelix().getUsers(null, null,
                            Collections.singletonList(twitchDataList.getFirst().username()))
                    .execute().getUsers().getFirst().getId();
            Logger.info("Streamer ID setup");

            moderatorId = tokenData.userId();

            twitchClient.getChat().joinChannel(twitchDataList.getFirst().username());
        }
    }

    public void shutdown() throws InterruptedException, IOException {
        twitchClient.close();
        twitchRepo.saveAll(twitchDataList, false);
        twitchRepo.saveShoutedOutNames(shoutedoutNames);

        scheduler.shutdown();
        scheduler.awaitTermination(3, TimeUnit.SECONDS);
        Logger.info("Twitch API shutdown");
    }

    public TwitchImpl(JDA discordAPI) throws Exception {
        Logger.info("Starting Twitch API...");

        this.discordAPI = discordAPI;
        this.oAuthService = BotMain.getContext().twitchOauthService();

        this.tokenData = twitchRepo.loadToken();

        setup();

        twitchClient.getEventManager()
                .onEvent(ChannelMessageEvent.class, this::handleChannelMessageEvent);
        if (moderatorId != null && !moderatorId.isEmpty())
            twitchClient.getEventManager().onEvent(RaidEvent.class, this::handleRaidEvent);
        twitchClient.getEventManager()
                .onEvent(ChannelGoLiveEvent.class, this::handleChannelGoLiveEvent);
        twitchClient.getEventManager()
                .onEvent(ChannelGoOfflineEvent.class, this::handleChannelGoOfflineEvent);

        scheduleLoad();
        Logger.info("Twitch API started");
    }
}
