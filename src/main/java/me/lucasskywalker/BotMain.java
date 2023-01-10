package me.lucasskywalker;

import io.github.cdimascio.dotenv.Dotenv;
import me.lucasskywalker.apis.TwitchImpl;
import me.lucasskywalker.apis.YoutubeImpl;
import me.lucasskywalker.commands.SlashCommandManager;
import me.lucasskywalker.listeners.ReactionRoleManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.File;
import java.net.URISyntaxException;

public class BotMain {

    private static final Dotenv CONFIG = Dotenv.configure().load();

    public static Dotenv getConfig() { return CONFIG; }

    private static JDA discordAPI;

    public static TwitchImpl twitch;

    private static boolean init() throws InvalidTokenException, URISyntaxException {
        discordAPI = JDABuilder.createDefault(CONFIG.get("BOT_TOKEN"))
                .enableIntents(GatewayIntent.GUILD_EMOJIS_AND_STICKERS)
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

        new YoutubeImpl(discordAPI);
        twitch = new TwitchImpl(discordAPI);

        //me.lucasskywalker.apis.TwitterImpl twitter = new me.lucasskywalker.apis.TwitterImpl();
        //twitter.getLatestTweet();
    }
}
