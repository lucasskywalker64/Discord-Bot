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
import com.github.twitch4j.eventsub.events.ChannelPointsCustomRewardRedemptionEvent;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionTypes;
import com.github.twitch4j.helix.domain.*;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.tuple.Pair;
import org.tinylog.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SuppressWarnings({"java:S1192", "DataFlowIssue"})
public class TwitchImpl {

    private static final String HTTPS_TWITCH_TV = "https://twitch.tv/";
    private static final TwitchRepository twitchRepo = TwitchRepository.getInstance();
    private static final Dotenv config = BotMain.getContext().config();
    private final TwitchOAuthService oAuthService;
    private final List<TwitchData> twitchDataList = new ArrayList<>();
    private final List<ShoutoutData> shoutoutNames = new ArrayList<>();
    private final List<String> redemptionIds = new ArrayList<>();
    private final Map<String, Pair<Integer, Integer>> checkinConfigs = new HashMap<>();
    private final List<String> shoutedoutNames = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final JDA discordAPI;
    private TwitchClient twitchClient;
    private Long shoutoutTimestamp = 0L;
    private String broadcasterId;
    private String moderatorId;
    private volatile TokenData streamerTokenData;
    private volatile TokenData chatTokenData;

    public String getBroadcasterId() {
        return broadcasterId;
    }

    public void load() {
        twitchDataList.clear();
        twitchDataList.addAll(twitchRepo.loadAll());
        shoutoutNames.clear();
        shoutoutNames.addAll(twitchRepo.loadAllShoutout());
        shoutedoutNames.clear();
        shoutedoutNames.addAll(twitchRepo.loadAllShoutedOutNames());
        redemptionIds.clear();
        checkinConfigs.clear();

        try {
            redemptionIds.addAll(twitchRepo.loadRedemptions());
            checkinConfigs.putAll(twitchRepo.loadCheckinConfigs());
            streamerTokenData = twitchRepo.loadToken(true);
            broadcasterId = streamerTokenData.userId();
        } catch (SQLException e) {
            Logger.error(e);
        }

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
        scheduler.scheduleAtFixedRate(this::checkExpiredVips, 0, 1, TimeUnit.HOURS);
    }

    public List<CustomReward> fetchRewards() throws Exception {
        return twitchClient.getHelix()
                .getCustomRewards(getValidStreamerAccessToken(), broadcasterId, null, null)
                .execute()
                .getRewards();
    }

    public List<Moderator> fetchModerators() throws Exception {
        List<Moderator> moderators = new ArrayList<>();
        String cursor = null;

        do {
            ModeratorList result = twitchClient.getHelix().getModerators(
                    getValidStreamerAccessToken(),
                    broadcasterId,
                    null,
                    cursor,
                    100
            ).execute();

            moderators.addAll(result.getModerators());

            if (result.getPagination() != null && result.getPagination().getCursor() != null)
                cursor = result.getPagination().getCursor();
            else
                cursor = null;
        } while (cursor != null);

        return moderators;
    }

    public List<ChannelVip> fetchVips() throws Exception {
        List<ChannelVip> vips = new ArrayList<>();
        String cursor = null;

        do {
            ChannelVipList result = twitchClient.getHelix().getChannelVips(
                    getValidStreamerAccessToken(),
                    broadcasterId,
                    null,
                    100,
                    cursor
            ).execute();

            vips.addAll(result.getData());

            if (result.getPagination() != null && result.getPagination().getCursor() != null)
                cursor = result.getPagination().getCursor();
            else
                cursor = null;
        } while (cursor != null);

        return vips;
    }

    private void checkExpiredVips() {
        try {
            long now = System.currentTimeMillis();
            List<Pair<String, String>> expired = twitchRepo.getExpiredVips(now);

            for (Pair<String, String> vip : expired) {
                String userId = vip.getKey();
                String channelId = vip.getValue();

                try {
                    twitchClient.getHelix().removeChannelVip(getValidStreamerAccessToken(), channelId, userId).execute();
                    Logger.info("Removed expired VIP from user: " + userId);

                    twitchRepo.removeActiveVip(userId, channelId);
                } catch (Exception e) {
                    Logger.error("Failed to remove VIP for user " + userId, e);
                }
            }
        } catch (SQLException e) {
            Logger.error("Database error checking expired VIPs", e);
        }
    }

