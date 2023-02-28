package me.lucasskywalker;

import io.github.cdimascio.dotenv.Dotenv;
import me.lucasskywalker.apis.TwitchImpl;
import me.lucasskywalker.apis.TwitterImpl;
import me.lucasskywalker.apis.YoutubeImpl;
import me.lucasskywalker.commands.SlashCommandManager;
import me.lucasskywalker.listeners.ReactionRoleManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BotMain {

    private static final Dotenv CONFIG = Dotenv.configure().load();

    public static Dotenv getConfig() { return CONFIG; }

    private static JDA discordAPI;

    public static TwitchImpl twitch;

    public static TwitterImpl twitter;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static String channel;

    private static List<Member> memberList;

    public static void scheduleUpdateMemberCount() {
        try {
            File filePath = new File(new File(BotMain.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/membercount.txt");
            FileReader fileReader = new FileReader(filePath);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            channel = bufferedReader.readLine();
            bufferedReader.close();
            fileReader.close();
        } catch (URISyntaxException | IOException e) {
            return;
        }
        scheduler.scheduleAtFixedRate(BotMain::updateMemberCount, 0, 1, TimeUnit.HOURS);
    }

    private static void updateMemberCount() {
        discordAPI.getVoiceChannelById(channel).getManager().setName("Member count: " +
                memberList.size()).queue();
    }

    private static boolean init() throws InvalidTokenException, URISyntaxException {
        discordAPI = JDABuilder.createDefault(CONFIG.get("BOT_TOKEN"))
                .enableIntents(GatewayIntent.GUILD_EMOJIS_AND_STICKERS, GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .build();
        discordAPI.addEventListener(new ReactionRoleManager(), new SlashCommandManager());
        File filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                + "/bot_files");
        return filePath.exists() || filePath.mkdir();
    }

    public static void main(String[] args) throws InterruptedException {

        try {
            if(init())
                discordAPI.awaitReady();
            else {
                System.out.println("Unexpected folder error aborting startup");
                return;
            }
        } catch (InvalidTokenException e) {
            System.out.println("Invalid bot token!");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        memberList = discordAPI.getGuilds().get(0).loadMembers().get();

        scheduleUpdateMemberCount();

        new YoutubeImpl(discordAPI);
        twitch = new TwitchImpl(discordAPI);

        //twitter = new TwitterImpl(discordAPI);
    }
}
