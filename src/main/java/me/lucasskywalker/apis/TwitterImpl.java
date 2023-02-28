package me.lucasskywalker.apis;

import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.TwitterCredentialsBearer;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.model.Get2UsersByResponse;
import com.twitter.clientlib.model.Get2UsersIdTweetsResponse;
import com.twitter.clientlib.model.Tweet;
import me.lucasskywalker.BotMain;
import net.dv8tion.jda.api.JDA;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TwitterImpl {

    private final TwitterApi api;
    private final JDA discordAPI;
    private final List<String> username = new ArrayList<>();
    private final List<String> userID = new ArrayList<>();
    private final List<OffsetDateTime> createdAt = new ArrayList<>();
    private final List<String> channel = new ArrayList<>();
    private final List<String> message = new ArrayList<>();

    public void readCSV() {
        try {
            username.clear();
            createdAt.clear();
            channel.clear();
            message.clear();

            FileReader fileReader = new FileReader(new File(YoutubeImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/twitter_data.csv");

            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(";")
                    .setHeader("username", "createdAt", "channel", "message")
                    .setSkipHeaderRecord(true)
                    .build();

            Iterable<CSVRecord> csvIterable = csvFormat.parse(fileReader);

            for(CSVRecord record : csvIterable) {
                createdAt.add(OffsetDateTime.parse(record.get("createdAt")));
                channel.add(record.get("channel"));
                message.add(record.get("message"));
            }

            fileReader = new FileReader(new File(YoutubeImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/twitter_names.csv");

            csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(";")
                    .setHeader("username")
                    .setSkipHeaderRecord(true)
                    .build();

            csvIterable = csvFormat.parse(fileReader);

            for(CSVRecord record : csvIterable) {
                username.add(record.get("username"));
            }
        } catch (URISyntaxException | IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private void writeCSV() {
        try {
            File filePath = new File(new File(YoutubeImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/twitter_data.csv");

            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(";")
                    .setHeader("username", "createdAt", "channel", "message")
                    .build();

            FileWriter fileWriter = new FileWriter(filePath, false);

            CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);
            for(int i = 0; i < username.size(); i++) {
                csvPrinter.printRecord(
                        username.get(i),
                        createdAt.get(i),
                        channel.get(i),
                        message.get(i));
            }
            csvPrinter.close();
        } catch (IOException | URISyntaxException e) {
            System.out.println(e.getMessage());
        }
    }

    public void getUserID() {
        try {
            Get2UsersByResponse response = api.users().findUsersByUsername(username.stream().distinct()
                    .collect(Collectors.toList())).execute();
            for(int i = 0; i < response.getData().size(); i++)
                userID.add(response.getData().get(i).getId());
        } catch (ApiException e) {
            System.out.println(e.getMessage());
        }
    }

    private void getLatestTweet() {
        readCSV();
        try {
            Set<String> tweetFields = new HashSet<>();
            tweetFields.add("created_at");
            tweetFields.add("in_reply_to_user_id");
            for(int i = 0; i < userID.size(); i++) {
                Get2UsersIdTweetsResponse response = api.tweets().usersIdTweets(userID.get(i)).tweetFields(tweetFields)
                        .maxResults(5).execute();
                for(int y = response.getData().size() - 1; y >= 0; y--) {
                    Tweet tweet = response.getData().get(y);
                    //for()
                    if(tweet.getCreatedAt().isAfter(createdAt.get(i)) && tweet.getInReplyToUserId() == null) {
                        discordAPI.getTextChannelById(channel.get(i))
                                .sendMessage(message.get(i).replace("\\n", "\n") + "\n"
                                        + "https://twitter.com/" + username.get(i) + "/status/" + tweet.getId())
                                .complete();
                        createdAt.set(i, tweet.getCreatedAt());
                    }
                }
            }
            if(userID.size() > 0)
                writeCSV();
        } catch (ApiException e) {
            System.out.println(e.getMessage());
        }
    }

    private void scheduleTweetCheck() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::getLatestTweet, 0, 1, TimeUnit.MINUTES);
    }

    public TwitterImpl(JDA discordAPI) {
        this.discordAPI = discordAPI;
        api = new TwitterApi(new TwitterCredentialsBearer(BotMain.getConfig().get("BEARER_TOKEN")));
        readCSV();
        getUserID();
        scheduleTweetCheck();
    }
}
