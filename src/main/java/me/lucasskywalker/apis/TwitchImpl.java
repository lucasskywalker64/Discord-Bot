package me.lucasskywalker.apis;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.RaidEvent;
import com.github.twitch4j.common.util.CryptoUtils;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.helix.domain.User;
import me.lucasskywalker.BotMain;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TwitchImpl {

    private final List<String> channel = new ArrayList<>();
    private final List<String> message = new ArrayList<>();
    private final List<String> username = new ArrayList<>();
    private final List<String> role = new ArrayList<>();
    private final List<Long> timestamp = new ArrayList<>();
    private final List<String> shoutoutNames = new ArrayList<>();
    private final List<String> shoutedoutNames = new ArrayList<>();
    private final TwitchClient twitchClient;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private Long shoutoutTimestamp = 0L;

    public List<String> getShoutoutNames() { return shoutoutNames; }


    public void updateLists() {
        try {
            channel.clear();
            message.clear();
            username.clear();
            role.clear();
            shoutoutNames.clear();

            FileReader fileReader = new FileReader(new File(TwitchImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/twitch.csv");

            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(";")
                    .setHeader("channel", "message", "username", "role")
                    .setSkipHeaderRecord(true)
                    .build();

            Iterable<CSVRecord> csvIterable = csvFormat.parse(fileReader);

            for(CSVRecord record : csvIterable) {
                channel.add(record.get("channel"));
                message.add(record.get("message"));
                username.add(record.get("username"));
                if(!record.get("role").contains("@"))
                    role.add(record.get("role"));
                else
                    role.add(record.get("role")
                            .substring(record.get("role").indexOf("&") + 1, record.get("role")
                                    .lastIndexOf(">")));
            }

            while(timestamp.size() < username.size())
                timestamp.add(0L);

            fileReader = new FileReader(new File(YoutubeImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/shoutout.csv");

            csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setHeader("username")
                    .setSkipHeaderRecord(true)
                    .build();

            csvIterable = csvFormat.parse(fileReader);

            for(CSVRecord record : csvIterable) {
                shoutoutNames.add(record.get("username"));
            }

            twitchClient.getClientHelper().disableStreamEventListener(username);
            twitchClient.getClientHelper().enableStreamEventListener(username);
        } catch (IOException | URISyntaxException ignored) {
        }
    }

    public void scheduleUpdateLists() {
        scheduler.scheduleAtFixedRate(this::updateLists, 1, 1, TimeUnit.DAYS); }

    private void postStreamAnnouncement(ChannelGoLiveEvent event, JDA discordAPI, int index) {
        timestamp.set(index, System.currentTimeMillis());
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Game", event.getStream().getGameName(), false);
        embedBuilder.setAuthor(event.getChannel().getName() + " is now streaming", "https://twitch.tv/"
                + event.getChannel().getName(), twitchClient.getHelix().getUsers(null,
                        Collections.singletonList(event.getChannel().getId()),
                        Collections.singletonList(event.getChannel().getName())).execute().getUsers().get(0)
                .getProfileImageUrl());
        embedBuilder.setTitle(event.getStream().getTitle(), "https://twitch.tv/" + event.getChannel().getName());
        embedBuilder.setImage(event.getStream().getThumbnailUrl(852, 480)
                + "?t=" + CryptoUtils.generateNonce(4));
        embedBuilder.setFooter(discordAPI.getSelfUser().getName());
        embedBuilder.setTimestamp(Instant.now());

        String messagePart2 = message.get(index).substring(message.get(index).indexOf("\\n") + 2).strip();
        String tempMessage = message.get(index).substring(0, message.get(index).lastIndexOf("\\n") + 2)
                .replace("\\n", "\n")
                + messagePart2;

        String messageId;
        if (role.size() >= 1 && !role.get(index).isBlank())
            messageId = discordAPI.getTextChannelById(channel.get(index)).sendMessage(
                            discordAPI.getRoleById(role.get(index)).getAsMention() + " " + tempMessage)
                    .addEmbeds(embedBuilder.build())
                    .addActionRow(Button.link("https://twitch.tv/" + username.get(index), "Watch Stream"))
                    .complete().getId();
        else messageId = discordAPI.getTextChannelById(channel.get(index)).sendMessage(tempMessage)
                .addEmbeds(embedBuilder.build())
                .addActionRow(Button.link("https://twitch.tv/" + username.get(index), "Watch Stream"))
                .complete().getId();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                embedBuilder.setImage(event.getStream().getThumbnailUrl(852, 480) + "?t="
                        + CryptoUtils.generateNonce(4));
                discordAPI.getTextChannelById(channel.get(index))
                        .editMessageEmbedsById(messageId, embedBuilder.build()).queue();
            }
        }, 300000);
    }

    /*public void testStream() {
        ChannelGoLiveEvent channelGoLiveEvent = new ChannelGoLiveEvent(
                new EventChannel("62799737", "lucasskywalker64"), twitchClient.getHelix()
                .getStreams(null, null, null, null, null, null,
                        null, Collections.singletonList("lucasskywalker64")).execute().getStreams().get(0));
        twitchClient.getEventManager().publish(channelGoLiveEvent);
    }*/

    public TwitchImpl(JDA discordAPI) {
        System.out.println("Starting Twitch api...");

        twitchClient = TwitchClientBuilder.builder()
                .withClientId(BotMain.getConfig().get("TWITCH_CLIENT_ID"))
                .withClientSecret(BotMain.getConfig().get("TWITCH_CLIENT_SECRET"))
                .withEnableChat(true)
                .withChatAccount(new OAuth2Credential("twitch",
                        BotMain.getConfig().get("TWITCH_ACCESS_TOKEN")))
                .withEnableHelix(true)
                .build();

        updateLists();

        String streamerId = twitchClient.getHelix().getUsers(null, null,
                        Collections.singletonList(username.get(0)))
                .execute().getUsers().get(0).getId();

        twitchClient.getChat().joinChannel(username.get(0));

        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            if(!shoutedoutNames.contains(event.getUser().getName().toLowerCase())) {
                if(!event.getUser().getName().equalsIgnoreCase("freudnim")
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
        });

        List<User> moderatorList = twitchClient.getHelix().getUsers(null, null,
                        List.of("Smolelibot", "Sirwibby")).execute().getUsers();
        String moderatorId = username.get(0).equals("elinovavt")
                ? moderatorList.get(0).getId()
                : moderatorList.get(1).getId();

        // TODO: Test shoutout queue system
        twitchClient.getEventManager().onEvent(RaidEvent.class, raidEvent -> {
            if(shoutoutTimestamp + 120000L < System.currentTimeMillis()) {
                twitchClient.getHelix().sendShoutout(BotMain.getConfig().get("TWITCH_ACCESS_TOKEN"),
                        streamerId,
                        raidEvent.getRaider().getId(), moderatorId).queue();
                shoutoutTimestamp = System.currentTimeMillis();
            }
            else {
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        RaidEvent delayedRaidEvent =
                                new RaidEvent(raidEvent.getChannel(), raidEvent.getRaider(), raidEvent.getViewers());
                        twitchClient.getEventManager().publish(delayedRaidEvent);
                    }
                }, (shoutoutTimestamp + 120000L) - System.currentTimeMillis() + 5000);
            }
        });

        twitchClient.getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
            int index = username.indexOf(event.getChannel().getName().toLowerCase());
            if(timestamp.get(index) + 10800000L < System.currentTimeMillis()) {
                postStreamAnnouncement(event, discordAPI, index);
                if(index == 0)
                    shoutedoutNames.clear();
            }
        });

        scheduleUpdateLists();
    }
}
