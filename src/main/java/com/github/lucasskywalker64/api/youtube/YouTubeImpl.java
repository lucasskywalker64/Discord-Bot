package com.github.lucasskywalker64.api.youtube;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.github.lucasskywalker64.BotContext;
import com.github.lucasskywalker64.api.youtube.model.Entry;
import com.github.lucasskywalker64.api.youtube.model.Feed;
import com.github.lucasskywalker64.exceptions.InvalidParameterException;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTube.Builder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import com.github.lucasskywalker64.BotMain;
import com.google.api.services.youtube.model.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.tinylog.Logger;

@SuppressWarnings({"java:S1192", "DataFlowIssue"})
public class YouTubeImpl {

    private static final String APP_NAME = BotMain.getContext().config().get("YOUTUBE_APP_NAME");
    private static final String API_KEY = BotMain.getContext().config().get("YOUTUBE_API_KEY");
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private final ExecutorService executor;
    private final List<YouTubeData> youTubeDataList = new ArrayList<>();
    private final JDA discordAPI;
    private final YouTube youTube;
    private final YouTubeRepository repository = YouTubeRepository.getInstance();
    private final String HUB_URL;
    private final String TOPIC_BASE_URL;
    private final String CALLBACK_BASE_URL;
    private final XmlMapper xmlMapper;
    private final Map<String, CompletableFuture<Integer>> pendingChallenges;

    private enum HubMode { SUBSCRIBE, UNSUBSCRIBE }

