package me.lucasskywalker;

import io.github.cdimascio.dotenv.Dotenv;
import me.lucasskywalker.commands.SlashCommandManager;
import me.lucasskywalker.listeners.ReactionRoleManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class BotMain {

    private static final Dotenv CONFIG = Dotenv.configure().load();

    public static Dotenv getConfig() { return CONFIG; }

    private static JDA bot;

    public BotMain() throws InvalidTokenException {
        bot = JDABuilder.createDefault(CONFIG.get("BOT_TOKEN"))
                .setActivity(Activity.streaming("test","https://www.twitch.tv/elinovavt"))
                .enableIntents(GatewayIntent.GUILD_EMOJIS_AND_STICKERS)
                .build();
        bot.addEventListener(new ReactionRoleManager(), new SlashCommandManager());
    }

    public static void main(String[] args) throws InterruptedException {

        try {
            new BotMain();
            bot.awaitReady();
        } catch (InvalidTokenException e) {
            System.out.println("Invalid bot token!");
        }
        //me.lucasskywalker.TwitterImpl twitter = new me.lucasskywalker.TwitterImpl();
        //twitter.getLatestTweet();
    }
}
