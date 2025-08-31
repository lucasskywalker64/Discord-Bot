package com.github.lucasskywalker64.listener.command;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.twitch.ShoutoutData;
import com.github.lucasskywalker64.api.youtube.YouTubeImpl;
import com.github.lucasskywalker64.persistence.PersistenceUtil;
import com.github.lucasskywalker64.persistence.data.ReactionRoleData;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import com.github.lucasskywalker64.api.twitch.TwitchImpl;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.exceptions.InvalidParameterException;
import com.github.lucasskywalker64.listener.role.ReactionRoleManager;
import com.github.lucasskywalker64.persistence.repository.ReactionRoleRepository;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
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

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            /*
            Add a reaction to a message which can be used by members to assign a role to themselves.
            Command: /addreactionrole <channel> <messageid> <role> <emoji>
             */
            case "addreactionrole" -> addReactionRole(event);

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

            default -> Logger.error("No matching case found for {}", event.getName());
        }
    }

    private void addReactionRole(SlashCommandInteractionEvent event) {
        try {
            validateRoles(event, Collections.singletonList(event.getOption("role").getAsString()));
            handleReactionRoles(
                    event.getOption("messageid").getAsString(),
                    Collections.singletonList(event.getOption("role").getAsString()),
                    Collections.singletonList(event.getOption("emoji").getAsString()),
                    event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId()));
            event.reply("Reaction role has been added.").setEphemeral(true).queue();
        } catch (ErrorResponseException e) {
            if (e.getErrorCode() == 10014) {
                Logger.error(e);
                event.reply("ERROR: Unknown Emoji. Can only use emojis that are either default " +
                        "or have been added to the server!").setEphemeral(true).queue();
            } else {
                Logger.error(e);
                event.reply("ERROR: Unknown message. Make sure the message is actually " +
                        "in the provided channel.").setEphemeral(true).queue();
            }
        } catch (InvalidParameterException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to add reaction role. If this error persists please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void createMessage(SlashCommandInteractionEvent event) {
        List<String> roleList = new ArrayList<>();
        List<String> emojiList = new ArrayList<>();

        if (event.getOption("roles") != null && event.getOption("emojis") != null) {
            roleList = new ArrayList<>(Arrays.asList(event.getOption("roles").getAsString().split(";")));
            emojiList =
                    new ArrayList<>(Arrays.asList(event.getOption("emojis").getAsString().split(";")));
        }

        try {
            if (roleList.size() == emojiList.size()) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                if (event.getOption("image") != null) {
                    embedBuilder.setThumbnail(event.getOption("image").getAsString());
                }
                if (event.getOption("title") != null) {
                    embedBuilder.setTitle(event.getOption("title").getAsString());
                }
                String message = event.getOption("message").getAsString().replace("\\n", "\n");
                embedBuilder.setDescription(message);

                MessageChannel channel = event.getGuild()
                        .getChannelById(GuildMessageChannel.class, event.getOption("channel")
                                .getAsChannel().getId());

                validateRoles(event, roleList);

                String messageId;
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
            } else {
                event.reply("ERROR: Amount of roles and emojis don't match! Command has been canceled.")
                        .setEphemeral(true).queue();
            }
        } catch (InvalidParameterException e) {
            if (e.getCode() == 1002) {
                event.reply("ERROR: Faulty role input!").setEphemeral(true).queue();
            }
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to create message. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void editMessage(SlashCommandInteractionEvent event) {
        TextChannel channel =
                event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId());

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
            TextChannel channel = event.getGuild()
                    .getTextChannelById(event.getOption("channel")
                            .getAsChannel()
                            .getId());
            String messageId = event.getOption("messageid").getAsString();
            List<ReactionRoleData> reactionRoleDataList = reactionRoleRepo.loadAll();
            Optional<ReactionRoleData> toBeRemoved = reactionRoleDataList.stream()
                    .filter(data -> data.messageId().equals(messageId))
                    .findFirst();
            if (toBeRemoved.isPresent()) {
                reactionRoleDataList.remove(toBeRemoved.get());
                reactionRoleRepo.saveAll(reactionRoleDataList);
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

    public static void validateRoles(SlashCommandInteractionEvent event, List<String> roleList)
            throws InvalidParameterException {
        for (String role : roleList) {
            if (role.contains("@")) {
                if (role.matches("@everyone|@here") || event.getGuild().getRoleById(
                        Long.parseLong(role.substring(role.indexOf("&") + 1, role.lastIndexOf(">")))) == null) {
                    throw new InvalidParameterException(1002, null);
                }
            } else {
                if (event.getGuild().getRoleById(Long.parseLong(role)) == null) {
                    throw new InvalidParameterException(1002, null);
                }
            }
        }
    }

    private void handleReactionRoles(String messageId, List<String> roleList, List<String> emojiList,
                                     MessageChannel channel) throws IOException {
        List<ReactionRoleData> newRoleData = new ArrayList<>();
        for (int i = 0; i < roleList.size(); i++) {
            channel.addReactionById(messageId, Emoji.fromFormatted(emojiList.get(i).strip()))
                    .complete();
            newRoleData.add(new ReactionRoleData(
                    messageId,
                    Long.parseLong(roleList.get(i).substring(roleList.get(i).indexOf("&") + 1,
                            roleList.get(i).lastIndexOf(">") == -1 ? roleList.get(i).length()
                                    : roleList.get(i).indexOf(">"))),
                    emojiList.get(i)
            ));
        }
        reactionRoleRepo.saveAll(newRoleData);
        reactionRoleManager.load();
    }

    private void addYoutubeNotification(SlashCommandInteractionEvent event) {
        try {
            validateRoles(event, Collections.singletonList(event.getOption("role").getAsString()));
            YouTubeData data = new YouTubeData(
                    event.getOption("channel").getAsString(),
                    event.getOption("message").getAsString(),
                    event.getOption("name").getAsString(),
                    youTube.getPlaylistIdFromChannelName(event.getOption("name").getAsString()),
                    event.getOption("role").getAsString(),
                    "",
                    ""
            );
            if (!youTubeRepo.loadAll().contains(data)) {
                youTubeRepo.saveAll(Collections.singletonList(data));
                event.reply("Youtube notification added.").setEphemeral(true).queue();
            } else event.replyFormat("The user %s is already in the list.", event.getOption("name").getAsString())
                    .setEphemeral(true).queue();
        } catch (InvalidParameterException e) {
            if (e.getCode() == 1001)
                event.replyFormat("ERROR: Faulty role input: %s.", event.getOption("role").getAsString())
                        .setEphemeral(true).queue();
            else if (e.getCode() == 1003) {
                event.replyFormat("ERROR: Failed to find YouTube channel: %s.",
                        event.getOption("name").getAsString()).setEphemeral(true).queue();
                if (e.getCause() != null)
                    Logger.error(e.getCause());
            }
        } catch (IOException e) {
            Logger.error(e);
            event.replyFormat("ERROR: Failed to add Youtube notification. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void editYoutubeNotification(SlashCommandInteractionEvent event) {
        try {
            List<YouTubeData> youTubeDataList = youTubeRepo.loadAll();
            if (youTubeDataList.isEmpty()) {
                event.reply("There are no YouTube notifications.").setEphemeral(true).queue();
                return;
            }
            int index = IntStream.range(0, youTubeDataList.size())
                    .filter(i -> youTubeDataList.get(i).name().equalsIgnoreCase(event.getOption("name")
                            .getAsString()))
                    .findFirst()
                    .orElse(-1);
            if (index == -1) {
                event.replyFormat("There is no notification for the user %s", event.getOption("username")
                        .getAsString()).setEphemeral(true).queue();
                return;
            }
            YouTubeData data = youTubeDataList.get(index);
            data.updateList(event, youTubeDataList, index, (e, roles) -> {
                try {
                    SlashCommandManager.validateRoles(e, roles);
                } catch (InvalidParameterException ex) {
                    event.reply("ERROR: Faulty role input!").setEphemeral(true).queue();
                }
            });
            youTubeRepo.saveAll(youTubeDataList, false);
            event.reply("YouTube notification edited.").setEphemeral(true).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to edit YouTube notification. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void removeYoutubeNotification(SlashCommandInteractionEvent event) {
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
                event.reply("ERROR: Failed to remove Youtube notification. Please contact the developer.")
                        .setEphemeral(true).queue();
            }
            event.replyFormat("Youtube notification for %s removed.", event.getOption("name").getAsString())
                    .setEphemeral(true).queue();
        } else event.replyFormat("The user %s is not in the list.", event.getOption("name").getAsString())
                .setEphemeral(true).queue();
    }

    private void addTwitchNotification(@NotNull SlashCommandInteractionEvent event) {
        try {
            String role = "";
            if (event.getOption("role") != null) {
                validateRoles(event, Collections.singletonList(event.getOption("role").getAsString()));
                role = event.getOption("role").getAsString();
            }
            TwitchData data = new TwitchData(
                    event.getOption("channel").getAsString(),
                    event.getOption("message").getAsString(),
                    event.getOption("username").getAsString(),
                    role,
                    null,
                    0L
            );
            if (!twitchRepo.loadAll().contains(data)) {
                twitchRepo.saveAll(Collections.singletonList(data));
                twitch.load();
                event.reply("Twitch notification added.").setEphemeral(true).queue();
            } else event.replyFormat("The user %s is already in the list.",
                    event.getOption("username").getAsString()).setEphemeral(true).queue();
        } catch (InvalidParameterException e) {
            event.reply("ERROR: Faulty role input!").setEphemeral(true).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to add Twitch notification. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void editTwitchNotification(SlashCommandInteractionEvent event) {
        try {
            List<TwitchData> twitchDataList = twitchRepo.loadAll();
            if (twitchDataList.isEmpty()) {
                event.reply("There are no Twitch notifications.").setEphemeral(true).queue();
                return;
            }
            int index = IntStream.range(0, twitchDataList.size())
                    .filter(i -> twitchDataList.get(i).username().equalsIgnoreCase(event.getOption("username")
                            .getAsString()))
                    .findFirst()
                    .orElse(-1);
            if (index == -1) {
                event.replyFormat("There is no notification for the user %s", event.getOption("username")
                        .getAsString()).setEphemeral(true).queue();
                return;
            }
            TwitchData data = twitchDataList.get(index);
            data.updateList(event, twitchDataList, index, (e, roles) -> {
                try {
                    SlashCommandManager.validateRoles(e, roles);
                } catch (InvalidParameterException ex) {
                    event.reply("ERROR: Faulty role input!").setEphemeral(true).queue();
                }
            });
            twitchRepo.saveAll(twitchDataList, false);
            twitch.load();
            event.reply("Twitch notification edited.").setEphemeral(true).queue();
        } catch (IOException e) {
            Logger.error(e);
            event.reply("ERROR: Failed to edit Twitch notification. Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }

    private void removeTwitchNotification(SlashCommandInteractionEvent event) {
        List<TwitchData> twitchDataList = twitchRepo.loadAll();
        Optional<TwitchData> toBeRemoved = twitchDataList.stream()
                .filter(data -> data.username().equalsIgnoreCase(event.getOption("username")
                        .getAsString()))
                .findFirst();
        if (toBeRemoved.isPresent()) {
            twitchDataList.remove(toBeRemoved.get());
            try {
                twitchRepo.saveAll(twitchDataList);
                twitch.load();
            } catch (IOException e) {
                Logger.error(e);
                event.reply("ERROR: Failed to remove Twitch notification. Please contact the developer.")
                        .setEphemeral(true).queue();
            }
            event.replyFormat("Twitch notification for %s removed.", event.getOption("username").getAsString())
                    .setEphemeral(true).queue();
        } else event.replyFormat("The user %s is not in the list.", event.getOption("username").getAsString())
                .setEphemeral(true).queue();
    }

    private void addShoutout(SlashCommandInteractionEvent event) {
        List<ShoutoutData> shoutoutData =
                new ArrayList<>(Arrays.stream(event.getOption("username").getAsString().split(";"))
                        .map(ShoutoutData::new).toList());

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
        StringBuilder message = new StringBuilder();
        for (ShoutoutData s : twitchRepo.loadAllShoutout()) {
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

    public SlashCommandManager(List<CommandData> commandDataList) {
        // Command: /addreactionrole <channel> <messageid> <@role> <emoji>
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
                                        "Roles separated by ; in the same order as emojis."),
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
                                        true), new OptionData(ROLE, "role",
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
                                        STRING,
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
                                        true)
                        ));

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
                                        STRING,
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
    }
}