    private String getValidStreamerAccessToken() throws Exception {
        Instant now = Instant.now();
        if (streamerTokenData.bundle().expiresAt().isAfter(now.plusSeconds(60)))
            return streamerTokenData.bundle().accessToken();

        synchronized (this) {
            if (streamerTokenData.bundle().expiresAt().isBefore(now.plusSeconds(60))) {
                Logger.info("Streamer access token expired, refreshing...");
                TokenBundle refreshed = oAuthService.refreshToken(streamerTokenData.bundle().refreshToken());
                streamerTokenData = streamerTokenData.withTokenBundle(refreshed);
                twitchRepo.saveToken(streamerTokenData);
            }
            return streamerTokenData.bundle().accessToken();
        }
    }

    public String getValidChatAccessToken() throws Exception {
        Instant now = Instant.now();
        if (chatTokenData.bundle().expiresAt().isAfter(now.plusSeconds(60)))
            return chatTokenData.bundle().accessToken();

        synchronized (this) {
            if (chatTokenData.bundle().expiresAt().isBefore(now.plusSeconds(60))) {
                Logger.info("Access token expired, refreshing...");
                TokenBundle refreshed = oAuthService.refreshToken(chatTokenData.bundle().refreshToken());
                chatTokenData = chatTokenData.withTokenBundle(refreshed);
                twitchRepo.saveToken(chatTokenData);
            }
            return chatTokenData.bundle().accessToken();
        }
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
        twitchDataList.set(index, twitchDataList.get(index)
                .withTimestamp(System.currentTimeMillis())
                .withStreamId(event.getStream().getId()));
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

        Optional<TwitchData> optionalData = twitchDataList.stream()
                .filter(data -> data.username().equals(event.getChannel().getId()))
                .findFirst();

        if (optionalData.isEmpty() || optionalData.get().announcementId() == null) {
            Logger.error("Data missing for channel: {}", event.getChannel().getName());
            return;
        }

        scheduler.schedule(() -> updateVodMessage(event, optionalData.get()), 2, TimeUnit.MINUTES);
    }

