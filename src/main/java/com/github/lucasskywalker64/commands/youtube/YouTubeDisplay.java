package com.github.lucasskywalker64.commands.youtube;

import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.sql.SQLException;
import java.util.List;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;
import static com.github.lucasskywalker64.commands.CommandUtil.addFieldSafe;

public class YouTubeDisplay implements SubcommandModule {

    private final YouTubeRepository repo = YouTubeRepository.getInstance();

    @Override public String getRootName() { return "youtube"; }
    @Override public String getSubcommandName() { return "display"; }
    @Override public String getDescription() { return "Displays all YouTube notifications"; }

    @Override public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        List<YouTubeData> list;
        try {
            list = repo.loadAll();
        } catch (SQLException e) {
            Logger.error(e.getMessage());
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }
        if (list.isEmpty()) {
            event.reply("No YouTube notifications").setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("YouTube Notifications");
        addFieldSafe(embed, "Username", list.stream().map(YouTubeData::name).toList(), true);
        addFieldSafe(embed, "Message", list.stream().map(YouTubeData::message).toList(), true);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
