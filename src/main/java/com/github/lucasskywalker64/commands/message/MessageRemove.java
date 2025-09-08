package com.github.lucasskywalker64.commands.message;

import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.listener.role.ReactionRoleListener;
import com.github.lucasskywalker64.persistence.data.ReactionRoleData;
import com.github.lucasskywalker64.persistence.repository.ReactionRoleRepository;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("DataFlowIssue")
public class MessageRemove implements SubcommandModule {

    private final ReactionRoleRepository reactionRepo = ReactionRoleRepository.getInstance();
    private final ReactionRoleListener reactionListener;

    public MessageRemove(ReactionRoleListener listener) {
        this.reactionListener = listener;
    }

    @Override public String getRootName() { return "message"; }
    @Override public String getSubcommandName() { return "remove"; }
    @Override public String getDescription() { return "Remove a message and any associated reaction roles"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.CHANNEL, "channel", "Channel that the message is in.", true)
                .addOption(OptionType.STRING, "messageid", "The ID of the message to remove.", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        try {
            StandardGuildMessageChannel channel = event.getOption("channel").getAsChannel().asStandardGuildMessageChannel();
            String messageId = event.getOption("messageid").getAsString();

            List<ReactionRoleData> all = reactionRepo.loadAll();
            List<ReactionRoleData> toRemove = all.stream().filter(d -> d.messageId().equals(messageId)).toList();
            if (!toRemove.isEmpty()) {
                all.removeAll(toRemove);
                reactionRepo.saveAll(all, false);
                reactionListener.load();
            }
            channel.deleteMessageById(messageId).complete();
            event.reply("Message removed.").setEphemeral(true).queue();
        } catch (RuntimeException | IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to remove message. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }
}
