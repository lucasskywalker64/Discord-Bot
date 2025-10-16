package com.github.lucasskywalker64;

import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService;
import com.github.lucasskywalker64.ticket.TicketModule;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;

import java.util.concurrent.CompletableFuture;

public class BotContext {

    private final JDA jda;
    private final Dotenv config;
    private TwitchImpl twitch;
    private CompletableFuture<TwitchImpl> twitchFuture;
    private TicketModule ticketModule;
    private TwitchOAuthService twitchOAuthService;

    public BotContext(JDA jda, Dotenv config, TwitchImpl twitch) {
        this.jda = jda;
        this.config = config;
        this.twitch = twitch;
    }

    public JDA jda() {
        return jda;
    }

    public Dotenv config() {
        return config;
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
}
