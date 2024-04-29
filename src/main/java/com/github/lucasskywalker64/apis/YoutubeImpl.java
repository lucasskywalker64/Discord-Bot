package com.github.lucasskywalker64.apis;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ActivityListResponse;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.github.lucasskywalker64.BotMain;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.tinylog.Logger;

@SuppressWarnings("java:S1192")
public class YoutubeImpl {

  private static final String YOUTUBE_API_KEY = BotMain.getConfig().get("YOUTUBE_API_KEY");
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final File youtoubeFile = BotMain.getYoutubeFile();
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
    try (FileReader fileReader = new FileReader(youtoubeFile)) {
      channel.clear();
      message.clear();
      ytchannelid.clear();
      role.clear();
      videoid.clear();


      CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
          .setDelimiter(";")
          .setHeader("channel", "message", "ytchannelid", "role", "videoid")
          .setSkipHeaderRecord(true)
          .build();

      Iterable<CSVRecord> csvIterable = csvFormat.parse(fileReader);

      for (CSVRecord csvRecord : csvIterable) {
        channel.add(csvRecord.get("channel"));
        message.add(csvRecord.get("message"));
        ytchannelid.add(csvRecord.get("ytchannelid"));
          if (!csvRecord.get("role").contains("@")) {
              role.add(csvRecord.get("role"));
          } else {
              role.add(csvRecord.get("role")
                  .substring(csvRecord.get("role").indexOf("&") + 1, csvRecord.get("role")
                      .lastIndexOf(">")));
          }
          if (!csvRecord.get("videoid").isBlank()) {
              videoid.add(csvRecord.get("videoid"));
          }
      }
    } catch (IOException e) {
      Logger.error(e);
    }
  }

  /**
   * Update the CSV that stores YouTube data with the new video ID.
   */
  private void updateCSV() {
    CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
        .setDelimiter(";")
        .setHeader("channel", "message", "ytchannelid", "role", "videoid")
        .build();
    try (FileWriter fileWriter = new FileWriter(youtoubeFile, false);
         CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat)) {
      for (int i = 0; i < channel.size(); i++) {
        csvPrinter.printRecord(
            channel.get(i),
            message.get(i),
            ytchannelid.get(i),
            role.get(i),
            videoid.get(i));
      }
    } catch (IOException e) {
      Logger.error(e);
    }
  }

  /**
   * Make a YouTube API call to check if there has been a new upload on the stored channels since the last call.
   * Calls {@link #readCSV()} to check for channel IDs and last video ID.
   *
   * @param request The YouTube API reference that the request is sent to.
   */
  private void checkForNewVideo(YouTube.Activities.List request) throws IOException {
    readCSV();
    for (int i = 0; i < ytchannelid.size(); i++) {
      ActivityListResponse response = request.setKey(YOUTUBE_API_KEY)
          .setChannelId(ytchannelid.get(i)).execute();
      String videoID = response.getItems().get(0).getContentDetails().getUpload().getVideoId();
      if (!videoID.equals(videoid.get(i))) {
        videoid.set(i, videoID);
        discordAPI.getChannelById(MessageChannel.class, channel.get(i))
            .sendMessage(discordAPI.getRoleById(role.get(i))
                .getAsMention() + " " + message.get(i).replace("\\n", "\n") + "\n"
                + "https://www.youtube.com/watch?v=" + videoID)
            .queue();
      }
    }
      if (!ytchannelid.isEmpty()) {
          updateCSV();
      }
  }

  /**
   * Scheduler to periodically call {@link #checkForNewVideo(YouTube.Activities.List)}.
   */
  private void scheduleVideoCheck() {
    scheduler.scheduleAtFixedRate(() -> {
      try {
        checkForNewVideo(request);
      } catch (IOException e) {
        Logger.error(e);
      }
    }, 30, 5 * 60L, TimeUnit.SECONDS);
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
      Logger.info("YouTube API started");
    } catch (GeneralSecurityException | IOException e) {
      Logger.error(e);
    }
  }

  public void cleanUp() {
    scheduler.shutdownNow();
    request.clear();
    updateCSV();
    channel.clear();
    message.clear();
    ytchannelid.clear();
    role.clear();
    videoid.clear();
  }

  /**
   * Create new YoutubeImpl
   */
  public YoutubeImpl(JDA discordAPI) {
    Logger.info("Starting YouTube API...");
    this.discordAPI = discordAPI;
    init();
  }
}
