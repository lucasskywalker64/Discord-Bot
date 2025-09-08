package com.github.lucasskywalker64.commands.notif.twitch;

import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

import static com.github.lucasskywalker64.commands.CommandUtil.addFieldSafe;

public class NotifTwitchDisplay implements SubcommandModule {

    private final TwitchRepository repository = TwitchRepository.getInstance();

    @Override public String getRootName() { return "notif"; }
    @Override public String getGroupName() { return "twitch"; }
    @Override public String getSubcommandName() { return "display"; }
    @Override public String getDescription() { return "Displays all Twitch notifications"; }

    @Override public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        List<TwitchData> twitchDataList = repository.loadAll();
        if (twitchDataList.isEmpty()) {
            event.reply("No Twitch notifications.").setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Twitch notifications");
        addFieldSafe(embed, "Username", twitchDataList.stream().map(TwitchData::username).toList(), true);
        addFieldSafe(embed, "Message", twitchDataList.stream().map(TwitchData::message).toList(), true);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
