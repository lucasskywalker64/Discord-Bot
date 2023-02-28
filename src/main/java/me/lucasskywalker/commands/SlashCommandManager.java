package me.lucasskywalker.commands;

import me.lucasskywalker.BotMain;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SlashCommandManager extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            /*
            Add a reaction to a message which can be used by members to assign a role to themselves
            Command: /addreactionrole <channel> <messageid> <roleid> <emoji>
             */
            case "addreactionrole" -> {
                try {
                    String emoji = event.getOption("emoji").getAsString();

                    event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId())
                            .addReactionById(event.getOption("messageid").getAsLong(),
                                    Emoji.fromFormatted(emoji)).complete();

                    File filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                            + "/bot_files/reaction-roles.csv");

                    CSVFormat csvFormat;
                    if (filePath.createNewFile()) {
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";")
                                .setHeader("messageId", "roleId", "emoji")
                                .build();
                    } else
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";").build();

                    FileWriter fileWriter = new FileWriter(filePath, true);

                    CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);
                    csvPrinter.printRecord(
                            event.getOption("messageid").getAsString(),
                            event.getOption("role").getAsString(),
                            event.getOption("emoji").getAsString());
                    csvPrinter.close(true);

                    event.reply("Reaction role has been added").queue();
                } catch (ErrorResponseException e) {
                    if(e.getErrorCode() == 10014)
                        event.reply("ERROR: Unknown Emoji. Can only use emojis that are either default " +
                                "or have been added to the server!").queue();
                    else event.reply("ERROR: Unknown message. Make sure the message is actually " +
                            "in the provided channel").queue();
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

            /*
            Create a new embed or message in the given channel with optional roles and emojis to add reaction roles
            Command: /createractionmessage <channel, message, embed:true/false> [title, roles, emojis, image]
             */
            case "createreactionmessage" -> {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                if(event.getOption("image") != null)
                    embedBuilder.setThumbnail(event.getOption("image").getAsString());
                if (event.getOption("title") != null)
                    embedBuilder.setTitle(event.getOption("title").getAsString());
                String message = event.getOption("message").getAsString()
                        .replace("\\n", "\n");
                embedBuilder.setDescription(message);

                if (event.getOption("roles") != null && event.getOption("emojis") != null) {
                    List<String> roleList = new ArrayList<>(Arrays.asList(event.getOption("roles")
                            .getAsString().split(";")));
                    List<String> emojiList = new ArrayList<>(Arrays.asList(event.getOption("emojis")
                            .getAsString().split(";")));

                    if(roleList.size() == emojiList.size()) {
                        try {
                            for (int i = 0; i < emojiList.size(); i++) {
                                if(roleList.get(i).contains("@")) {
                                    if (event.getGuild().getRoleById(Long.parseLong(roleList.get(i)
                                            .substring(roleList.get(i).indexOf("&") + 1, roleList.get(i)
                                                    .lastIndexOf(">")))) == null)
                                        throw new NullPointerException("No role");
                                }
                                else {
                                    if (event.getGuild().getRoleById(Long.parseLong(roleList.get(i))) == null)
                                        throw new NullPointerException("No role");
                                }
                            }

                            String messageId;
                            if (event.getOption("embed").getAsBoolean()) {
                                messageId = event.getGuild().getTextChannelById(event.getOption("channel")
                                        .getAsChannel().getId()).sendMessageEmbeds(embedBuilder.build()).complete()
                                        .getId();
                                event.reply("Embed created in " + event.getOption("channel").getAsChannel()
                                        .getAsMention()).queue();
                            } else {
                                messageId = event.getGuild().getTextChannelById(event.getOption("channel")
                                        .getAsChannel().getId()).sendMessage(message).complete().getId();
                                event.reply("Message sent in " + event.getOption("channel").getAsChannel()
                                        .getAsMention()).queue();
                            }

                            File filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                                    + "/bot_files/reaction-roles.csv");

                            CSVFormat csvFormat;
                            if (filePath.createNewFile()) {
                                csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                        .setDelimiter(";")
                                        .setHeader("messageId", "roleId", "emoji")
                                        .build();
                            } else
                                csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                        .setDelimiter(";").build();

                            FileWriter fileWriter = new FileWriter(filePath, true);

                            CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);

                            for (int i = 0; i < emojiList.size(); i++) {
                                csvPrinter.printRecord(messageId, roleList.get(i).strip(), emojiList.get(i).strip());

                                event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel()
                                        .getId()).addReactionById(messageId,
                                        Emoji.fromFormatted(emojiList.get(i).strip())).complete();
                            }
                            csvPrinter.close(true);
                        } catch (URISyntaxException | IOException e) {
                            throw new RuntimeException(e);
                        } catch (NullPointerException e) {
                            if(e.getMessage().equals("No role"))
                                event.reply("ERROR: Faulty role input!").queue();
                        }
                    } else
                        event.reply("ERROR: Amount of roles and emojis don't match! Command has been canceled.")
                                .queue();
                } else {
                    if (event.getOption("embed").getAsBoolean()) {
                        event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId())
                                .sendMessageEmbeds(embedBuilder.build()).queue();
                        event.reply("Embed created in " + event.getOption("channel").getAsChannel()
                                .getAsMention() + " without role reaction.").queue();
                    } else {
                        event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId())
                                .sendMessage(message).queue();
                        event.reply("Message sent in " + event.getOption("channel").getAsChannel()
                                .getAsMention() + " without role reaction.").queue();
                    }
                }
            }

            /*
            Add a YouTube notification output to a specific channel
            Command: /addyoutubenotif <channel, message, ytchannelid, role>
             */
            case "addyoutubenotif" -> {
                try {
                    File filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                            + "/bot_files/youtube.csv");

                    CSVFormat csvFormat;
                    if (filePath.createNewFile()) {
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";")
                                .setHeader("channel", "message", "ytchannelid", "role", "videoid")
                                .build();
                    } else
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";").build();

                    FileWriter fileWriter = new FileWriter(filePath, true);

                    CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);
                    csvPrinter.printRecord(
                            event.getOption("channel").getAsString(),
                            event.getOption("message").getAsString(),
                            event.getOption("ytchannelid").getAsString(),
                            event.getOption("role").getAsString(),
                            "null");
                    csvPrinter.close(true);
                    event.reply("Youtube notification added").queue();
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

            /*
            Edit message command
            Command: /editmessage <channel, messageid, embed:true/false> [message, title, image]
             */
            case "editmessage" -> {
                TextChannel channel = event.getGuild().getTextChannelById(
                        event.getOption("channel").getAsChannel().getId());

                String messageid = event.getOption("messageid").getAsString();

                if(event.getOption("embed").getAsBoolean()) {
                    Message oldEmbed = channel.retrieveMessageById(messageid).complete();
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    if(event.getOption("image") != null)
                        embedBuilder.setThumbnail(event.getOption("image").getAsString());
                    if (event.getOption("title") != null)
                        embedBuilder.setTitle(event.getOption("title").getAsString());
                    if(event.getOption("message") != null)
                        embedBuilder.setDescription(event.getOption("message").getAsString()
                                .replace("\\n", "\n"));
                    else embedBuilder.setDescription(oldEmbed.getEmbeds().get(0).getDescription());
                    if(event.getOption("image") != null || event.getOption("title") != null ||
                            event.getOption("message") != null) {
                        channel.editMessageEmbedsById(messageid, embedBuilder.build()).complete();
                        event.reply("Embed edited.").queue();
                    } else event.reply("Can't edit embed without at least one of the following: " +
                            "message, title, image").queue();
                } else {
                    if(event.getOption("message") != null) {
                        channel.editMessageById(messageid, event.getOption("message").getAsString()
                                .replace("\\n", "\n")).complete();
                        event.reply("Message edited.").queue();
                    } else event.reply("Can't edit message without message content").queue();
                }
            }

            /*
            Add a YouTube notification output to a specific channel
            Command: /addyoutubenotif <channel, message, username, role>
             */
            case "addtwitchnotif" -> {
                try {
                    File filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                            + "/bot_files/twitch.csv");

                    CSVFormat csvFormat;
                    if (filePath.createNewFile()) {
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";")
                                .setHeader("channel", "message", "username", "role")
                                .build();
                    } else
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";").build();

                    FileWriter fileWriter = new FileWriter(filePath, true);

                    String role = event.getOption("role") != null ?
                            event.getOption("role").getAsString()
                            : "";

                    CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);
                    csvPrinter.printRecord(
                            event.getOption("channel").getAsString(),
                            event.getOption("message").getAsString(),
                            event.getOption("username").getAsString().toLowerCase(),
                            role);
                    csvPrinter.close(true);
                    BotMain.twitch.updateLists();
                    event.reply("Twitch notification added").queue();
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

            /*
            Add a tweet output to a channel
            Command: /addtwitter <username, channel, message>
             */
            case "addtwitter" -> {
                try {
                    File filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                            + "/bot_files/twitter_data.csv");

                    CSVFormat csvFormat;
                    if (filePath.createNewFile()) {
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";")
                                .setHeader("username", "tweetID", "channel", "message", "keyword")
                                .build();
                    } else
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";").build();

                    FileWriter fileWriter = new FileWriter(filePath, true);

                    CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);
                    csvPrinter.printRecord(
                            event.getOption("username").getAsString(),
                            OffsetDateTime.now(),
                            event.getOption("channel").getAsString(),
                            event.getOption("message").getAsString());
                    csvPrinter.close(true);

                    filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                            + "/bot_files/twitter_names.csv");

                    if (filePath.createNewFile()) {
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";")
                                .setHeader("username")
                                .build();
                    } else
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";").build();

                    fileWriter = new FileWriter(filePath, true);

                    csvPrinter = new CSVPrinter(fileWriter, csvFormat);
                    csvPrinter.printRecord(event.getOption("username").getAsString());
                    csvPrinter.close(true);

                    BotMain.twitter.readCSV();
                    BotMain.twitter.getUserID();
                    event.reply("Twitter output added").queue();
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

            /*
            Add users to be shouted out on Twitch
            Command: /addshoutout <username>
             */
            case "addshoutout" -> {
                List<String> usernameList = new ArrayList<>(Arrays.asList(event.getOption("username")
                        .getAsString().split(";")));

                try {
                    File filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                            + "/bot_files/shoutout.csv");

                    CSVFormat csvFormat;
                    if (filePath.createNewFile()) {
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setHeader("username")
                                .build();
                    } else
                        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                .setDelimiter(";").build();

                    FileWriter fileWriter = new FileWriter(filePath, true);

                    CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);

                    for (String s : usernameList) {
                        csvPrinter.printRecord(s.strip().toLowerCase());
                    }
                    csvPrinter.close(true);

                    BotMain.twitch.updateLists();
                    event.reply("Shout out added").queue();
                } catch (URISyntaxException | IOException e) {
                    e.printStackTrace();
                }
            }

            /*
            Displays all usernames from the shoutout list
            Command: /displayshoutout
             */
            case "displayshoutout" -> {
                StringBuilder message = new StringBuilder();
                for(String s : BotMain.twitch.getShoutoutNames())
                    message.append(s).append("\n");
                event.reply(message.toString()).setEphemeral(true).queue();
            }

            /*
            Removes a user from the shoutout list
            Command: /removeshoutout <username>
             */
            case "removeshoutout" -> {
                List<String> usernames = BotMain.twitch.getShoutoutNames();
                System.out.println(usernames);
                if(usernames.contains(event.getOption("username").getAsString())) {
                    usernames.remove(event.getOption("username").getAsString());
                    System.out.println(usernames);
                    try {
                        File filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                                .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                                + "/bot_files/shoutout.csv");

                        CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                                    .setHeader("username")
                                    .build();

                        FileWriter fileWriter = new FileWriter(filePath);

                        CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);

                        for (String s : usernames) {
                            csvPrinter.printRecord(s);
                        }
                        csvPrinter.close(true);

                        BotMain.twitch.updateLists();

                        event.reply("User has successfully been removed!").queue();
                    } catch (URISyntaxException | IOException e) {
                        throw new RuntimeException(e);
                    }
                } else event.reply("Provided username is not in the list! " +
                        "(use /displayshoutout to see all usernames)").queue();
            }

            /*
            Add a member count
            Command: /addmembercount <channel>
             */
            case "addmembercount" -> {
                try {
                    File filePath = new File(new File(SlashCommandManager.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                            + "/bot_files/membercount.txt");

                    FileWriter fileWriter;
                    if (filePath.createNewFile()) {
                        fileWriter = new FileWriter(filePath);
                    } else {
                        event.reply("Member count already added please remove it first before adding a new one.")
                                .queue();
                        return;
                    }

                    fileWriter.write(event.getOption("channel").getAsChannel().getId());
                    fileWriter.close();

                    BotMain.scheduleUpdateMemberCount();

                    event.reply("Member count successfully added!").queue();
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

            /*case "teststream" -> {
                BotMain.twitch.testStream();
                event.reply("test notif sent").queue();
            }*/

            /*
            Simple ping to check if the bot is responding
            Command: /ping
             */
            case "ping" -> {
                long restPing = event.getJDA().getRestPing().complete();
                long gatewayPing = event.getJDA().getGatewayPing();
                event.reply("Rest ping: " + restPing + "\n"
                        + "Gateway ping: " + gatewayPing).queue();
            }
        }
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        List<CommandData> commandDataList = new ArrayList<>();

        // Command: /addreactionrole <channel> <messageid> <@role> <emoji>
        commandDataList.add(Commands.slash("addreactionrole", "Add a new reaction that assigns a role")
                .addOptions(
                        new OptionData(OptionType.CHANNEL, "channel",
                                "The ID of the channel that the message is in.", true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
                        new OptionData(OptionType.STRING, "messageid",
                                "The ID of the message that this reaction should be added to.",
                                true),
                        new OptionData(OptionType.ROLE, "role",
                                "The role that this reaction should give.", true),
                        new OptionData(OptionType.STRING, "emoji",
                                "The emoji that should be used.", true)));

        // Command: /createractionmessage <channel, message, embed:true/false> [title, roles, emojis, image]
        commandDataList.add(Commands.slash("createreactionmessage",
                "Create a new message or embed for adding reaction roles.").addOptions(
                        new OptionData(OptionType.CHANNEL, "channel",
                                "The channel that this message should be sent in", true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
                        new OptionData(OptionType.STRING, "message",
                                "The text that should be displayed in the message or embed. " +
                                        "Insert a \\n for a new line.", true),
                        new OptionData(OptionType.BOOLEAN, "embed",
                                "Whether the message should be an embed or not.", true),
                        new OptionData(OptionType.STRING, "title",
                                "Optional title for the embed."),
                        new OptionData(OptionType.STRING, "roles",
                                "Roles separated by ; in the same order as emojis."),
                        new OptionData(OptionType.STRING, "emojis",
                                "Emojis separated by ; in the same order as roles " +
                                        "(custom emojis only from this server)."),
                        new OptionData(OptionType.STRING, "image",
                                "Optional image that is added to the embed")));

        // Command: /addyoutubenotif <channel, message, ytchannelid, role>
        commandDataList.add(Commands.slash("addyoutubenotif",
                "Add a YouTube notification output to a specific channel").addOptions(
                        new OptionData(OptionType.CHANNEL, "channel",
                                "The channel that the notification should be posted to.", true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
                        new OptionData(OptionType.STRING, "message",
                                "The message sent with the notification. Insert a \\n for a new line.",
                                true),
                        new OptionData(OptionType.STRING, "ytchannelid",
                                "The ID of the YouTube channel.", true),
                        new OptionData(OptionType.ROLE, "role",
                                "The role that will be pinged with the notification.", true)));

        // Command: /editmessage <channel, messageid, message, embed:true/false> [title, image]
        commandDataList.add(Commands.slash("editmessage",
                "Edit the text of a specific message or embed").addOptions(
                        new OptionData(OptionType.CHANNEL, "channel",
                                "The channel that the message/embed is in.", true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
                        new OptionData(OptionType.STRING, "messageid",
                                "The id of the message/embed that should be edited.", true),
                        new OptionData(OptionType.BOOLEAN, "embed",
                                "Whether the message that should be edited is an embed or not.",
                                true),
                        new OptionData(OptionType.STRING, "message",
                            "The new message that replaces the old one. Old message if empty. " +
                                    "Insert a \\n for a new line."),
                        new OptionData(OptionType.STRING, "title",
                                "Optional title for the embed."),
                        new OptionData(OptionType.STRING, "image",
                                "Optional image that is added to the embed")));

        // Command: /addtwitchnotif <channel, message, username, role>
        commandDataList.add(Commands.slash("addtwitchnotif",
                "Add a Twitch notification output to a specific channel").addOptions(
                new OptionData(OptionType.CHANNEL, "channel",
                        "The channel that the notification should be posted to.", true)
                        .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
                new OptionData(OptionType.STRING, "message",
                        "The message sent with the notification. Insert a \\n for a new line.",
                        true),
                new OptionData(OptionType.STRING, "username",
                        "The username of the Twitch channel.", true),
                new OptionData(OptionType.ROLE, "role",
                        "The role that will be pinged with the notification.")));

        // Command: /addtwitter <username, channel, message>
        commandDataList.add(Commands.slash("addtwitter", "Add a tweet output").addOptions(
                new OptionData(OptionType.STRING, "username",
                        "Twitter username without @.", true),
                new OptionData(OptionType.CHANNEL, "channel",
                        "The channel the tweet should be posted to.", true),
                new OptionData(OptionType.STRING, "message",
                        "The message that should be sent with the tweet. Insert a \\n for a new line.",
                        true)));

        // Command: /addshoutout <username>
        commandDataList.add(Commands.slash("addshoutout",
                "Add users to be shouted out on Twitch").addOptions(
                        new OptionData(OptionType.STRING, "username",
                                "Usernames separated by ;", true)));

        // Command: /displayshoutout
        commandDataList.add(Commands.slash("displayshoutout",
                "Displays all usernames from the shoutout list"));

        // Command: /removeshoutout
        commandDataList.add(Commands.slash("removeshoutout", "Removes a user from the shoutout list")
                .addOptions(new OptionData(OptionType.STRING, "username",
                        "The username to be removed", true)));

        // Command: /addmembercount
        commandDataList.add(Commands.slash("addmembercount", "Add a member count")
                .addOptions(new OptionData(OptionType.CHANNEL, "channel",
                        "The locked vc that displays the member count", true)
                        .setChannelTypes(ChannelType.VOICE)));

        //commandDataList.add(Commands.slash("teststream", "test"));

        // Command: /ping
        commandDataList.add(Commands.slash("ping", "Simple ping to check if the bot is responding."));

        event.getGuild().updateCommands().addCommands(commandDataList).queue();
    }
}
