package com.github.lucasskywalker64.commands.shoutout;

import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.ArrayList;

@SuppressWarnings("DataFlowIssue")
public class ShoutoutRemoveAll implements SubcommandModule {

    private final TwitchRepository repo = TwitchRepository.getInstance();
    private final TwitchImpl twitch;

    public ShoutoutRemoveAll(TwitchImpl twitch) { this.twitch = twitch; }

    @Override public String getRootName() { return "shoutout"; }
    @Override public String getSubcommandName() { return "removeall"; }
    @Override public String getDescription() { return "Removes all users from the shoutout list"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.STRING, "are-you-sure", "Type yes to confirm the removal of all shoutouts.", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (!"yes".equals(event.getOption("are-you-sure").getAsString())) {
            event.reply("Please confirm that you want to remove all shoutouts.").setEphemeral(true).queue();
            return;
        }
        try {
            repo.saveAllShoutout(new ArrayList<>(), false);
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to remove shoutouts! Please contact the developer.")
                    .setEphemeral(true).queue();
        }
        twitch.load();
        event.reply("All shoutouts removed.").setEphemeral(true).queue();
    }
}