    private void sendHubRequest(
            HubMode mode,
            String ytChannelId,
            String guildId,
            String secret,
            String token
    ) throws IOException, InterruptedException {
        String callbackUrl = String.format("%s?channel_id=%s&guild_id=%s&token=%s",
                CALLBACK_BASE_URL, ytChannelId, guildId, token);
        String topicUrl = String.format("%s?channel_id=%s", TOPIC_BASE_URL, ytChannelId);
        String formBody = "hub.mode=" + (mode == HubMode.SUBSCRIBE ? "subscribe" : "unsubscribe") +
                "&hub.topic=" + URLEncoder.encode(topicUrl, StandardCharsets.UTF_8) +
                "&hub.callback=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8) +
                "&hub.secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HUB_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            if (Thread.interrupted())
                Logger.warn("Command thread arrived interrupted; clearing flag to proceed");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!ok) {
                Logger.warn("YouTube hub {} request failed with status {}: {}",
                        mode == HubMode.SUBSCRIBE ? "subscription" : "unsubscription",
                        response.statusCode(), response.body());
            } else {
                Logger.info("YouTube hub {} request succeeded with status {}",
                        mode == HubMode.SUBSCRIBE ? "subscription" : "unsubscription",
                        response.statusCode());
            }
        }
    }

    private Integer websubChallengeWithRetry(HubMode mode, int maxRetries, long initialDelay, String token, YouTubeData data)
            throws IOException, InterruptedException, ExecutionException {
        int attempts = 0;
        long currentDelay = initialDelay;
        final long MAX_DELAY = 10000;

        try {
            while (attempts < maxRetries) {
                CompletableFuture<Integer> challengeFuture = new CompletableFuture<>();
                pendingChallenges.put(token, challengeFuture);

                try {
                    Logger.info("Attempting Youtube {} {}/{}",
                            mode == HubMode.SUBSCRIBE ? "subscription" : "unsubscription",
                            (attempts + 1), maxRetries);

                    sendHubRequest(mode, data.channelId(), data.guildId(), data.secret(), token);

                    return challengeFuture.get(3, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    attempts++;
                    Logger.error("Websub {} timed out (Attempt {}/{})",
                            mode == HubMode.SUBSCRIBE ? "subscription" : "unsubscription",
                            attempts, maxRetries);

                    if (attempts >= maxRetries) {
                        break;
                    }

                    long exponentialDelay = Math.min(currentDelay * 2, MAX_DELAY);
                    long jitteredDelay = ThreadLocalRandom.current().nextLong(currentDelay) + exponentialDelay;
                    currentDelay = exponentialDelay;
                    Logger.info("Retrying in {}ms", jitteredDelay);
                    Thread.sleep(jitteredDelay);
                }
            }
        } finally {
            pendingChallenges.remove(token);
        }
        return null;
    }

    /**
     * Load the saved YouTube data and populate local lists.
     */
    public void load() throws SQLException {
        youTubeDataList.clear();
        youTubeDataList.addAll(repository.loadAll());
    }

    /**
     * Saves YouTube data.
     */
    private void save() throws SQLException {
        repository.saveAll(youTubeDataList);
    }

    public void processNotification(String xmlPayload, YouTubeData staleData) {
        try {
            YouTubeData data = repository.get(staleData.channelId(), staleData.guildId());
            if (data == null) {
                Logger.warn("Data disappeared for {}", staleData.channelId());
                return;
            }

            Entry entry = xmlMapper.readValue(xmlPayload, Feed.class).entry;
            if (entry != null) {
                if (data.videoIds().contains(entry.videoId)) {
                    Logger.info("Duplicate notification received, skipping: {}", entry.videoId);
                    return;
                }

                Video video = youTube.videos()
                        .list(Collections.singletonList("liveStreamingDetails,snippet,contentDetails"))
                        .setId(Collections.singletonList(Objects.requireNonNull(entry.videoId)))
                        .setKey(API_KEY)
                        .execute()
                        .getItems()
                        .getFirst();

                if (video.getSnippet().getLiveBroadcastContent().equals("upcoming"))
                    return;

                if (video.getLiveStreamingDetails() != null
                        && video.getLiveStreamingDetails().getActualEndTime() == null
                        && video.getContentDetails().getDuration() != null) {
                    discordAPI.getChannelById(MessageChannel.class, data.discordChannelId())
                            .sendMessage(entry.link.href)
                            .queue();
                } else {
                    discordAPI.getChannelById(MessageChannel.class, data.discordChannelId())
                            .sendMessage(discordAPI.getRoleById(data.roleId()).getAsMention()
                                    + " " + data.message().replace("\\n", "\n")
                                    + "\n" + entry.link.href)
                            .queue();
                }
                data.videoIds().add(entry.videoId);
                repository.save(data);
                load();
            }
        } catch (Exception e) {
            Logger.error(e);
            throw new RuntimeException(e);
        }
    }

    public String getChannelId(String channelName) throws IOException, InvalidParameterException {
        ChannelListResponse channelResponse;
        if (channelName.startsWith("@")) {
            channelResponse = youTube.channels()
                    .list(Collections.singletonList("id"))
                    .setForHandle(channelName)
                    .setKey(API_KEY)
                    .execute();
        } else {
            channelResponse = youTube.channels()
                    .list(Collections.singletonList("id"))
                    .setForUsername(channelName)
                    .setKey(API_KEY)
                    .execute();
        }

        if (channelResponse == null || channelResponse.getItems().isEmpty())
            throw new InvalidParameterException(1001, null);

        return channelResponse.getItems()
                .getFirst()
                .getId();
    }

    public void shutdown() throws InterruptedException, SQLException {
        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);
        save();
        Logger.info("YouTube API shutdown");
    }

    public String generateRandomToken() {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[64];
        random.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public YouTubeData subscribeWithRetry(int maxRetries, long initialDelay, String token, YouTubeData data) throws IOException, InterruptedException, SQLException, ExecutionException {
        Integer leaseSeconds = websubChallengeWithRetry(HubMode.SUBSCRIBE, maxRetries, initialDelay, token, data);
        if (leaseSeconds == null) return null;
        long expirationTime = System.currentTimeMillis() + leaseSeconds * 1000L;
        data = data.withExpirationTime(expirationTime);
        repository.save(data);
        load();
        return data;
    }

    public boolean unsubscribeWithRetry(int maxRetries, long initialDelay, String token, YouTubeData data) throws IOException, InterruptedException, ExecutionException {
        Integer result = websubChallengeWithRetry(HubMode.UNSUBSCRIBE, maxRetries, initialDelay, token, data);
        return result != null;
    }

    public void checkAndRenewSubscriptions() {
        Logger.info("Checking for subscriptions to renew...");
        long now = System.currentTimeMillis();
        long renewalThreshold = now + TimeUnit.HOURS.toMillis(25);

        List<YouTubeData> subscriptionsToRenew = youTubeDataList.stream()
                .filter(data -> data.expirationTime() != null && data.expirationTime() < renewalThreshold)
                .toList();

        if (subscriptionsToRenew.isEmpty()) {
            Logger.info("No subscriptions to renew");
            return;
        }

        Logger.info("Found {} subscription(s) to renew", subscriptionsToRenew.size());

        executor.submit(() -> {
            for (YouTubeData data : subscriptionsToRenew) {
                String token = generateRandomToken();
                try {
                    data = subscribeWithRetry(5, 200, token, data);
                    if (data != null) {
                        Logger.info("Successfully renewed subscription for channel {}. New expiry: {}",
                                data.name(),
                                Instant.ofEpochMilli(data.expirationTime()));
                    } else {
                        Logger.error("Failed to renew subscription for channel {}", data.name());
                    }
                } catch (Exception e) {
                    Logger.error(e, "Failed to renew subscription for channel: {}", data.name());
                } finally {
                    if (token != null)
                        pendingChallenges.remove(token);
                }
            }
        });
    }

    /**
     * Create new YoutubeImpl
     */
    public YouTubeImpl() throws GeneralSecurityException, IOException, SQLException {
        Logger.info("Starting YouTube API...");
        load();
        BotContext context = BotMain.getContext();
        this.discordAPI = context.jda();
        pendingChallenges = context.pendingChallenges();
        youTube = new Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, null)
                .setApplicationName(APP_NAME)
                .build();
        executor = context.taskExecutor();
        HUB_URL = "https://pubsubhubbub.appspot.com/";
        TOPIC_BASE_URL = "https://www.youtube.com/xml/feeds/videos.xml";
        CALLBACK_BASE_URL = context.config().get("SERVER_BASE_URL") + context.config().get("YOUTUBE_CALLBACK_PATH");
        xmlMapper = new XmlMapper();
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Logger.info("YouTube API started");
    }
}
