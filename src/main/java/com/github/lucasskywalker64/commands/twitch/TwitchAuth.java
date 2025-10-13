package com.github.lucasskywalker64.commands.twitch;

import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.tinylog.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

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
        TwitchOAuthService.AuthLink link;
        try {
            if (TwitchRepository.getInstance().loadToken() != null) {
                List<Command> commands = event.getGuild().retrieveCommands().complete();
                Command twitchCommand = commands.stream()
                        .filter(command -> command.getName().equals("twitch"))
                        .findFirst()
                        .get();
                event.getHook().sendMessage(String.format("Twitch is already authorized, if you need to re-authorize " +
                        "please revoke it first with </twitch revoke:%s", twitchCommand.getId())).queue();
                return;
            }
            link = oauth.createAuthorizationLink(event.getUser().getIdLong());
        } catch (IOException | SQLException e) {
            Logger.error(e);
            event.getHook().sendMessage(INTERNAL_ERROR).queue();
            return;
        }

        event.getHook().sendMessage("Click the button below to authorize this bot to access your Twitch account")
                .addActionRow(Button.link(link.url(), "Authorize on Twitch"))
                .queue();
    }
}
