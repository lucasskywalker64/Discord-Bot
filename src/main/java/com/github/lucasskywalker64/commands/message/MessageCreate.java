package com.github.lucasskywalker64.commands.message;

import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.listener.role.ReactionRoleListener;
import com.github.lucasskywalker64.persistence.data.ReactionRoleData;
import com.github.lucasskywalker64.persistence.repository.ReactionRoleRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.apache.commons.collections4.CollectionUtils;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("DataFlowIssue")
public class MessageCreate implements SubcommandModule {

    private final ReactionRoleRepository reactionRepo = ReactionRoleRepository.getInstance();
    private final ReactionRoleListener reactionListener;

    public MessageCreate(ReactionRoleListener listener) {
        this.reactionListener = listener;
    }

    @Override public String getRootName() { return "message"; }
    @Override public String getSubcommandName() { return "create"; }
    @Override public String getDescription() { return "Create a new embed/message with optional reaction roles"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Target channel", true)
                        .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD))
                .addOption(OptionType.STRING, "message", "Text content. Use \\n for newline.", true)
                .addOption(OptionType.BOOLEAN, "embed", "Whether to send as embed", true)
                .addOption(OptionType.STRING, "title", "Embed title")
                .addOption(OptionType.STRING, "roles", "Roles in same order as emojis")
                .addOption(OptionType.STRING, "emojis", "Emojis separated by ; in same order as roles")
                .addOption(OptionType.STRING, "image", "Optional image url for embed");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        MessageChannel channel = null;
        String messageId = "";
        try {
            List<Role> roleList = new ArrayList<>();
            List<Emoji> emojiList = new ArrayList<>();

            if (event.getOption("roles") != null && event.getOption("emojis") != null) {
                roleList = event.getOption("roles").getMentions().getRoles();
                emojiList = Arrays.stream(event.getOption("emojis").getAsString()
                                .split(";"))
                        .map(s -> Emoji.fromFormatted(s.strip())).collect(Collectors.toList());
                if (roleList.size() != emojiList.size()) {
                    event.reply("Amount of roles and emojis don't match! Command has been canceled.")
                            .setEphemeral(true).queue();
                    return;
                }
                if (rolesExist(roleList)) {
                    event.reply("One or more roles already exist.").setEphemeral(true).queue();
                    return;
                }
            }

            if (!event.getOption("roles").getMentions().getUsers().isEmpty()) {
                event.reply("The list of roles contains users. Please only input valid roles.")
                        .setEphemeral(true).queue();
                return;
            }
            if (!CommandUtil.validateEmojis(event.getGuild(), emojiList)) {
                event.reply("One or more emojis are invalid. Please only use either default emojis or custom emojis from this server.")
                        .setEphemeral(true).queue();
                return;
            }

            // Build content
            EmbedBuilder embedBuilder = new EmbedBuilder();
            if (event.getOption("image") != null)
                embedBuilder.setThumbnail(event.getOption("image").getAsString());
            if (event.getOption("title") != null)
                embedBuilder.setTitle(event.getOption("title").getAsString());
            String msg = event.getOption("message").getAsString()
                    .replace("\\n", "\n");
            embedBuilder.setDescription(msg);

            channel = event.getGuild().getChannelById(GuildMessageChannel.class, event.getOption("channel")
                    .getAsChannel().getId());

            String roleReaction = !roleList.isEmpty() ? " with role reaction." : " without role reaction.";
            if (event.getOption("embed").getAsBoolean()) {
                messageId = channel.sendMessageEmbeds(embedBuilder.build()).complete().getId();
                event.replyFormat("Embed created in %s", channel.getAsMention() + roleReaction)
                        .setEphemeral(true).queue();
            } else {
                messageId = channel.sendMessage(msg).complete().getId();
                event.replyFormat("Message sent in %s", channel.getAsMention() + roleReaction)
                        .setEphemeral(true).queue();
            }

            if (!roleList.isEmpty())
                handleReactionRoles(messageId, roleList, emojiList, channel);
        } catch (Exception e) {
            Logger.error(e);
            if (!messageId.isEmpty())
                channel.deleteMessageById(messageId).complete();
            event.reply("Something went wrong and changes were reverted. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleReactionRoles(String messageId, List<Role> roleList, List<Emoji> emojiList,
                                        MessageChannel channel) throws IOException {
        List<ReactionRoleData> newRoleData = new ArrayList<>();
        for (int i = 0; i < roleList.size(); i++) {
            channel.addReactionById(messageId, emojiList.get(i)).complete();
            newRoleData.add(new ReactionRoleData(
                    channel.getId(),
                    messageId,
                    roleList.get(i).getId(),
                    roleList.get(i).getName(),
                    emojiList.get(i).getFormatted()));
        }
        reactionRepo.saveAll(newRoleData);
        reactionListener.load();
    }

    private boolean rolesExist(List<Role> roleList) {
        List<String> oldRoleIds = reactionRepo.loadAll().stream().map(ReactionRoleData::roleId).toList();
        List<String> newRoleIds = roleList.stream().map(Role::getId).toList();
        return !CollectionUtils.intersection(oldRoleIds, newRoleIds).isEmpty();
    }
}
