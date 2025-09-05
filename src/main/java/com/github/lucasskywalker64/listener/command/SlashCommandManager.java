package com.github.lucasskywalker64.listener.command;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.persistence.data.ShoutoutData;
import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.persistence.PersistenceUtil;
import com.github.lucasskywalker64.persistence.data.ReactionRoleData;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import com.github.lucasskywalker64.api.twitch.TwitchImpl;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.exceptions.InvalidParameterException;
import com.github.lucasskywalker64.listener.role.ReactionRoleManager;
import com.github.lucasskywalker64.persistence.repository.ReactionRoleRepository;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.Emoji.Type;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;

@SuppressWarnings({"java:S1192", "DataFlowIssue"})
public class SlashCommandManager extends ListenerAdapter {

    private static final TwitchImpl twitch = BotMain.getTwitch();
    private static final TwitchRepository twitchRepo = TwitchRepository.getInstance();
    private static final YouTubeImpl youTube = BotMain.getYouTube();
    private static final YouTubeRepository youTubeRepo = YouTubeRepository.getInstance();
    private static final ReactionRoleManager reactionRoleManager = BotMain.getReactionRoleManager();
    private static final ReactionRoleRepository reactionRoleRepo = ReactionRoleRepository.getInstance();
    private static final File memberCountFile = BotMain.getMemberCountFile();
    private static final Map<String, String> categories = new HashMap<>();

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            /*
            Displays a list of commands.
            Command: /help
             */
            case "help" -> help(event);

            /*
            Add a reaction to a message which can be used by members to assign a role to themselves.
            Command: /addreactionrole <channel> <messageid> <role> <emoji>
             */
            case "addreactionrole" -> addReactionRole(event);

            /*
            Display all reaction roles.
            Command: /displayreactionroles
             */
            case "displayreactionroles" -> displayReactionRoles(event);

            /*
            Remove reaction role from a message.
            Command: /removereactionrole <channel> <messageid> <role>
             */
            case "removereactionrole" -> removeReactionRole(event);

            /*
            Create a new embed or message in the given channel with optional roles and emojis to add reaction roles.
            Command: /createmessage <channel, message, embed:true/false> [title, roles, emojis, image]
             */
            case "createmessage" -> createMessage(event);

            /*
            Edit message command.
            Command: /editmessage <channel, messageid, embed:true/false> [message, title, image]
             */
            case "editmessage" -> editMessage(event);

            /*
            Removes a message, if this message had any reaction roles they will be deleted as well.
            Command: /removemessage <channel, messageid>
             */
            case "removemessage" -> removeMessage(event);

            /*
            Add a YouTube notification output to a specific channel.
            Command: /addyoutubenotif <channel, message, name, role>
             */
            case "addyoutubenotif" -> addYoutubeNotification(event);

            /*
            Edit a YouTube notification
            Command: /edityoutubenotif <name> [message, role]
             */
            case "edityoutubenotif" -> editYoutubeNotification(event);

            /*
            Remove a YouTube notification
            Command: /removeyoutubenotif <name>
             */
            case "removeyoutubenotif" -> removeYoutubeNotification(event);

            /*
            Display all YouTube notifications
            Command: /displayyoutubenotif
             */
            case "displayyoutubenotif" -> displayYouTubeNotification(event);

            /*
            Add a Twitch notification output to a specific channel
            Command: /addtwitchnotif <channel, message, username, role>
             */
            case "addtwitchnotif" -> addTwitchNotification(event);

            /*
            Edit a Twitch notification
            Command: /edittwitchnotif <username> [message, role]
             */
            case "edittwitchnotif" -> editTwitchNotification(event);

            /*
            Remove a Twitch notification
            Command: /removetwitchnotif <username>
             */
            case "removetwitchnotif" -> removeTwitchNotification(event);

            /*
            Display all Twitch notifications
            Command: /displaytwitchnotif
             */
            case "displaytwitchnotif" -> displayTwitchNotification(event);

            /*
            Add users to be shouted out on Twitch
            Command: /addshoutout <username>
             */
            case "addshoutout" -> addShoutout(event);

            /*
            Displays all usernames from the shoutout list
            Command: /displayshoutout
             */
            case "displayshoutout" -> displayShoutout(event);

            /*
            Removes a user from the shoutout list
            Command: /removeshoutout <username>
             */
            case "removeshoutout" -> removeShoutout(event);

            /*
            Removes all shoutouts from the list
            Command: /removeallshoutouts <yes>
             */
            case "removeallshoutouts" -> removeAllShoutout(event);

            /*
            Add a member count
            Command: /addmembercount <channel>
             */
            case "addmembercount" -> addMemberCount(event);

