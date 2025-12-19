package com.github.lucasskywalker64;

import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService;
import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.ticket.TicketModule;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;

import java.util.Map;
import java.util.concurrent.*;

public class BotContext {

    private final JDA jda;
    private final Dotenv config;
    private final Map<String, CompletableFuture<Integer>> pendingChallenges;
    private final ExecutorService taskExecutor;
    private TwitchImpl twitch;
    private CompletableFuture<TwitchImpl> twitchFuture;
    private TicketModule ticketModule;
    private TwitchOAuthService twitchOAuthService;
    private YouTubeImpl youTube;

    public BotContext(JDA jda, Dotenv config, TwitchImpl twitch) {
        this.jda = jda;
        this.config = config;
        this.twitch = twitch;
        pendingChallenges = new ConcurrentHashMap<>();
        taskExecutor = Executors.newCachedThreadPool();
    }

    public JDA jda() {
        return jda;
    }

    public Dotenv config() {
        return config;
    }

    public Map<String, CompletableFuture<Integer>> pendingChallenges() {
        return pendingChallenges;
    }

    public ExecutorService taskExecutor() {
        return taskExecutor;
    }

    public TwitchImpl twitch() {
        return twitch;
    }

    public CompletableFuture<TwitchImpl> twitchFuture() {
        return twitchFuture;
    }

    public TicketModule ticketModule() {
        return ticketModule;
    }

    public TwitchOAuthService twitchOauthService() {
        return twitchOAuthService;
    }

    public YouTubeImpl youTube() {
        return youTube;
    }

    public void setTwitch(TwitchImpl twitch) {
        this.twitch = twitch;
    }

    public void setTwitchFuture(CompletableFuture<TwitchImpl> twitchFuture) {
        this.twitchFuture = twitchFuture;
    }

    public void setTicketModule(TicketModule ticketModule) {
        this.ticketModule = ticketModule;
    }

    public void setTwitchOAuthService(TwitchOAuthService twitchOAuthService) {
        this.twitchOAuthService = twitchOAuthService;
    }

    public void setYouTube(YouTubeImpl youTube) {
        this.youTube = youTube;
    }
}
