package com.github.lucasskywalker64.commands.twitch;

import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.TokenData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

public class TwitchRevoke implements SubcommandModule {

    private final TwitchRepository repository = TwitchRepository.getInstance();
    private final TwitchOAuthService oAuthService;

    public TwitchRevoke(TwitchOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    @Override
    public String getRootName() { return "twitch"; }

    @Override
    public String getSubcommandName() { return "revoke"; }

    @Override
    public String getDescription() { return "Revoke the twitch authentication for this bot"; }

    @Override
    public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription())
            .addOption(OptionType.BOOLEAN, "are-you-sure",
                    "Select true to confirm that you want to revoke this authentication.", true); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        if (!event.getOption("are-you-sure").getAsBoolean()) {
            event.getHook().sendMessage("Please confirm that you want to revoke this authentication").queue();
            return;
        }
        try {
            if (repository.loadToken() == null) {
                event.getHook().sendMessage("No token registered").queue();
                return;
            }
            TokenData tokenData = repository.loadToken();
            String token = tokenData.bundle().accessToken();
            oAuthService.revokeToken(token);
            event.getHook().sendMessage("Token revoked").queue();
        } catch (Exception e) {
            Logger.error(e);
            event.getHook().sendMessage("Internal server error. Please try again. " +
                    "If this error persists contact the developer.").queue();
        }
    }
}
