package com.github.lucasskywalker64.commands.twitch;

import com.github.lucasskywalker64.BotContext;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.SubcommandModule;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class TwitchAuthStreamer implements SubcommandModule {

    private final BotContext context;

    public TwitchAuthStreamer() {
        context = BotMain.getContext();
    }

    @Override public String getRootName() { return "twitch"; }
    @Override public String getSubcommandName() { return "auth-streamer"; }
    @Override public String getDescription() { return "Authorize this bot to access your twitch account"; }

    @Override
    public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String link = context.twitchOauthService().getAuthorizationLink(true);
        event.reply("Click the button below to authorize this bot to access your twitch account")
                .addActionRow(Button.link(link, "Authorize on Twitch"))
                .setEphemeral(true)
                .queue();
    }
}
