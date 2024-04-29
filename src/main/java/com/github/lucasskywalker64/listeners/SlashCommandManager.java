package com.github.lucasskywalker64.listeners;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.apis.TwitchImpl;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import com.github.lucasskywalker64.exceptions.InvalidParameters;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
import org.tinylog.Logger;

@SuppressWarnings("java:S1192")
public class SlashCommandManager extends ListenerAdapter {

  private static final TwitchImpl twitch = BotMain.getTwitch();
  private static final ReactionRoleManager reactionRoleManager = BotMain.getReactionRoleManager();
  private static final File reactionRolesFile = BotMain.getReactionRolesFile();
  private static final File youtubeFile = BotMain.getYoutubeFile();
  private static final File twitchFile = BotMain.getTwitchFile();
  private static final File shoutoutFile = BotMain.getShoutoutFile();
  private static final File memberCountFile = BotMain.getMemberCountFile();

  @Override
  public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
    switch (event.getName()) {
            /*
            Add a reaction to a message which can be used by members to assign a role to themselves
            Command: /addreactionrole <channel> <messageid> <roleid> <emoji>
             */
      case "addreactionrole" -> addReactionRole(event);

            /*
            Create a new embed or message in the given channel with optional roles and emojis to add reaction roles
            Command: /createractionmessage <channel, message, embed:true/false> [title, roles, emojis, image]
             */
      case "createreactionmessage" -> createMessage(event);

            /*
            Add a YouTube notification output to a specific channel
            Command: /addyoutubenotif <channel, message, ytchannelid, role>
             */
      case "addyoutubenotif" -> addYoutubeNotification(event);

            /*
            Add a YouTube notification output to a specific channel
            Command: /addyoutubenotif <channel, message, username, role>
             */
      case "addtwitchnotif" -> addTwitchNotification(event);

            /*
            Edit message command
            Command: /editmessage <channel, messageid, embed:true/false> [message, title, image]
             */
      case "editmessage" -> editMessage(event);

            /*
            Add users to be shouted out on Twitch
            Command: /addshoutout <username>
             */
      case "addshoutout" -> addShoutout(event);

            /*
            Displays all usernames from the shoutout list
            Command: /displayshoutout
             */
      case "displayshoutout" -> {
        StringBuilder message = new StringBuilder();
        for (String s : twitch.getShoutoutNames()) {
          message.append(s).append("\n");
        }
        event.reply(message.toString()).setEphemeral(true).queue();
      }

            /*
            Removes a user from the shoutout list
            Command: /removeshoutout <username>
             */
      case "removeshoutout" -> removeShoutout(event);

            /*
            Add a member count
            Command: /addmembercount <channel>
             */
      case "addmembercount" -> {
        try (BufferedReader br = new BufferedReader(new FileReader(memberCountFile))) {
          if (br.readLine() != null) {
            event.reply(
                    "Member count already added please remove it first before adding a new one.")
                .queue();
            return;
          }
          try (FileWriter fileWriter = new FileWriter(memberCountFile)) {
            fileWriter.write(event.getOption("channel").getAsChannel().getId());
          }

          BotMain.scheduleUpdateMemberCount();

          event.reply("Member count successfully added!").queue();
        } catch (IOException e) {
          Logger.error(e);
        }
      }

            /*
            Simple ping to check if the bot is responding
            Command: /ping
             */
      case "ping" -> {
        long restPing = event.getJDA().getRestPing().complete();
        long gatewayPing = event.getJDA().getGatewayPing();
        event.reply("Rest ping: " + restPing + "\n" + "Gateway ping: " + gatewayPing).queue();
      }

      default -> Logger.error("No matching case found for {}", event.getName());
    }
  }

  private void addReactionRole(SlashCommandInteractionEvent event) {
    try (BufferedReader br = new BufferedReader(new FileReader(reactionRolesFile))) {
      String emoji = Objects.requireNonNull(event.getOption("emoji")).getAsString();

      Objects.requireNonNull(Objects.requireNonNull(event.getGuild()).getTextChannelById(
              Objects.requireNonNull(event.getOption("channel")).getAsChannel().getId()))
          .addReactionById(Objects.requireNonNull(event.getOption("messageid")).getAsLong(),
              Emoji.fromFormatted(emoji)).complete();

      CSVFormat csvFormat;
      if (br.readLine() == null) {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(";")
            .setHeader("messageId", "roleId", "emoji").build();
      } else {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(";").build();
      }

      try (FileWriter fileWriter = new FileWriter(reactionRolesFile, true);
           CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat)) {
        csvPrinter.printRecord(Objects.requireNonNull(event.getOption("messageid")).getAsString(),
            Objects.requireNonNull(event.getOption("role")).getAsString(),
            Objects.requireNonNull(event.getOption("emoji")).getAsString());

        reactionRoleManager.updateLists();
        event.reply("Reaction role has been added").queue();
      }
    } catch (ErrorResponseException e) {
      if (e.getErrorCode() == 10014) {
        event.reply("ERROR: Unknown Emoji. Can only use emojis that are either default " +
            "or have been added to the server!").queue();
      } else {
        event.reply("ERROR: Unknown message. Make sure the message is actually " +
            "in the provided channel").queue();
      }
    } catch (IOException e) {
      Logger.error(e);
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

        MessageChannel channel = Objects.requireNonNull(
            Objects.requireNonNull(event.getGuild(), "Guild must not be null")
                .getChannelById(GuildMessageChannel.class,
                    Objects.requireNonNull(event.getOption("channel"), "Channel must not be null")
                        .getAsChannel().getId()), "Message channel must not be null");

        validateRoles(event, roleList);

        String messageId;
        if (event.getOption("embed").getAsBoolean()) {
          messageId = channel.sendMessageEmbeds(embedBuilder.build()).complete().getId();
          event.reply("Embed created in " + channel.getAsMention() + " without role reaction.")
              .queue();
        } else {
          messageId = channel.sendMessage(message).complete().getId();
          event.reply("Message sent in " + channel.getAsMention() + " without role reaction.")
              .queue();
        }
        handleReactionRoles(messageId, roleList, emojiList, channel);
      } else {
        throw new InvalidParameters(1001);
      }
    } catch (InvalidParameters e) {
      if (e.getCode() == 1001) {
        event.reply("ERROR: Amount of roles and emojis don't match! Command has been canceled.")
            .queue();
      } else if (e.getCode() == 1002) {
        event.reply("ERROR: Faulty role input!").queue();
      }
    }
  }

  private void validateRoles(SlashCommandInteractionEvent event, List<String> roleList)
      throws InvalidParameters {
    for (String role : roleList) {
      if (role.contains("@")) {
        if (event.getGuild().getRoleById(
            Long.parseLong(role.substring(role.indexOf("&") + 1, role.lastIndexOf(">")))) == null) {
          throw new InvalidParameters(1002);
        }
      } else {
        if (event.getGuild().getRoleById(Long.parseLong(role)) == null) {
          throw new InvalidParameters(1002);
        }
      }
    }
  }

  private void handleReactionRoles(String messageId, List<String> roleList, List<String> emojiList,
                                   MessageChannel channel) {
    try (BufferedReader br = new BufferedReader(new FileReader(reactionRolesFile))) {
      CSVFormat csvFormat;
      if (br.readLine() == null) {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(";")
            .setHeader("messageId", "roleId", "emoji").build();
      } else {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(";").build();
      }

      try (FileWriter fileWriter = new FileWriter(reactionRolesFile, true);
           CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat)) {
        for (int i = 0; i < emojiList.size(); i++) {
          csvPrinter.printRecord(messageId, roleList.get(i).strip(), emojiList.get(i).strip());
          channel.addReactionById(messageId, Emoji.fromFormatted(emojiList.get(i).strip()))
              .complete();
        }
      }
      reactionRoleManager.updateLists();
    } catch (IOException e) {
      Logger.error(e);
    }
  }

  private void addYoutubeNotification(SlashCommandInteractionEvent event) {
    try (BufferedReader br = new BufferedReader(new FileReader(youtubeFile))) {
      validateRoles(event, Collections.singletonList(event.getOption("role").getAsString()));
      CSVFormat csvFormat;
      if (br.readLine() == null) {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(";")
            .setHeader("channel", "message", "ytchannelid", "role", "videoid").build();
      } else {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(";").build();
      }

      try (FileWriter fileWriter = new FileWriter(youtubeFile, true);
           CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat)) {
        csvPrinter.printRecord(event.getOption("channel").getAsString(),
            event.getOption("message").getAsString(), event.getOption("ytchannelid").getAsString(),
            event.getOption("role").getAsString(), "null");
      }
      event.reply("Youtube notification added").queue();
    } catch (IOException e) {
      Logger.error(e);
    } catch (InvalidParameters e) {
      event.reply("ERROR: Faulty role input!").queue();
    }
  }

  private void addTwitchNotification(@NotNull SlashCommandInteractionEvent event) {
    try (BufferedReader br = new BufferedReader(new FileReader(twitchFile))) {
      String role = "";
      if (event.getOption("role") != null) {
        validateRoles(event, Collections.singletonList(event.getOption("role").getAsString()));
        role = event.getOption("role").getAsString();
      }
      CSVFormat csvFormat;
      if (br.readLine() == null) {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(";")
            .setHeader("channel", "message", "username", "role").build();
      } else {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(";").build();
      }

      try (FileWriter fileWriter = new FileWriter(twitchFile, true);
           CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat)) {
        csvPrinter.printRecord(event.getOption("channel").getAsString(),
            event.getOption("message").getAsString(),
            event.getOption("username").getAsString().toLowerCase(), role);
      }
      twitch.updateLists();
      event.reply("Twitch notification added").queue();
    } catch (IOException e) {
      Logger.error(e);
    } catch (InvalidParameters e) {
      event.reply("ERROR: Faulty role input!").queue();
    }
  }

  @SuppressWarnings("java:S3776")
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
        embedBuilder.setDescription(oldEmbed.getEmbeds().get(0).getDescription());
      }
      if (event.getOption("image") != null || event.getOption("title") != null ||
          event.getOption("message") != null) {
        channel.editMessageEmbedsById(messageId, embedBuilder.build()).complete();
        event.reply("Embed edited.").queue();
      } else {
        event.reply(
                "Can't edit embed without at least one of the following: " + "message, title, image")
            .queue();
      }
    } else {
      if (event.getOption("message") != null) {
        channel.editMessageById(messageId,
            event.getOption("message").getAsString().replace("\\n", "\n")).complete();
        event.reply("Message edited.").queue();
      } else {
        event.reply("Can't edit message without message content").queue();
      }
    }
  }

  private void addShoutout(SlashCommandInteractionEvent event) {
    List<String> usernameList =
        new ArrayList<>(Arrays.asList(event.getOption("username").getAsString().split(";")));

    try (BufferedReader br = new BufferedReader(new FileReader(shoutoutFile))) {
      CSVFormat csvFormat;
      if (br.readLine() == null) {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader("username").build();
      } else {
        csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(";").build();
      }

      try (FileWriter fileWriter = new FileWriter(shoutoutFile, true);
           CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat)) {
        for (String s : usernameList) {
          csvPrinter.printRecord(s.strip().toLowerCase());
        }
      }

      twitch.updateLists();
      event.reply("Shout out added").queue();
    } catch (IOException e) {
      Logger.error(e);
    }
  }

  private void removeShoutout(SlashCommandInteractionEvent event) {
    List<String> usernames = twitch.getShoutoutNames();
    if (usernames.contains(event.getOption("username").getAsString())) {
      usernames.remove(event.getOption("username").getAsString());
      try {
        CSVFormat csvFormat =
            CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader("username").build();

        try (FileWriter fileWriter = new FileWriter(shoutoutFile);
             CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat)) {
          for (String s : usernames) {
            csvPrinter.printRecord(s);
          }
        }

        twitch.updateLists();

        event.reply("User has successfully been removed!").queue();
      } catch (IOException e) {
        Logger.error(e);
      }
    } else {
      event.reply(
              "Provided username is not in the list! (use /displayshoutout to see all usernames)")
          .queue();
    }
  }

  @Override
  public void onGuildReady(@NotNull GuildReadyEvent event) {
    List<CommandData> commandDataList = new ArrayList<>();

    // Command: /addreactionrole <channel> <messageid> <@role> <emoji>
    commandDataList.add(Commands.slash("addreactionrole", "Add a new reaction that assigns a role")
        .addOptions(new OptionData(OptionType.CHANNEL, "channel",
                "The ID of the channel that the message is in.", true).setChannelTypes(ChannelType.TEXT,
                ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
            new OptionData(OptionType.STRING, "messageid",
                "The ID of the message that this reaction should be added to.", true),
            new OptionData(OptionType.ROLE, "role", "The role that this reaction should give.",
                true),
            new OptionData(OptionType.STRING, "emoji", "The emoji that should be used.", true)));

    // Command: /createractionmessage <channel, message, embed:true/false> [title, roles, emojis, image]
    commandDataList.add(Commands.slash("createreactionmessage",
        "Create a new message or embed for adding reaction roles.").addOptions(
        new OptionData(OptionType.CHANNEL, "channel",
            "The channel that this message should be sent in", true).setChannelTypes(
            ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
        new OptionData(OptionType.STRING, "message",
            "The text that should be displayed in the message or embed. " +
                "Insert a \\n for a new line.", true), new OptionData(OptionType.BOOLEAN, "embed",
            "Whether the message should be an embed or not.", true),
        new OptionData(OptionType.STRING, "title", "Optional title for the embed."),
        new OptionData(OptionType.STRING, "roles",
            "Roles separated by ; in the same order as emojis."),
        new OptionData(OptionType.STRING, "emojis",
            "Emojis separated by ; in the same order as roles " +
                "(custom emojis only from this server)."),
        new OptionData(OptionType.STRING, "image",
            "Optional image url that is added to the embed")));

    // Command: /addyoutubenotif <channel, message, ytchannelid, role>
    commandDataList.add(
        Commands.slash("addyoutubenotif", "Add a YouTube notification output to a specific channel")
            .addOptions(new OptionData(OptionType.CHANNEL, "channel",
                    "The channel that the notification should be posted to.", true).setChannelTypes(
                    ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
                new OptionData(OptionType.STRING, "message",
                    "The message sent with the notification. Insert a \\n for a new line.", true),
                new OptionData(OptionType.STRING, "ytchannelid", "The ID of the YouTube channel.",
                    true), new OptionData(OptionType.ROLE, "role",
                    "The role that will be pinged with the notification.", true)));

    // Command: /editmessage <channel, messageid, message, embed:true/false> [title, image]
    commandDataList.add(
        Commands.slash("editmessage", "Edit the text of a specific message or embed").addOptions(
            new OptionData(OptionType.CHANNEL, "channel",
                "The channel that the message/embed is in.", true).setChannelTypes(ChannelType.TEXT,
                ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
            new OptionData(OptionType.STRING, "messageid",
                "The id of the message/embed that should be edited.", true),
            new OptionData(OptionType.BOOLEAN, "embed",
                "Whether the message that should be edited is an embed or not.", true),
            new OptionData(OptionType.STRING, "message",
                "The new message that replaces the old one. Old message if empty. " +
                    "Insert a \\n for a new line."),
            new OptionData(OptionType.STRING, "title", "Optional title for the embed."),
            new OptionData(OptionType.STRING, "image",
                "Optional image that is added to the embed")));

    // Command: /addtwitchnotif <channel, message, username, role>
    commandDataList.add(
        Commands.slash("addtwitchnotif", "Add a Twitch notification output to a specific channel")
            .addOptions(new OptionData(OptionType.CHANNEL, "channel",
                    "The channel that the notification should be posted to.", true).setChannelTypes(
                    ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD),
                new OptionData(OptionType.STRING, "message",
                    "The message sent with the notification. Insert a \\n for a new line.", true),
                new OptionData(OptionType.STRING, "username", "The username of the Twitch channel.",
                    true), new OptionData(OptionType.ROLE, "role",
                    "The role that will be pinged with the notification.")));

    // Command: /addshoutout <username>
    commandDataList.add(Commands.slash("addshoutout", "Add users to be shouted out on Twitch")
        .addOptions(
            new OptionData(OptionType.STRING, "username", "Usernames separated by ;", true)));

    // Command: /displayshoutout
    commandDataList.add(
        Commands.slash("displayshoutout", "Displays all usernames from the shoutout list"));

    // Command: /removeshoutout
    commandDataList.add(Commands.slash("removeshoutout", "Removes a user from the shoutout list")
        .addOptions(
            new OptionData(OptionType.STRING, "username", "The username to be removed", true)));

    // Command: /addmembercount
    commandDataList.add(Commands.slash("addmembercount", "Add a member count").addOptions(
        new OptionData(OptionType.CHANNEL, "channel",
            "The locked vc that displays the member count", true).setChannelTypes(
            ChannelType.VOICE)));

    // Command: /ping
    commandDataList.add(Commands.slash("ping", "Simple ping to check if the bot is responding."));

    event.getGuild().updateCommands().addCommands(commandDataList).queue();
  }
}
