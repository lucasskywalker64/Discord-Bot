package com.github.lucasskywalker64.commands.general;

import com.github.lucasskywalker64.commands.SubcommandModule;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public class GeneralPing implements SubcommandModule {

    @Override public String getRootName() { return "general"; }
    @Override public String getSubcommandName() { return "ping"; }
    @Override public String getDescription() { return "Simple ping to check if the bot is responding"; }

    @Override public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long rest = event.getJDA().getRestPing().complete();
        long gw = event.getJDA().getGatewayPing();
        event.replyFormat("Rest ping: %d\nGateway ping: %d", rest, gw).setEphemeral(true).queue();
    }
}
