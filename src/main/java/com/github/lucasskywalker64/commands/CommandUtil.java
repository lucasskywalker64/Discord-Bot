package com.github.lucasskywalker64.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.Emoji.Type;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.Command.Option;
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand;
import net.dv8tion.jda.api.interactions.commands.Command.SubcommandGroup;
import net.dv8tion.jda.api.interactions.commands.build.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class CommandUtil {

    public static boolean validateEmojis(Guild guild, List<Emoji> emojiList) {
        boolean valid = true;
        for (Emoji emoji : emojiList) {
            valid = emoji.getType().equals(Type.UNICODE) || guild.getEmojiById(((CustomEmoji) emoji).getId()) != null;
            if (!valid) break;
        }
        return valid;
    }

    public static void addFieldSafe(EmbedBuilder embed, String title, List<String> lines, boolean inline) {
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

    public static boolean commandListsMatch(List<Command> existing, List<CommandData> updated) {
        if (existing.size() != updated.size()) return false;

        existing.sort(Comparator.comparing(Command::getName));
        updated.sort(Comparator.comparing(CommandData::getName));

        for (int i = 0; i < existing.size(); i++) {
            Command exRoot = existing.get(i);
            CommandData upRootData = updated.get(i);
            if (!(upRootData instanceof SlashCommandData upRoot)) return false; // We only use slash commands

            if (!Objects.equals(exRoot.getName(), upRoot.getName())) return false;
            if (!Objects.equals(exRoot.getDescription(), upRoot.getDescription())) return false;

            // Compare ungrouped subcommands
            List<Subcommand> exUngrouped = new ArrayList<>(exRoot.getSubcommands());
            List<SubcommandData> upUngrouped = new ArrayList<>(upRoot.getSubcommands());
            if (exUngrouped.size() != upUngrouped.size()) return false;
            exUngrouped.sort(Comparator.comparing(Subcommand::getName));
            upUngrouped.sort(Comparator.comparing(SubcommandData::getName));
            for (int s = 0; s < exUngrouped.size(); s++) {
                if (!subcommandMatches(exUngrouped.get(s), upUngrouped.get(s))) return false;
            }

            // Compare subcommand groups
            List<SubcommandGroup> exGroups = new ArrayList<>(exRoot.getSubcommandGroups());
            List<SubcommandGroupData> upGroups = new ArrayList<>(upRoot.getSubcommandGroups());
            if (exGroups.size() != upGroups.size()) return false;
            exGroups.sort(Comparator.comparing(SubcommandGroup::getName));
            upGroups.sort(Comparator.comparing(SubcommandGroupData::getName));

            for (int g = 0; g < exGroups.size(); g++) {
                SubcommandGroup exG = exGroups.get(g);
                SubcommandGroupData upG = upGroups.get(g);

                if (!Objects.equals(exG.getName(), upG.getName())) return false;
                if (!Objects.equals(exG.getDescription(), upG.getDescription())) return false;

                List<Subcommand> exSubs = new ArrayList<>(exG.getSubcommands());
                List<SubcommandData> upSubs = new ArrayList<>(upG.getSubcommands());
                if (exSubs.size() != upSubs.size()) return false;
                exSubs.sort(Comparator.comparing(Subcommand::getName));
                upSubs.sort(Comparator.comparing(SubcommandData::getName));

                for (int s = 0; s < exSubs.size(); s++) {
                    if (!subcommandMatches(exSubs.get(s), upSubs.get(s))) return false;
                }
            }

            // Root-level options (rare when using subcommands, but keep parity if any are present)
            List<Option> exRootOpts = new ArrayList<>(exRoot.getOptions());
            List<OptionData> upRootOpts = new ArrayList<>(upRoot.getOptions());
            if (exRootOpts.size() != upRootOpts.size()) return false;
            exRootOpts.sort(Comparator.comparing(Option::getName));
            upRootOpts.sort(Comparator.comparing(OptionData::getName));
            for (int o = 0; o < exRootOpts.size(); o++) {
                if (!optionMatches(exRootOpts.get(o), upRootOpts.get(o))) return false;
            }
        }
        return true;
    }

    private static boolean subcommandMatches(Subcommand ex, SubcommandData up) {
        if (!Objects.equals(ex.getName(), up.getName())) return false;
        if (!Objects.equals(ex.getDescription(), up.getDescription())) return false;

        // Options inside subcommand
        List<Option> exOpts = new ArrayList<>(ex.getOptions());
        List<OptionData> upOpts = new ArrayList<>(up.getOptions());
        if (exOpts.size() != upOpts.size()) return false;
        exOpts.sort(Comparator.comparing(Option::getName));
        upOpts.sort(Comparator.comparing(OptionData::getName));

        for (int i = 0; i < exOpts.size(); i++) {
            if (!optionMatches(exOpts.get(i), upOpts.get(i))) return false;
        }
        return true;
    }

    private static boolean optionMatches(Option ex, OptionData up) {
        if (!Objects.equals(ex.getName(), up.getName())) return false;
        if (!Objects.equals(ex.getDescription(), up.getDescription())) return false;
        if (!Objects.equals(ex.getType(), up.getType())) return false;
        if (ex.isRequired() != up.isRequired()) return false;

        // Autocomplete
        if (ex.isAutoComplete() != up.isAutoComplete()) return false;

        // Channel types (order-independent)
        var exCh = ex.getChannelTypes();
        var upCh = up.getChannelTypes();
        if (!exCh.isEmpty() || !upCh.isEmpty()) {
            var exSet = new java.util.HashSet<>(exCh);
            var upSet = new java.util.HashSet<>(upCh);
            if (!exSet.equals(upSet)) return false;
        }

        // Choices (order-independent by name+value)
        List<Choice> exChoices = ex.getChoices();
        List<Choice> upChoices = up.getChoices();
        if (!exChoices.isEmpty() || !upChoices.isEmpty()) {
            if (exChoices.size() != upChoices.size()) return false;
            var exSet = exChoices.stream()
                    .map(c -> c.getName() + "::" + c.getAsString())
                    .collect(java.util.stream.Collectors.toSet());
            var upSet = upChoices.stream()
                    .map(c -> c.getName() + "::" + c.getAsString())
                    .collect(java.util.stream.Collectors.toSet());
            if (!exSet.equals(upSet)) return false;
        }

        // Numeric min/max (if present)
        if (ex.getMinValue() != null || up.getMinValue() != null) {
            if (!Objects.equals(ex.getMinValue(), up.getMinValue())) return false;
        }
        if (ex.getMaxValue() != null || up.getMaxValue() != null) {
            if (!Objects.equals(ex.getMaxValue(), up.getMaxValue())) return false;
        }

        // String length min/max (if present in your JDA version)
        if (ex.getMinLength() != null || up.getMinLength() != null) {
            if (!Objects.equals(ex.getMinLength(), up.getMinLength())) return false;
        }
        if (ex.getMaxLength() != null || up.getMaxLength() != null) {
            if (!Objects.equals(ex.getMaxLength(), up.getMaxLength())) return false;
        }

        return true;
    }
}
