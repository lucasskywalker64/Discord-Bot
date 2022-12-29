package me.lucasskywalker.apis;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ActivityListResponse;
import me.lucasskywalker.BotMain;
import net.dv8tion.jda.api.JDA;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class YoutubeImpl {

    private final String YOUTUBE_API_KEY = BotMain.getConfig().get("YOUTUBE_API_KEY");

    private final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final List<String> channel = new ArrayList<>();

    private final List<String> message = new ArrayList<>();

    private final List<String> ytchannelid = new ArrayList<>();

    private final List<String> role = new ArrayList<>();

    private final List<String> videoid = new ArrayList<>();

    private final JDA discordAPI;

    private YouTube.Activities.List request;

    /**
     * Read the YouTube CSV file and populate local lists.
     */
    private void readCSV() {
        try {
            channel.clear();
            message.clear();
            ytchannelid.clear();
            role.clear();
            videoid.clear();

            FileReader fileReader = new FileReader(new File(YoutubeImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/youtube.csv");

            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(";")
                    .setHeader("channel", "message", "ytchannelid", "role", "videoid")
                    .setSkipHeaderRecord(true)
                    .build();

            Iterable<CSVRecord> csvIterable = csvFormat.parse(fileReader);

            for(CSVRecord record : csvIterable) {
                channel.add(record.get("channel"));
                message.add(record.get("message"));
                ytchannelid.add(record.get("ytchannelid"));
                if(!record.get("role").contains("@"))
                    role.add(record.get("role"));
                else
                    role.add(record.get("role")
                            .substring(record.get("role").indexOf("&") + 1, record.get("role")
                                    .lastIndexOf(">")));
                if(!record.get("videoid").isBlank())
                    videoid.add(record.get("videoid"));
            }
        } catch (IOException | URISyntaxException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Update the CSV that stores YouTube data with the new video ID.
     */
    private void updateCSV() {
        try {
            File filePath = new File(new File(YoutubeImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/youtube.csv");

            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(";")
                    .setHeader("channel", "message", "ytchannelid", "role", "videoid")
                    .build();

            FileWriter fileWriter = new FileWriter(filePath, false);

            CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);
            for(int i = 0; i < channel.size(); i++) {
                csvPrinter.printRecord(
                        channel.get(i),
                        message.get(i),
                        ytchannelid.get(i),
                        role.get(i),
                        videoid.get(i));
            }
            csvPrinter.close();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Make a YouTube API call to check if there has been a new upload on the stored channels since the last call.
     * Calls {@link #readCSV()} to check for channel IDs and last video ID.
     * @param request The YouTube API reference that the request is sent to.
     * @throws IOException
     */
    private void checkForNewVideo(YouTube.Activities.List request) throws IOException {
        readCSV();
        for (int i = 0; i < ytchannelid.size(); i++) {
            ActivityListResponse response = request.setKey(YOUTUBE_API_KEY).setChannelId(ytchannelid.get(i)).execute();
            String videoID = response.getItems().get(0).getContentDetails().getUpload().getVideoId();
            if (!videoID.equals(videoid.get(i))) {
                videoid.set(i, videoID);
                discordAPI.getTextChannelById(channel.get(i))
                        .sendMessage(discordAPI.getRoleById(role.get(i))
                                .getAsMention() + " " + message.get(i).replace("\\n", "\n") + "\n"
                                + "https://www.youtube.com/watch?v=" + videoID)
                        .queue();
            }
        }
        if(ytchannelid.size() > 0)
            updateCSV();
    }

    /**
     * Scheduler to periodically call {@link #checkForNewVideo(YouTube.Activities.List)}.
     */
    public void scheduleVideoCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkForNewVideo(request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    /**
     * Initialize a new instance of the YouTube API and start the video check scheduler ({@link #scheduleVideoCheck()}).
     */
    private void init() {
        try {
            final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            YouTube youTube = new YouTube.Builder(httpTransport, JSON_FACTORY, null)
                    .setApplicationName("YoutubeToDiscord")
                    .build();
            request = youTube.activities().list(Collections.singletonList("contentDetails"));
            scheduleVideoCheck();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create new YoutubeImpl
     */
    public YoutubeImpl(JDA discordAPI) {
        this.discordAPI = discordAPI;
        init();
    }
}
