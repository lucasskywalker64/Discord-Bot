package me.lucasskywalker.commands;

import me.lucasskywalker.BotMain;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SlashCommandManager extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "addreactionrole" -> {
                /*
                Add a reaction to a message which can be used by members to assign a role to themselves
                Command: /addreactionrole <channel> <messageid> <roleid> <emoji>
                */
                String emoji = event.getOption("emoji").getAsString();
                try {
                    event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId())
                            .addReactionById(event.getOption("messageid").getAsLong(),
                                    Emoji.fromFormatted(emoji)).queue();

                    File filePath = new File(new File(BotMain.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                            .getParentFile().getPath() + "/reaction-roles.csv");

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
                } catch (IllegalArgumentException e) {
                    event.reply("ERROR: Can only use emojis that are either default " +
                            "or have been added to the server!").queue();
                } catch (URISyntaxException | IOException e) {
                    e.printStackTrace();
                }
            }

            case "createreactionmessage" -> {
                /*
                Create a new embed or message in the given channel with optional roles and emojis to add reaction roles
                Command: /createractionmessage <channel, message, embed:true/false> [title, roles, emojis]
                 */
                if(event.getOption("embed").getAsBoolean()) {
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    if(event.getOption("title") != null)
                        embedBuilder.setTitle(event.getOption("title").getAsString());
                    String message = event.getOption("message").getAsString()
                            .replace("\\n", "\n");
                    embedBuilder.setDescription(message);

                    if(event.getOption("roles") != null && event.getOption("emojis") != null) {
                        List<String> roleList = new ArrayList<>(Arrays.asList(event.getOption("roles")
                                .getAsString().split(";")));
                        List<String> emojiList = new ArrayList<>(Arrays.asList(event.getOption("emojis")
                                .getAsString().split(";")));

                        AtomicReference<String> messageId = new AtomicReference<>();
                        event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId())
                                .sendMessageEmbeds(embedBuilder.build()).queue(message1 ->
                                        messageId.set(message1.getId()));
                        try {
                            Thread.sleep(1000);

                            File filePath = new File(new File(BotMain.class.getProtectionDomain()
                                    .getCodeSource().getLocation().toURI())
                                    .getParentFile().getPath() + "/reaction-roles.csv");

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

                            for(int i = 0; i < emojiList.size(); i++) {
                                csvPrinter.printRecord(messageId, roleList.get(i), emojiList.get(i));

                                event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel()
                                        .getId()).addReactionById(messageId.get(),
                                        Emoji.fromFormatted(emojiList.get(i))).queue();
                            }
                            csvPrinter.close(true);
                        } catch (InterruptedException | IOException | URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    } else
                        event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId())
                                .sendMessageEmbeds(embedBuilder.build()).queue();

                    event.reply("Embed created in " + event.getOption("channel").getAsChannel()
                            .getAsMention()).queue();
                } else {
                    String message = "";
                    if(event.getOption("title") != null)
                        message = event.getOption("title").getAsString() + "\n";
                    message = message + event.getOption("message").getAsString()
                            .replace("\\n", "\n");

                    if(event.getOption("roles") != null && event.getOption("emojis") != null) {
                        List<String> roleList = new ArrayList<>(Arrays.asList(event.getOption("roles")
                                .getAsString().split(";")));
                        List<String> emojiList = new ArrayList<>(Arrays.asList(event.getOption("emojis")
                                .getAsString().split(";")));

                        AtomicReference<String> messageId = new AtomicReference<>();
                        event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId())
                                .sendMessage(message).queue(message1 ->
                                        messageId.set(message1.getId()));
                        try {
                            Thread.sleep(1000);

                            File filePath = new File(new File(BotMain.class.getProtectionDomain()
                                    .getCodeSource().getLocation().toURI())
                                    .getParentFile().getPath() + "/reaction-roles.csv");

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

                            for(int i = 0; i < emojiList.size(); i++) {
                                csvPrinter.printRecord(messageId, roleList.get(i), emojiList.get(i));

                                event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel()
                                        .getId()).addReactionById(messageId.get(),
                                        Emoji.fromFormatted(emojiList.get(i))).queue();
                            }
                            csvPrinter.close(true);
                        } catch (InterruptedException | IOException | URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    } else
                        event.getGuild().getTextChannelById(event.getOption("channel").getAsChannel().getId())
                                .sendMessage(message).queue();

                    event.reply("Message sent in " + event.getOption("channel").getAsChannel()
                            .getAsMention()).queue();
                }
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
                                "The ID of the channel that the message is in", true),
                        new OptionData(OptionType.STRING, "messageid",
                                "The ID of the message that this reaction should be added to", true),
                        new OptionData(OptionType.ROLE, "role",
                                "The role that this reaction should give", true),
                        new OptionData(OptionType.STRING, "emoji",
                                "The emoji that should be used", true)));

        // Command: /createractionmessage <channel, message, embed:true/false> [title, roles, emojis]
        commandDataList.add(Commands.slash("createreactionmessage",
                "Create a new message or embed for adding reaction roles")
                .addOption(OptionType.CHANNEL, "channel",
                        "The channel that this message should be sent in", true)
                .addOption(OptionType.STRING, "message",
                        "The text that should be displayed in the message or embed. " +
                                "Insert a \\n for a new line", true)
                .addOption(OptionType.BOOLEAN, "embed",
                        "Whether the message should be an embed or not", true)
                .addOption(OptionType.STRING, "title", "Optional title for the embed")
                .addOption(OptionType.STRING, "roles",
                        "Roles separated by ; in the same order as emojis")
                .addOption(OptionType.STRING, "emojis",
                        "Emoji ID for custom emoji (from this server) or emoji directly if default " +
                                "separated by ;"));

        event.getGuild().updateCommands().addCommands(commandDataList).queue();
    }
}
