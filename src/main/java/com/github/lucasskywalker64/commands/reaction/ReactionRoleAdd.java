package com.github.lucasskywalker64.commands.reaction;

import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.listener.role.ReactionRoleListener;
import com.github.lucasskywalker64.persistence.data.ReactionRoleData;
import com.github.lucasskywalker64.persistence.repository.ReactionRoleRepository;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;

@SuppressWarnings("DataFlowIssue")
public class ReactionRoleAdd implements SubcommandModule {

    private final ReactionRoleRepository repository = ReactionRoleRepository.getInstance();
    private final ReactionRoleListener listener;

    public ReactionRoleAdd(ReactionRoleListener listener) {
        this.listener = listener;
    }

    @Override public String getRootName() { return "reactionrole"; }
    @Override public String getSubcommandName() { return "add"; }
    @Override public String getDescription() { return "Add a reaction role."; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(CHANNEL, "channel", "Channel containing the message.", true)
                .addOption(STRING, "messageid", "The ID of the message.", true)
                .addOption(ROLE, "role", "The role that this reaction should give.", true)
                .addOption(STRING, "emoji", "The emoji that should be used.", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        MessageChannel channel = event.getChannel().asGuildMessageChannel();
        String messageId = event.getOption("messageid").getAsString();
        Role role = event.getOption("role").getAsRole();
        Emoji emoji = Emoji.fromFormatted(event.getOption("emoji").getAsString());

        if (!CommandUtil.validateEmojis(event.getGuild(), Collections.singletonList(emoji))) {
            event.getHook().sendMessage("ERROR: Emoji must be unicode or a custom emoji from this server.")
                    .queue();
            return;
        }

        var existingIds = repository.loadAll().stream().map(ReactionRoleData::roleId).toList();
        if (existingIds.contains(role.getId())) {
            event.getHook().sendMessage("This role is already configured as a reaction role.").queue();
            return;
        }

        channel.addReactionById(messageId, emoji).complete();
        try {
            repository.saveAll(List.of(new ReactionRoleData(
                    channel.getId(), messageId, role.getId(), role.getName(), emoji.getFormatted()
            )));
        } catch (IOException e) {
            Logger.error(e);
            channel.removeReactionById(messageId, emoji).queue();
            event.getHook().sendMessage("Something went wrong while saving the reaction role " +
                    "and changes have been reverted. Please contact the developer.").queue();
            return;
        }
        listener.load();
        event.getHook().sendMessage("Reaction role added.").queue();
    }
}