    private void updateVodMessage(ChannelGoOfflineEvent event, TwitchData data) {
        try {
            var videoList = twitchClient.getHelix()
                    .getVideos(getValidChatAccessToken(), Collections.singletonList(data.streamId()),
                            event.getChannel().getId(), null, null, null, null,
                            null, null, null, null)
                    .execute().getVideos();

            if (videoList.isEmpty()) {
                Logger.error("No VOD found for the latest stream of channel {}", event.getChannel().getName());
                return;
            }

            Video lastVod = videoList.getFirst();

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setAuthor(event.getChannel().getName(), HTTPS_TWITCH_TV
                    + event.getChannel().getName(), twitchClient.getHelix()
                    .getUsers(null, Collections.singletonList(event.getChannel().getId()),
                            Collections.singletonList(event.getChannel().getName())).execute().getUsers()
                    .getFirst().getProfileImageUrl());
            embedBuilder.setTitle(lastVod.getTitle());
            embedBuilder.addField("Game", data.gameName(), true);
            embedBuilder.addField("Duration", lastVod.getDuration(), true);
            embedBuilder.setImage(lastVod.getThumbnailUrl(852, 480)
                    + "?t=" + CryptoUtils.generateNonce(4));
            embedBuilder.setThumbnail(data.boxArtUrl());
            embedBuilder.setFooter("Last online");
            embedBuilder.setTimestamp(event.getFiredAtInstant());
            Logger.info("Embed builder set up");

            MessageChannel textChannel = discordAPI.getChannelById(MessageChannel.class,
                    data.channel());
            textChannel.editMessageById(data.announcementId(),
                            event.getChannel().getName() + " was live")
                    .and(textChannel.editMessageEmbedsById(
                                    data.announcementId(),
                                    embedBuilder.build())
                            .setActionRow(Button.link(lastVod.getUrl(), "Watch VOD"))).queue();
            Logger.info("Announcement updated");
        } catch (Exception e) {
            Logger.error("Failed to update VOD link in background task", e);
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
                        getValidChatAccessToken(),
                        broadcasterId,
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

    private void handleChannelPointsRedemptionEvent(ChannelPointsCustomRewardRedemptionEvent event) {
        String rewardId = event.getReward().getId();
        String userId = event.getUserId();

        if (redemptionIds.contains(rewardId) || checkinConfigs.containsKey(rewardId)) {
            try {
                int newCount = twitchRepo.incrementRedemptionCount(rewardId, userId);

                if (checkinConfigs.containsKey(rewardId)) {
                    Pair<Integer, Integer> config = checkinConfigs.get(rewardId);
                    int required = config.getKey();
                    int days = config.getValue();

                    if (newCount > 0 && newCount % required == 0) {
                        if (grantVip(userId, days)) {
                            twitchClient.getChat().sendMessage(event.getBroadcasterUserName(),
                                    "@" + event.getUserName() + " has reached " + newCount + " "
                                            + event.getReward().getTitle() + " redeems and earned VIP for " + days +
                                            (days > 1 ?
                                                    " days!"
                                                    : " day!"));
                            return;
                        }
                    }
                }
                twitchClient.getChat().sendMessage(event.getBroadcasterUserName(),
                        "@" + event.getUserName() + " has redeemed \"" + event.getReward().getTitle()
                                + "\" " + newCount + (newCount > 1 ? " times!" : " time!"));
            } catch (SQLException e) {
                Logger.error(e);
            }
        }
    }

    private boolean grantVip(String userId, int days) {
        try {
            boolean isTrackedTemp = twitchRepo.isTemporaryVip(userId, broadcasterId);

            if (!isTrackedTemp) {
                if (broadcasterId.equals(userId)) {
                    Logger.info("User {} is the broadcaster. The bot will not interfere.", userId);
                    return false;
                }

                var vips = BotMain.getContext().twitchVipsCacheService().getOrFetch(broadcasterId);

                if (vips.stream().anyMatch(vip -> vip.getUserId().equals(userId))) {
                    Logger.info("User {} is already a permanent VIP. The bot will not interfere.", userId);
                    return false;
                }

                var moderators = BotMain.getContext().twitchModeratorsCacheService().getOrFetch(broadcasterId);

                if (moderators.stream().anyMatch(moderator -> moderator.getUserId().equals(userId))) {
                    Logger.info("User {} is a moderator. The bot will not interfere.", userId);
                    return false;
                }
            }

            long millisInDay = 86_400_000L;
            long newExpiration;

            if (isTrackedTemp) {
                long currentExp = twitchRepo.getVipExpiration(userId, broadcasterId);
                long base = Math.max(currentExp, System.currentTimeMillis());
                newExpiration = base + (days * millisInDay);

                Logger.info("Extending VIP for user {} by {} days.", userId, days);
            } else {
                newExpiration = System.currentTimeMillis() + (days * millisInDay);

                try {
                    twitchClient.getHelix().addChannelVip(getValidStreamerAccessToken(), broadcasterId, userId).execute();
                    Logger.info("Granted new VIP to user {}.", userId);
                } catch (Exception e) {
                    Logger.error("Failed to add VIP role on Twitch for " + userId, e);
                    return false;
                }
            }

            twitchRepo.saveActiveVip(userId, broadcasterId, newExpiration);
        } catch (Exception e) {
            Logger.error("Error in grantVip logic for user " + userId, e);
        }
        return true;
    }

    private void setup() throws Exception {
        twitchClient = TwitchClientBuilder.builder()
                .withClientId(config.get("TWITCH_CLIENT_ID"))
                .withClientSecret(config.get("TWITCH_CLIENT_SECRET"))
                .withEnableChat(true)
                .withEnableHelix(true)
                .withEnableEventSocket(true)
                .withChatAccount(new OAuth2Credential("twitch", getValidChatAccessToken()))
                .build();

        load();

        twitchClient.getEventSocket().register(
                new OAuth2Credential("twitch", getValidStreamerAccessToken()),
                SubscriptionTypes.CHANNEL_POINTS_CUSTOM_REWARD_REDEMPTION_ADD
                        .prepareSubscription(
                                b -> b.broadcasterUserId(broadcasterId).build(),
                                null
                        )
        );

        if (!twitchDataList.isEmpty()) {
            moderatorId = chatTokenData.userId();

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
        oAuthService = BotMain.getContext().twitchOauthService();

        streamerTokenData = twitchRepo.loadToken(true);
        chatTokenData = twitchRepo.loadToken();

        setup();

        twitchClient.getEventManager()
                .onEvent(ChannelMessageEvent.class, this::handleChannelMessageEvent);
        if (moderatorId != null && !moderatorId.isEmpty())
            twitchClient.getEventManager().onEvent(RaidEvent.class, this::handleRaidEvent);
        twitchClient.getEventManager()
                .onEvent(ChannelGoLiveEvent.class, this::handleChannelGoLiveEvent);
        twitchClient.getEventManager()
                .onEvent(ChannelGoOfflineEvent.class, this::handleChannelGoOfflineEvent);
        twitchClient.getEventManager()
                .onEvent(ChannelPointsCustomRewardRedemptionEvent.class, this::handleChannelPointsRedemptionEvent);

        scheduleLoad();
        Logger.info("Twitch API started");
    }
}
