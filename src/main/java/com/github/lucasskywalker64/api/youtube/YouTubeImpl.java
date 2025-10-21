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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.github.lucasskywalker64.BotMain;
import com.google.api.services.youtube.model.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
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

    public void subscribeToChannel(
            String ytChannelId,
            String guildId,
            String discordChannelId,
            String secret,
            String token) throws IOException, InterruptedException {
        String callbackUrl = String.format("%s?channel_id=%s&guild_id=%s&discord_channel_id=%s&token=%s",
                CALLBACK_BASE_URL, ytChannelId, guildId, discordChannelId, token);
        String topicUrl = String.format("%s?channel_id=%s", TOPIC_BASE_URL, ytChannelId);
        String formBody = "hub.mode=subscribe" +
                "&hub.topic=" + URLEncoder.encode(topicUrl, StandardCharsets.UTF_8) +
                "&hub.callback=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8) +
                "&hub.secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HUB_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /**
     * Load the saved YouTube data and populate local lists.
     */
    public void load() {
        youTubeDataList.clear();
        youTubeDataList.addAll(repository.loadAll());
    }

    /**
     * Saves YouTube data.
     */
    private void save() throws SQLException {
        repository.saveAll(youTubeDataList, false);
    }

    /**
     * Make a YouTube API call to check if there has been a new upload on the stored channels since the last call.
     * Calls {@link #load()} to check for channel IDs and last video ID.
     */
    public void processNotification(String xmlPayload, String discordChannelId) {
        executor.submit(() -> {
            try {
                System.out.println(xmlPayload);
                Entry entry = xmlMapper.readValue(xmlPayload, Feed.class).entry;
                VideoListResponse response = youTube.videos()
                        .list(Collections.singletonList("contentDetails,fileDetails,id,liveStreamingDetails,localizations,paidProductPlacementDetails,player,processingDetails,recordingDetails,snippet,statistics,status,suggestions,topicDetails"))
                        .setId(Collections.singletonList(entry.videoId))
                        .execute();
                System.out.println(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static String getUrl(String videoID) throws IOException {
        CloseableHttpClient client = HttpClientBuilder.create().disableRedirectHandling().build();
        HttpHead head = new HttpHead("https://www.youtube.com/shorts/" + videoID);
        String url;
        try (CloseableHttpResponse httpResponse = client.execute(head)) {
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == HttpURLConnection.HTTP_OK) {
                url = "https://www.youtube.com/shorts/" + videoID;
            } else url = "https://www.youtube.com/watch?v=" + videoID;
            client.close();
        }
        return url;
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

    /**
     * Create new YoutubeImpl
     */
    public YouTubeImpl() throws GeneralSecurityException, IOException {
        Logger.info("Starting YouTube API...");
        BotContext context = BotMain.getContext();
        this.discordAPI = context.jda();
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
