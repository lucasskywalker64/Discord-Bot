package com.github.lucasskywalker64.commands.reaction;

import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.listener.role.ReactionRoleListener;
import com.github.lucasskywalker64.persistence.repository.ReactionRoleRepository;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

@SuppressWarnings("DataFlowIssue")
public class ReactionRoleRemove implements SubcommandModule {

    private final ReactionRoleRepository repository = ReactionRoleRepository.getInstance();
    private final ReactionRoleListener listener;

    public ReactionRoleRemove(ReactionRoleListener listener) {
        this.listener = listener;
    }

    @Override public String getRootName() { return "reactionrole"; }
    @Override public String getSubcommandName() { return "remove"; }
    @Override public String getDescription() { return "Remove a reaction role"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.ROLE, "role", "Role to remove", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        var reactionRoleDataList = repository.loadAll();
        var toBeRemoved = reactionRoleDataList.stream()
                .filter(data -> data.roleId().equals(event.getOption("role").getAsRole().getId()))
                .findFirst();
        if (toBeRemoved.isEmpty()) {
            event.getHook().sendMessage(String.format("The role %s is not in the list.",
                    event.getOption("role").getAsRole().getName())).queue();
            return;
        }
        reactionRoleDataList.remove(toBeRemoved.get());
        event.getGuild().getChannelById(StandardGuildMessageChannel.class, toBeRemoved.get().channelId()).removeReactionById(
                        toBeRemoved.get().messageId(), Emoji.fromFormatted(toBeRemoved.get().emoji()))
                .complete();
        try {
            repository.saveAll(reactionRoleDataList, false);
        } catch (Exception e) {
            Logger.error(e);
            event.getHook().sendMessage("Something went wrong while saving the reaction role " +
                    "and changes have been reverted. Please contact the developer.").queue();
        }
        listener.load();
        event.getHook().sendMessage(String.format("The role %s has been removed.",
                event.getOption("role").getAsRole().getName())).queue();
    }
}
