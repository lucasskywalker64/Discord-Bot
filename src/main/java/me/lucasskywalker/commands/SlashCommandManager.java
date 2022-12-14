package me.lucasskywalker.commands;

import me.lucasskywalker.BotMain;
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
import java.util.List;

public class SlashCommandManager extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "addreactionrole" -> {
                /*
                Add a reaction to a message which can be used by members to assign a role to themselves
                Command: /addReactionRole <channelid> <messageid> <roleid> <emoji>
                */
                String emoji = event.getOption("emoji").getAsString();
                try {
                    if (emoji.endsWith(">")) {
                        long emojiId = Long.parseLong(emoji.substring(emoji.lastIndexOf(":") + 1, emoji.length() - 1));
                        event.getGuild().getTextChannelById(event.getOption("channelid").getAsLong())
                                .addReactionById(event.getOption("messageid").getAsLong(),
                                        event.getGuild().getEmojiById(emojiId)).queue();

                    } else
                        event.getGuild().getTextChannelById(event.getOption("channelid").getAsLong())
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
        }
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        List<CommandData> commandDataList = new ArrayList<>();

        commandDataList.add(Commands.slash("addreactionrole", "Add a new reaction that assigns a role")
                .addOptions(
                        new OptionData(OptionType.STRING, "channelid",
                                "The ID of the channel that the message is in", true),
                        new OptionData(OptionType.STRING, "messageid",
                                "The ID of the message that this reaction should be added to", true),
                        new OptionData(OptionType.ROLE, "role",
                                "The role that this reaction should give", true),
                        new OptionData(OptionType.STRING, "emoji",
                                "The emoji that should be used", true)));

        event.getGuild().updateCommands().addCommands(commandDataList).queue();
    }
}