            /*
            Remove the member count
            Command: /removemembercount
             */
            case "removemembercount" -> removeMemberCount(event);

            /*
            Simple ping to check if the bot is responding
            Command: /ping
             */
            case "ping" -> ping(event);

            default -> {
                Logger.error("No matching case found for {}", event.getName());
                event.reply("Something went wrong. Please contact the developer.").setEphemeral(true).queue();
            }
        }
    }

    private void help(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        List<Command> commands = event.getGuild().retrieveCommands().complete();

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Command command : commands) {
            String category = categories.getOrDefault(command.getName(), "Other");
            grouped.computeIfAbsent(category, k -> new ArrayList<>())
                    .add("</" + command.getName() + ":" + command.getId() + ">" +
                            (command.getDescription().isEmpty()
                                    ? ""
                                    : " : " + command.getDescription()));
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Help");
        embed.setColor(Color.GREEN);

        for (Entry<String, List<String>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<String> lines = entry.getValue();
            addFieldSafe(embed, category, lines, false);
        }

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void addReactionRole(SlashCommandInteractionEvent event) {
        try {
            event.deferReply(true).queue();
            if (!handleReactionRoles(
                    event.getOption("messageid").getAsString(),
                    Collections.singletonList(event.getOption("role").getAsRole()),
                    Collections.singletonList(Emoji.fromFormatted(event.getOption("emoji").getAsString())),
                    event.getOption("channel").getAsChannel().asStandardGuildMessageChannel()))
                event.getHook().sendMessage("One or more roles are already in the list.").queue();
            else
                event.getHook().sendMessage("Reaction role has been added.").queue();
        } catch (ErrorResponseException e) {
            if (e.getErrorCode() == 10014) {
                Logger.error(e);
                event.getHook().sendMessage("ERROR: Unknown Emoji. Can only use emojis that are either " +
                        "default or have been added to the server!").queue();
            } else {
                Logger.error(e);
                event.getHook().sendMessage("ERROR: Unknown message. Make sure the message is actually " +
                        "in the provided channel.").queue();
            }
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to add reaction role. Please contact the developer.")
                    .queue();
        }
    }

    private void displayReactionRoles(SlashCommandInteractionEvent event) {
        List<ReactionRoleData> reactionRoleDataList = reactionRoleRepo.loadAll();
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

    private void removeReactionRole(SlashCommandInteractionEvent event) {
        List<ReactionRoleData> reactionRoleDataList = reactionRoleRepo.loadAll();
        Optional<ReactionRoleData> toBeRemoved = reactionRoleDataList.stream()
                .filter(data -> data.roleId().equals(event.getOption("role").getAsRole().getId()))
                .findFirst();
        if (toBeRemoved.isEmpty()) {
            event.replyFormat("The role %s is not in the list.", event.getOption("role").getAsRole().getName())
                    .setEphemeral(true).queue();
            return;
        }
        reactionRoleDataList.remove(toBeRemoved.get());
        try {
            event.getGuild().getChannelById(StandardGuildMessageChannel.class, toBeRemoved.get().channelId()).removeReactionById(
                            toBeRemoved.get().messageId(), Emoji.fromFormatted(toBeRemoved.get().emoji()))
                    .complete();
            reactionRoleRepo.saveAll(reactionRoleDataList, false);
            event.replyFormat("The role %s has been removed.", event.getOption("role").getAsRole().getName())
                    .setEphemeral(true).queue();
        } catch (Exception e) {
            Logger.error(e);
            event.reply("ERROR: Failed to remove role reaction. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void createMessage(SlashCommandInteractionEvent event) {
        List<Role> roleList = new ArrayList<>();
        List<Emoji> emojiList = new ArrayList<>();

        if (event.getOption("roles") != null && event.getOption("emojis") != null) {
            roleList = event.getOption("roles").getMentions().getRoles();
            emojiList = Arrays.stream(event.getOption("emojis").getAsString().split(";"))
                    .map(emoji -> Emoji.fromFormatted(emoji.strip())).collect(Collectors.toList());
            if (roleList.size() != emojiList.size()) {
                event.reply("ERROR: Amount of roles and emojis don't match! Command has been canceled.")
                        .setEphemeral(true).queue();
                return;
            }
        }

        MessageChannel channel = null;
        String messageId = "";
        try {
            if (!event.getOption("roles").getMentions().getUsers().isEmpty()) {
                event.reply("The list of roles contains users. Please only input valid roles.")
                        .setEphemeral(true).queue();
                return;
            }
            if (!validateEmojis(event.getGuild(), emojiList)) {
                event.reply("One or more emojis are invalid. " +
                                "Please only use either default emojis or custom emojis from this server.")
                        .setEphemeral(true).queue();
                return;
            }
            EmbedBuilder embedBuilder = new EmbedBuilder();
            if (event.getOption("image") != null) {
                embedBuilder.setThumbnail(event.getOption("image").getAsString());
            }
            if (event.getOption("title") != null) {
                embedBuilder.setTitle(event.getOption("title").getAsString());
            }
            String message = event.getOption("message").getAsString().replace("\\n", "\n");
            embedBuilder.setDescription(message);

            channel = event.getGuild()
                    .getChannelById(GuildMessageChannel.class, event.getOption("channel")
                            .getAsChannel().getId());

            String roleReaction = !roleList.isEmpty() ? " with role reaction." : " without role reaction.";
            if (event.getOption("embed").getAsBoolean()) {
                messageId = channel.sendMessageEmbeds(embedBuilder.build()).complete().getId();
                event.replyFormat("Embed created in %s", channel.getAsMention() + roleReaction).setEphemeral(true)
                        .queue();
            } else {
                messageId = channel.sendMessage(message).complete().getId();
                event.replyFormat("Message sent in %s", channel.getAsMention() + roleReaction).setEphemeral(true)
                        .queue();
            }
            if (!roleList.isEmpty())
                handleReactionRoles(messageId, roleList, emojiList, channel);
        } catch (Exception e) {
            Logger.error(e);
            if (!messageId.isEmpty())
                channel.deleteMessageById(messageId).complete();
            event.reply("ERROR: Something went wrong and changes were reverted. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void editMessage(SlashCommandInteractionEvent event) {
        MessageChannel channel =
                event.getOption("channel").getAsChannel().asStandardGuildMessageChannel();

        String messageId = event.getOption("messageid").getAsString();

        if (event.getOption("embed").getAsBoolean()) {
            Message oldEmbed = channel.retrieveMessageById(messageId).complete();
            EmbedBuilder embedBuilder = new EmbedBuilder();
            if (event.getOption("image") != null) {
                embedBuilder.setThumbnail(event.getOption("image").getAsString());
            }
            if (event.getOption("title") != null) {
                embedBuilder.setTitle(event.getOption("title").getAsString());
            }
            if (event.getOption("message") != null) {
                embedBuilder.setDescription(event.getOption("message").getAsString().replace("\\n", "\n"));
            } else {
                embedBuilder.setDescription(oldEmbed.getEmbeds().getFirst().getDescription());
            }
            if (event.getOption("image") != null || event.getOption("title") != null ||
                    event.getOption("message") != null) {
                channel.editMessageEmbedsById(messageId, embedBuilder.build()).complete();
                event.reply("Embed edited.").setEphemeral(true).queue();
            } else {
                event.reply("Can't edit embed without at least one of the following: message, title, image.")
                        .setEphemeral(true).queue();
            }
        } else {
            if (event.getOption("message") != null) {
                channel.editMessageById(messageId, event.getOption("message").getAsString()
                        .replace("\\n", "\n")).complete();
                event.reply("Message edited.").setEphemeral(true).queue();
            } else {
                event.reply("Can't edit message without message content.").setEphemeral(true).queue();
            }
        }
    }

    private void removeMessage(SlashCommandInteractionEvent event) {
        try {
            StandardGuildMessageChannel channel = event.getOption("channel").getAsChannel()
                    .asStandardGuildMessageChannel();
            String messageId = event.getOption("messageid").getAsString();
            List<ReactionRoleData> reactionRoleDataList = reactionRoleRepo.loadAll();
            List<ReactionRoleData> toBeRemoved = reactionRoleDataList.stream()
                    .filter(data -> data.messageId().equals(messageId))
                    .toList();
            if (!toBeRemoved.isEmpty()) {
                reactionRoleDataList.removeAll(toBeRemoved);
                reactionRoleRepo.saveAll(reactionRoleDataList, false);
                reactionRoleManager.load();
            }
            channel.deleteMessageById(messageId).complete();
            event.reply("Message removed.").setEphemeral(true).queue();
        } catch (RuntimeException | IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to remove message. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void addYoutubeNotification(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        try {
            YouTubeData data = new YouTubeData(
                    event.getOption("channel").getAsString(),
                    event.getOption("message").getAsString(),
                    event.getOption("name").getAsString(),
                    youTube.getPlaylistIdFromChannelName(event.getOption("name").getAsString()),
                    event.getOption("role").getAsRole().getId(),
                    "",
                    ""
            );
            if (!youTubeRepo.loadAll().contains(data)) {
                youTubeRepo.saveAll(Collections.singletonList(data));
                event.getHook().sendMessage("Youtube notification added.").queue();
            } else event.getHook().sendMessage(
                    String.format("The user %s is already in the list.", event.getOption("name").getAsString()))
                    .queue();
        } catch (InvalidParameterException e) {
            event.getHook().sendMessage(String.format("ERROR: Failed to find upload playlist for channel: %s.",
                    event.getOption("name").getAsString())).queue();
            if (e.getCause() != null)
                Logger.error(e.getCause());
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to add Youtube notification. " +
                            "Please contact the developer.").queue();
        }
    }

    private void editYoutubeNotification(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        try {
            List<YouTubeData> youTubeDataList = youTubeRepo.loadAll();
            if (youTubeDataList.isEmpty()) {
                event.getHook().sendMessage("There are no YouTube notifications.").queue();
                return;
            }
            int index = IntStream.range(0, youTubeDataList.size())
                    .filter(i -> youTubeDataList.get(i).name().equalsIgnoreCase(event.getOption("name")
                            .getAsString()))
                    .findFirst()
                    .orElse(-1);
            if (index == -1) {
                event.getHook().sendMessage(String.format("There is no notification for the user %s",
                        event.getOption("username").getAsString())).queue();
                return;
            }
            YouTubeData data = youTubeDataList.get(index);
            data.updateList(event, youTubeDataList, index);
            youTubeRepo.saveAll(youTubeDataList, false);
            event.getHook().sendMessage("YouTube notification edited.").queue();
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to edit YouTube notification. " +
                            "Please contact the developer.").queue();
        }
    }

    private void removeYoutubeNotification(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        List<YouTubeData> youTubeDataList = youTubeRepo.loadAll();
        Optional<YouTubeData> toBeRemoved = youTubeDataList.stream()
                .filter(data -> data.name().equalsIgnoreCase(event.getOption("name").getAsString()))
                .findFirst();
        if (toBeRemoved.isPresent()) {
            youTubeDataList.remove(toBeRemoved.get());
            try {
                youTubeRepo.saveAll(youTubeDataList, false);
            } catch (IOException e) {
                Logger.error(e);
                event.getHook().sendMessage("ERROR: Failed to remove Youtube notification. " +
                                "Please contact the developer.").queue();
            }
            event.getHook().sendMessage(String.format("Youtube notification for %s removed.",
                            event.getOption("name").getAsString())).queue();
        } else event.getHook().sendMessage(String.format("The user %s is not in the list.",
                        event.getOption("name").getAsString())).queue();
    }

    private void displayYouTubeNotification(SlashCommandInteractionEvent event) {
        List<YouTubeData> youTubeDataList = youTubeRepo.loadAll();
        if (youTubeDataList.isEmpty()) {
            event.reply("No YouTube notifications").setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("YouTube Notifications");
        addFieldSafe(embed, "Username", youTubeDataList.stream()
                        .map(YouTubeData::name)
                        .toList(), true);
        addFieldSafe(embed, "Message", youTubeDataList.stream()
                        .map(YouTubeData::message)
                        .toList(), true);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void addTwitchNotification(@NotNull SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        try {
            String role = "";
            if (event.getOption("role") != null) {
                role = event.getOption("role").getAsRole().getId();
            }
            TwitchData data = new TwitchData(
                    event.getOption("channel").getAsString(),
                    event.getOption("message").getAsString(),
                    event.getOption("username").getAsString(),
                    role,
                    null,
                    0L,
                    null,
                    null
            );
            if (!twitchRepo.loadAll().contains(data)) {
                twitchRepo.saveAll(Collections.singletonList(data));
                twitch.load();
                event.getHook().sendMessage("Twitch notification added.").queue();
            } else event.getHook().sendMessage(String.format("The user %s is already in the list.",
                    event.getOption("username").getAsString())).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to add Twitch notification. Please contact the developer.")
                    .queue();
        }
    }

    private void editTwitchNotification(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        try {
            List<TwitchData> twitchDataList = twitchRepo.loadAll();
            if (twitchDataList.isEmpty()) {
                event.getHook().sendMessage("There are no Twitch notifications.").queue();
                return;
            }
            int index = IntStream.range(0, twitchDataList.size())
                    .filter(i -> twitchDataList.get(i).username().equalsIgnoreCase(event.getOption("username")
                            .getAsString()))
                    .findFirst()
                    .orElse(-1);
            if (index == -1) {
                event.getHook().sendMessage(String.format("There is no notification for the user %s",
                        event.getOption("username").getAsString())).queue();
                return;
            }
            TwitchData data = twitchDataList.get(index);
            data.updateList(event, twitchDataList, index);
            twitchRepo.saveAll(twitchDataList, false);
            twitch.load();
            event.getHook().sendMessage("Twitch notification edited.").queue();
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to edit Twitch notification. " +
                            "Please contact the developer.").queue();
        }
    }

    private void removeTwitchNotification(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        List<TwitchData> twitchDataList = twitchRepo.loadAll();
        Optional<TwitchData> toBeRemoved = twitchDataList.stream()
                .filter(data -> data.username().equalsIgnoreCase(event.getOption("username")
                        .getAsString()))
                .findFirst();
        if (toBeRemoved.isPresent()) {
            twitchDataList.remove(toBeRemoved.get());
            try {
                twitchRepo.saveAll(twitchDataList, false);
                twitch.load();
            } catch (IOException e) {
                Logger.error(e);
                event.getHook().sendMessage("ERROR: Failed to remove Twitch notification. " +
                                "Please contact the developer.").queue();
            }
            event.getHook().sendMessage(String.format("Twitch notification for %s removed.",
                            event.getOption("username").getAsString())).queue();
        } else event.getHook().sendMessage(String.format("The user %s is not in the list.",
                        event.getOption("username").getAsString())).queue();
    }

    private void displayTwitchNotification(SlashCommandInteractionEvent event) {
        List<TwitchData> twitchDataList = twitchRepo.loadAll();
        if (twitchDataList.isEmpty()) {
            event.reply("No Twitch notifications.").setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Twitch notifications");
        addFieldSafe(embed, "Username", twitchDataList.stream()
                        .map(TwitchData::username)
                        .toList(), true);
        addFieldSafe(embed, "Message", twitchDataList.stream()
                        .map(TwitchData::message)
                        .toList(), true);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void addShoutout(SlashCommandInteractionEvent event) {
        List<ShoutoutData> shoutoutData = Arrays.stream(event.getOption("username").getAsString().split(";"))
                        .map(ShoutoutData::new).toList();

        List<ShoutoutData> oldShoutoutData = twitchRepo.loadAllShoutout();
        if (oldShoutoutData.stream().anyMatch(shoutoutData::contains)) {
            List<ShoutoutData> names = oldShoutoutData.stream().filter(shoutoutData::contains).toList();
            event.reply(String.format("The following names are already in the list please remove them and try again\n%s",
                    String.join("\n", names.stream().map(ShoutoutData::username).toList())))
                    .setEphemeral(true).queue();
            return;
        }
        try {
            twitchRepo.saveAllShoutout(shoutoutData);
            twitch.load();
            event.reply("Shout out added.").setEphemeral(true).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to add Shout out! Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private static void displayShoutout(@NotNull SlashCommandInteractionEvent event) {
        List<ShoutoutData> shoutoutData = twitchRepo.loadAllShoutout();
        if (shoutoutData.isEmpty()) {
            event.reply("No shoutout notifications.").setEphemeral(true).queue();
            return;
        }
        StringBuilder message = new StringBuilder();
        for (ShoutoutData s : shoutoutData) {
            message.append(s.username()).append("\n");
        }
        event.reply(message.toString()).setEphemeral(true).queue();
    }

    private void removeShoutout(SlashCommandInteractionEvent event) {
        List<ShoutoutData> toRemove = new ArrayList<>(Arrays.stream(event.getOption("username").getAsString()
                        .split(";")).map(ShoutoutData::new).toList());
        List<ShoutoutData> oldShoutoutData = twitchRepo.loadAllShoutout();
        List<ShoutoutData> removed = new ArrayList<>(oldShoutoutData);
        removed.retainAll(toRemove);
        if (!oldShoutoutData.removeAll(toRemove)) {
            event.reply(String.format("No username was removed. Ensure that the following names " +
                            "are in the list and spelled correctly (/displayshoutout)\n%s",
                    String.join("\n", toRemove.stream().map(ShoutoutData::username).toList())))
                    .setEphemeral(true).queue();
            return;
        }
        try {
            twitchRepo.saveAllShoutout(oldShoutoutData, false);
            twitch.load();
            event.reply(String.format("Following usernames were removed\n%s",
                            String.join("\n", removed.stream().map(ShoutoutData::username).toList())))
                    .setEphemeral(true).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to add Shout out! Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void removeAllShoutout(SlashCommandInteractionEvent event) {
        if (!"yes".equals(event.getOption("are-you-sure").getAsString())) {
            event.reply("Please confirm that you want to remove all shoutouts.").setEphemeral(true).queue();
            return;
        }
        try {
            twitchRepo.saveAllShoutout(new ArrayList<>(),false);
            twitch.load();
            event.reply("All shoutouts removed.").setEphemeral(true).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to remove shoutouts! Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void addMemberCount(SlashCommandInteractionEvent event) {
        try {
            if (!PersistenceUtil.readFileAsString(memberCountFile.toPath()).isEmpty()) {
                event.reply("Member count already added please remove it first before adding a new one.")
                        .setEphemeral(true).queue();
                return;
            }
            PersistenceUtil.writeStringAsFile(memberCountFile.toPath(), event.getOption("channel").getAsString());
            BotMain.scheduleUpdateMemberCount();

            event.reply("Member count successfully added!").setEphemeral(true).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to add member count! Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void removeMemberCount(SlashCommandInteractionEvent event) {
        try {
            if (PersistenceUtil.readFileAsString(memberCountFile.toPath()).isEmpty()) {
                BotMain.removeMemberCount();
                PersistenceUtil.writeStringAsFile(memberCountFile.toPath(), "");
                event.reply("Member count successfully removed.").setEphemeral(true).queue();
            } else event.reply("No existing member count to remove.").setEphemeral(true).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to remove member count! Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void ping(SlashCommandInteractionEvent event) {
        long restPing = event.getJDA().getRestPing().complete();
        long gatewayPing = event.getJDA().getGatewayPing();
        event.replyFormat("Rest ping: %d\nGateway ping: %d", restPing, gatewayPing).setEphemeral(true).queue();
    }

    private boolean handleReactionRoles(String messageId, List<Role> roleList, List<Emoji> emojiList,
                                        MessageChannel channel) throws IOException {
        List<ReactionRoleData> newRoleData = new ArrayList<>();
        List<String> oldRoleIds = reactionRoleRepo.loadAll().stream().map(ReactionRoleData::roleId).toList();
        List<String> newRoleIds = roleList.stream().map(Role::getId).toList();
        if (!CollectionUtils.intersection(oldRoleIds, newRoleIds).isEmpty())
            return false;
        for (int i = 0; i < roleList.size(); i++) {
            channel.addReactionById(messageId, emojiList.get(i)).complete();
            newRoleData.add(new ReactionRoleData(
                    channel.getId(),
                    messageId,
                    newRoleIds.get(i),
                    roleList.get(i).getName(),
                    emojiList.get(i).getFormatted()));
        }
        reactionRoleRepo.saveAll(newRoleData);
        reactionRoleManager.load();
        return true;
    }

    private void addFieldSafe(EmbedBuilder embed, String title, List<String> lines, boolean inline) {
        StringBuilder chunk = new StringBuilder();
        for (String line : lines) {
            if (chunk.length() + line.length() + 1 > 1024) {
                embed.addField(title, chunk.toString(), inline);
                chunk.setLength(0);
                title = ""; // only show the title once
            }
            chunk.append(line).append("\n");
        }
        if (!chunk.isEmpty()) {
            embed.addField(title, chunk.toString(), inline);
        }
    }

    private boolean validateEmojis(Guild guild, List<Emoji> emojiList) {
        boolean valid = true;
        for (Emoji emoji : emojiList) {
            valid = emoji.getType().equals(Type.UNICODE) || guild.getEmojiById(((CustomEmoji) emoji).getId()) != null;
            if (!valid) break;
        }
        return valid;
    }

    public SlashCommandManager(List<CommandData> commandDataList) {
        // Command: /help
        commandDataList.add(Commands.slash("help", "Displays a list of commands"));

        // Command: /addreactionrole <channel> <messageid> <role> <emoji>
        commandDataList.add(
                Commands.slash("addreactionrole", "Add a new reaction that assigns a role")
                        .addOptions(
                                new OptionData(
                                        CHANNEL,
                                        "channel",
                                        "The ID of the channel that the message is in.",
                                        true)
                                        .setChannelTypes(
                                                ChannelType.TEXT,
                                                ChannelType.NEWS,
                                                ChannelType.GUILD_PUBLIC_THREAD),
                                new OptionData(
                                        STRING,
                                        "messageid",
                                        "The ID of the message that this reaction should be added to.",
                                        true),
                                new OptionData(
                                        ROLE,
                                        "role",
                                        "The role that this reaction should give.",
                                        true),
                                new OptionData(
                                        STRING,
                                        "emoji",
                                        "The emoji that should be used.",
                                        true)));

        // Command: /displayreactionroles
        commandDataList.add(Commands.slash("displayreactionroles", "Display all reaction roles."));

        // Command: /removereactionrole <channel> <messageid> <role>
        commandDataList.add(
                Commands.slash("removereactionrole", "Remove a reaction role.")
                        .addOptions(new OptionData(
                                        ROLE,
                                        "role",
                                        "The role to remove.",
                                        true)));

        // Command: /createmessage <channel, message, embed:true/false> [title, roles, emojis, image]
        commandDataList.add(
                Commands.slash("createmessage",
                        "Create a new embed/message in the channel with optional roles and " +
                                "emojis to add reaction roles.")
                        .addOptions(
                                new OptionData(
                                        CHANNEL,
                                        "channel",
                                        "The channel that this message should be sent in",
                                        true)
                                        .setChannelTypes(
                                                ChannelType.TEXT,
                                                ChannelType.NEWS,
                                                ChannelType.GUILD_PUBLIC_THREAD),
                                new OptionData(
                                        STRING,
                                        "message",
                                        "The text that should be displayed in the message or embed. " +
                                                "Insert a \\n for a new line.",
                                        true),
                                new OptionData(
                                        BOOLEAN,
                                        "embed",
                                        "Whether the message should be an embed or not.",
                                        true),
                                new OptionData(
                                        STRING,
                                        "title",
                                        "Optional title for the embed."),
                                new OptionData(
                                        STRING,
                                        "roles",
                                        "Roles in the same order as emojis."),
                                new OptionData(
                                        STRING,
                                        "emojis",
                                        "Emojis separated by ; in the same order as roles " +
                                                "(custom emojis only from this server)."),
                                new OptionData(
                                        STRING,
                                        "image",
                                        "Optional image url that is added to the embed")));

        // Command: /removemessage <channel, messageid>
        commandDataList.add(
                Commands.slash("removemessage",
                        "Removes a message, if this message had any reaction roles " +
                                "they will be deleted as well.")
                        .addOptions(
                                new OptionData(
                                        CHANNEL,
                                        "channel",
                                        "The channel that the message is in.",
                                        true),
                                new OptionData(
                                        STRING,
                                        "messageid",
                                        "The ID of the message that should be removed.",
                                        true)));

        // Command: /addyoutubenotif <channel, message, name, role>
        commandDataList.add(
                Commands.slash("addyoutubenotif",
                                "Add a YouTube notification output to a specific channel")
                        .addOptions(
                                new OptionData(
                                        CHANNEL,
                                        "channel",
                                        "The channel that the notification should be posted to.",
                                        true)
                                        .setChannelTypes(
                                                ChannelType.TEXT,
                                                ChannelType.NEWS,
                                                ChannelType.GUILD_PUBLIC_THREAD),
                                new OptionData(
                                        STRING,
                                        "message",
                                        "The message sent with the notification. Insert a \\n for a new line.",
                                        true),
                                new OptionData(
                                        STRING,
                                        "name",
                                        "The @ handle or legacy username of the YouTube channel.",
                                        true),
                                new OptionData(
                                        ROLE,
                                        "role",
                                        "The role that will be pinged with the notification.",
                                        true)));

        // Command: /edityoutubenotif <name> [message, role]
        commandDataList.add(
                Commands.slash("edityoutubenotif", "Edit a YouTube notification.")
                        .addOptions(
                                new OptionData(
                                        STRING,
                                        "name",
                                        "The @ handle or legacy username of the YouTube channel.",
                                        true),
                                new OptionData(
                                        STRING,
                                        "message",
                                        "The new message."),
                                new OptionData(
                                        ROLE,
                                        "role",
                                        "The new role.")));

        // Command: /removeyoutubenotif <name>
        commandDataList.add(
                Commands.slash("removeyoutubenotif", "Remove a YouTube notification")
                        .addOptions(
                                new OptionData(
                                        STRING,
                                        "name",
                                        "The @ handle or legacy username of the YouTube channel.",
                                        true)));

        // Command: /displayyoutubenotif
        commandDataList.add(
                Commands.slash("displayyoutubenotif", "Displays all YouTube notifications."));

        // Command: /addtwitchnotif <channel, message, username, role>
        commandDataList.add(
                Commands.slash("addtwitchnotif",
                        "Add a Twitch notification output to a specific channel")
                        .addOptions(
                                new OptionData(
                                        CHANNEL,
                                        "channel",
                                        "The channel that the notification should be posted to.",
                                        true).setChannelTypes(ChannelType.TEXT, ChannelType.NEWS,
                                        ChannelType.GUILD_PUBLIC_THREAD),
                                new OptionData(
                                        STRING,
                                        "message",
                                        "The message sent with the notification. Insert a \\n for a new line.",
                                        true),
                                new OptionData(
                                        STRING,
                                        "username",
                                        "The username of the Twitch channel.",
                                        true),
                                new OptionData(
                                        ROLE,
                                        "role",
                                        "The role that will be pinged with the notification.")));

        // Command: /edittwitchnotif <username> [message, role]
        commandDataList.add(
                Commands.slash("edittwitchnotif", "Edit a Twitch notification")
                        .addOptions(
                                new OptionData(
                                        STRING,
                                        "username",
                                        "The username of the Twitch channel.",
                                        true),
                                new OptionData(
                                        STRING,
                                        "message",
                                        "The new message."),
                                new OptionData(
                                        ROLE,
                                        "role",
                                        "The new role.")));

        // Command: /removetwitchnotif <username>
        commandDataList.add(
                Commands.slash("removetwitchnotif", "Remove a Twitch notification")
                        .addOptions(
                                new OptionData(
                                        STRING,
                                        "username",
                                        "The username of the Twitch channel",
                                        true)));

        commandDataList.add(Commands.slash("displaytwitchnotif", "Displays all Twitch notifications."));

        // Command: /editmessage <channel, messageid, message, embed:true/false> [title, image]
        commandDataList.add(
                Commands.slash("editmessage", "Edit the text of a specific message or embed")
                        .addOptions(
                                new OptionData(
                                        CHANNEL,
                                        "channel",
                                        "The channel that the message/embed is in.",
                                        true)
                                        .setChannelTypes(
                                                ChannelType.TEXT,
                                                ChannelType.NEWS,
                                                ChannelType.GUILD_PUBLIC_THREAD),
                                new OptionData(
                                        STRING,
                                        "messageid",
                                        "The id of the message/embed that should be edited.",
                                        true),
                                new OptionData(
                                        BOOLEAN,
                                        "embed",
                                        "Whether the message that should be edited is an embed or not.",
                                        true),
                                new OptionData(
                                        STRING,
                                        "message",
                                        "The new message that replaces the old one. Old message if empty. " +
                                                "Insert a \\n for a new line."),
                                new OptionData(
                                        STRING,
                                        "title",
                                        "Optional title for the embed."),
                                new OptionData(
                                        STRING,
                                        "image",
                                        "Optional image that is added to the embed")));

        // Command: /addshoutout <username>
        commandDataList.add(
                Commands.slash("addshoutout", "Add users to be shouted out on Twitch")
                        .addOptions(
                                new OptionData(
                                        STRING,
                                        "username",
                                        "Usernames separated by ;",
                                        true)));

        // Command: /removeshoutout
        commandDataList.add(
                Commands.slash("removeshoutout", "Removes users from the shoutout list")
                        .addOptions(
                                new OptionData(
                                        STRING,
                                        "username",
                                        "Usernames seperated by ;",
                                        true)));

        // Command: /removeallshoutouts
        commandDataList.add(
                Commands.slash("removeallshoutouts", "Removes all users from the shoutout list")
                        .addOptions(
                                new OptionData(
                                        STRING,
                                        "are-you-sure",
                                        "This action is not reversible! " +
                                                "If you are sure to remove all shoutouts from the list type yes.",
                                        true)));

        // Command: /displayshoutout
        commandDataList.add(
                Commands.slash("displayshoutout", "Displays all usernames from the shoutout list"));

        // Command: /addmembercount
        commandDataList.add(
                Commands.slash("addmembercount", "Add a member count")
                        .addOptions(
                                new OptionData(
                                        CHANNEL,
                                        "channel",
                                        "The vc that displays the member count",
                                        true)
                                        .setChannelTypes(ChannelType.VOICE)));

        // Command: /removemembercount
        commandDataList.add(Commands.slash("removemembercount",
                "Removes the member count and deletes the voice channel"));

        // Command: /ping
        commandDataList.add(Commands.slash("ping", "Simple ping to check if the bot is responding."));

        categories.put("help", "General");
        categories.put("addreactionrole", "Reaction Role");
        categories.put("removereactionrole", "Reaction Role");
        categories.put("displayreactionroles", "Reaction Roles");
        categories.put("createmessage", "General");
        categories.put("editmessage", "General");
        categories.put("removemessage", "General");
        categories.put("addyoutubenotif", "YouTube");
        categories.put("edityoutubenotif", "YouTube");
        categories.put("removeyoutubenotif", "YouTube");
        categories.put("displayyoutubenotif", "YouTube");
        categories.put("addtwitchnotif", "Twitch");
        categories.put("edittwitchnotif", "Twitch");
        categories.put("removetwitchnotif", "Twitch");
        categories.put("displaytwitchnotif", "Twitch");
        categories.put("addshoutout", "Shoutout");
        categories.put("removeshoutout", "Shoutout");
        categories.put("removeallshoutouts", "Shoutout");
        categories.put("displayshoutout", "Shoutout");
        categories.put("addmembercount", "General");
        categories.put("removemembercount", "General");
        categories.put("ping", "General");
    }
}
