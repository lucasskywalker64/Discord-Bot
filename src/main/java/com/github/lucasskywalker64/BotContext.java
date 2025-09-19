package com.github.lucasskywalker64;

import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class BotContext {

    private final JDA jda;
    private final Dotenv config;
    private final File botFile;
    private TwitchImpl twitch;
    private CompletableFuture<TwitchImpl> twitchFuture;

    public BotContext(JDA jda, Dotenv config, File botFile, TwitchImpl twitch) {
        this.jda = jda;
        this.config = config;
        this.botFile = botFile;
        this.twitch = twitch;
    }

    public JDA jda() {
        return jda;
    }

    public Dotenv config() {
        return config;
    }

    public File botFile() {
        return botFile;
    }

    public TwitchImpl twitch() {
        return twitch;
    }

    public CompletableFuture<TwitchImpl> twitchFuture() {
        return twitchFuture;
    }

    public void setTwitch(TwitchImpl twitch) {
        this.twitch = twitch;
    }

    public void setTwitchFuture(CompletableFuture<TwitchImpl> twitchFuture) {
        this.twitchFuture = twitchFuture;
    }
}
