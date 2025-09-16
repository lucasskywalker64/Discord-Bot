package com.github.lucasskywalker64.api.youtube;

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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final List<YouTubeData> youTubeDataList = new ArrayList<>();
    private final JDA discordAPI;
    private final YouTube youTube;
    private final YouTubeRepository repository = YouTubeRepository.getInstance();

    /**
     * Load the saved YouTube data and populate local lists.
     */
    private void load() {
        youTubeDataList.clear();
        youTubeDataList.addAll(repository.loadAll());
    }

    /**
     * Saves YouTube data.
     */
    private void save() throws IOException {
        repository.saveAll(youTubeDataList, false);
    }

    /**
     * Make a YouTube API call to check if there has been a new upload on the stored channels since the last call.
     * Calls {@link #load()} to check for channel IDs and last video ID.
     */
    private void checkForNewUpload() throws IOException {
        load();
        List<YouTubeData> old = new ArrayList<>(youTubeDataList);
        for (YouTubeData data : youTubeDataList) {
            PlaylistItemListResponse playlistResponse = youTube.playlistItems()
                    .list(Collections.singletonList("contentDetails"))
                    .setPlaylistId(data.playlistId())
                    .setMaxResults(1L)
                    .setKey(API_KEY)
                    .execute();

            String videoId = playlistResponse.getItems()
                    .getFirst()
                    .getContentDetails()
                    .getVideoId();

            if (!videoId.equals(data.videoId()) && !videoId.equals(data.streamId())) {
                Video video = youTube.videos()
                        .list(Collections.singletonList("snippet,liveStreamingDetails"))
                        .setId(Collections.singletonList(videoId))
                        .setKey(API_KEY)
                        .execute()
                        .getItems()
                        .getFirst();

                Logger.info(video);
                if (video.getLiveStreamingDetails() != null) {
                    if (video.getLiveStreamingDetails().getActualEndTime() == null) {
                        youTubeDataList.set(youTubeDataList.indexOf(data), data.withStreamId(videoId));
                        discordAPI.getChannelById(MessageChannel.class, data.channel())
                                .sendMessage(getUrl(videoId))
                                .queue();
                    }
                } else {
                    youTubeDataList.set(youTubeDataList.indexOf(data), data.withVideoId(videoId));
                    discordAPI.getChannelById(MessageChannel.class, data.channel())
                            .sendMessage(discordAPI.getRoleById(data.roleId())
                                    .getAsMention() + " " + data.message().replace("\\n", "\n") + "\n"
                                    + getUrl(videoId))
                            .queue();
                }
            }
        }
        if (!old.equals(youTubeDataList)) {
            save();
        }
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

    /**
     * Scheduler to periodically call {@link #checkForNewUpload()}.
     */
    private void scheduleUploadCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkForNewUpload();
            } catch (IOException e) {
                Logger.error(e);
            }
        }, 30, 5 * 60L, TimeUnit.SECONDS);
    }

    public String getPlaylistIdFromChannelName(String channelName) throws IOException, InvalidParameterException {
        ChannelListResponse channelResponse;
        if (channelName.startsWith("@")) {
            channelResponse = youTube.channels()
                    .list(Collections.singletonList("contentDetails"))
                    .setForHandle(channelName)
                    .setKey(API_KEY)
                    .execute();
        } else {
            channelResponse = youTube.channels()
                    .list(Collections.singletonList("contentDetails"))
                    .setForUsername(channelName)
                    .setKey(API_KEY)
                    .execute();
        }

        if (channelResponse.getItems().isEmpty())
            throw new InvalidParameterException(1001, null);

        String playlistId = channelResponse.getItems()
                .getFirst()
                .getContentDetails()
                .getRelatedPlaylists()
                .getUploads();

        try {
            youTube.playlistItems()
                    .list(Collections.singletonList("contentDetails"))
                    .setPlaylistId(playlistId)
                    .setMaxResults(1L)
                    .setKey(API_KEY)
                    .execute();
        } catch (Exception e) {
            throw new InvalidParameterException(1001, e.getCause());
        }

        return playlistId;
    }

    public void shutdown() throws InterruptedException, IOException {
        scheduler.shutdown();
        scheduler.awaitTermination(3, TimeUnit.SECONDS);
        save();
        Logger.info("YouTube API shutdown");
    }

    /**
     * Create new YoutubeImpl
     */
    public YouTubeImpl(JDA discordAPI) throws GeneralSecurityException, IOException {
        Logger.info("Starting YouTube API...");
        this.discordAPI = discordAPI;
        youTube = new Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, null)
                .setApplicationName(APP_NAME)
                .build();
        scheduleUploadCheck();
        Logger.info("YouTube API started");
    }
}
