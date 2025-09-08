package com.github.lucasskywalker64.commands.reaction;

import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.ReactionRoleData;
import com.github.lucasskywalker64.persistence.repository.ReactionRoleRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

import static com.github.lucasskywalker64.commands.CommandUtil.addFieldSafe;

public class ReactionRoleDisplay implements SubcommandModule {

    private final ReactionRoleRepository repository = ReactionRoleRepository.getInstance();

    @Override public String getRootName() { return "reactionrole"; }
    @Override public String getSubcommandName() { return "display"; }
    @Override public String getDescription() { return "Display all reaction roles."; }

    @Override public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        List<ReactionRoleData> reactionRoleDataList = repository.loadAll();
        if (reactionRoleDataList.isEmpty()) {
            event.reply("No reaction roles.").setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder();
        addFieldSafe(embed, "Role Name", reactionRoleDataList.stream()
                .map(ReactionRoleData::roleName)
                .toList(), true);
        addFieldSafe(embed, "Emoji", reactionRoleDataList.stream()
                .map(ReactionRoleData::emoji)
                .toList(), true);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
