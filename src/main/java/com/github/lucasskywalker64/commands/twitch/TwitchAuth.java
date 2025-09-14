package com.github.lucasskywalker64.commands.twitch;

import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService;
import com.github.lucasskywalker64.commands.SubcommandModule;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.tinylog.Logger;

import java.io.IOException;

public class TwitchAuth implements SubcommandModule {

    private final TwitchOAuthService oauth;

    public TwitchAuth(TwitchOAuthService oauth) {
        this.oauth = oauth;
    }

    @Override
    public String getRootName() { return "twitch"; }
    @Override
    public String getSubcommandName() { return "auth"; }
    @Override
    public String getDescription() { return "Authorize this bot to access your Twitch stream"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription());
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        TwitchOAuthService.AuthLink link = null;
        try {
            link = oauth.createAuthorizationLink(event.getUser().getIdLong());
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("Internal server error. Please try again. " +
                            "If this error persists contact the developer.").queue();
            return;
        }

        event.getHook().sendMessage("Click the button below to authorize this bot to access your Twitch account")
                .addActionRow(Button.link(link.url(), "Authorize on Twitch"))
                .queue();
    }
}
