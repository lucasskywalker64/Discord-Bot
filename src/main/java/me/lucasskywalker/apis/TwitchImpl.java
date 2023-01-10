package me.lucasskywalker.apis;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.events.ChannelGoLiveEvent;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TwitchImpl {

    private final List<String> channel = new ArrayList<>();

    private final List<String> message = new ArrayList<>();

    private final List<String> username = new ArrayList<>();
    private final List<String> role = new ArrayList<>();

    private final TwitchClient twitchClient;


    public void updateLists() {
        try {
            channel.clear();
            message.clear();
            username.clear();
            role.clear();

            FileReader fileReader = new FileReader(new File(YoutubeImpl.class.getProtectionDomain()
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

            twitchClient.getClientHelper().disableStreamEventListener(username);
            twitchClient.getClientHelper().enableStreamEventListener(username);
        } catch (IOException | URISyntaxException e) {
            System.out.println(e.getMessage());
        }
    }

    public TwitchImpl(JDA discordAPI) {
        System.out.println("Starting twitch api...");

        twitchClient = TwitchClientBuilder.builder()
                .withClientId(BotMain.getConfig().get("TWITCH_CLIENT_ID"))
                .withClientSecret(BotMain.getConfig().get("TWITCH_CLIENT_SECRET"))
                .withEnableHelix(true)
                .build();

        updateLists();

        twitchClient.getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
            int index = username.indexOf(event.getChannel().getName());

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.addField("Game", event.getStream().getGameName(), false);
            embedBuilder.setAuthor(username.get(index) + " is now streaming", "https://twitch.tv/"
                    + username.get(index), twitchClient.getHelix().getUsers(null,
                    Collections.singletonList(event.getChannel().getId()),
                    Collections.singletonList(event.getChannel().getName())).execute().getUsers().get(0)
                    .getProfileImageUrl());
            embedBuilder.setTitle(event.getStream().getTitle(), "https://twitch.tv/" + username.get(index));
            embedBuilder.setImage(event.getStream().getThumbnailUrl(1920, 1080));
            embedBuilder.setFooter(discordAPI.getSelfUser().getName());
            embedBuilder.setTimestamp(Instant.now());

            String messagePart2 = message.get(index).substring(message.get(index).indexOf("\\n") + 2).strip();
            String message = this.message.get(index).substring(0, this.message.get(index).lastIndexOf("\\n") + 2)
                    .replace("\\n", "\n")
                    + messagePart2;

            if(!role.get(index).isBlank())
                discordAPI.getTextChannelById(channel.get(index)).sendMessage(
                    discordAPI.getRoleById(role.get(index)).getAsMention() + " " + message)
                    .addEmbeds(embedBuilder.build())
                        .addActionRow(Button.link("https://twitch.tv/" + username.get(index), "Watch Stream"))
                        .queue();
            else discordAPI.getTextChannelById(channel.get(index)).sendMessage(message)
                    .addEmbeds(embedBuilder.build()).addEmbeds(embedBuilder.build())
                    .addActionRow(Button.link("https://twitch.tv/" + username.get(index), "Watch Stream"))
                    .queue();
        });
    }
}
