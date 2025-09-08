package com.github.lucasskywalker64.commands.shoutout;

import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.ShoutoutData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

public class ShoutoutDisplay implements SubcommandModule {

    private final TwitchRepository repo = TwitchRepository.getInstance();

    @Override public String getRootName() { return "shoutout"; }
    @Override public String getSubcommandName() { return "display"; }
    @Override public String getDescription() { return "Displays all usernames from the shoutout list"; }

    @Override public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        List<ShoutoutData> data = repo.loadAllShoutout();
        if (data.isEmpty()) {
            event.reply("No shoutout notifications.").setEphemeral(true).queue();
            return;
        }
        StringBuilder msg = new StringBuilder();
        for (ShoutoutData s : data) msg.append(s.username()).append("\n");
        event.reply(msg.toString()).setEphemeral(true).queue();
    }
}
