package com.github.lucasskywalker64.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class CommandRegistry {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<String, Map<String, Map<String, SubcommandModule>>> tree = new TreeMap<>();

    public CommandRegistry(Collection<SubcommandModule> modules) {
        for (var module : modules) {
            var group = Objects.toString(module.getGroupName(), "");
            var commands = tree.computeIfAbsent(module.getRootName(), ignored -> new TreeMap<>())
                    .computeIfAbsent(group, ignored -> new TreeMap<>());
            if (commands.putIfAbsent(module.getSubcommandName(), module) != null) {
                throw new IllegalStateException("Duplicate subcommand: " + module.getRootName()
                        + "/" + group + "/" + module.getSubcommandName());
            }
        }
    }

    public List<CommandData> definitions() {
        List<CommandData> definitions = new ArrayList<>();
        tree.forEach((root, groups) -> {
            var definition = Commands.slash(root, "general".equals(root) ? "General utilities" : "Commands")
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED);
            groups.forEach((group, modules) -> {
                if (group.isEmpty()) {
                    modules.values().forEach(module -> definition.addSubcommands(module.definition()));
                } else {
                    var groupDefinition = new SubcommandGroupData(group, "Group: " + group);
                    modules.values().forEach(module -> groupDefinition.addSubcommands(module.definition()));
                    definition.addSubcommandGroups(groupDefinition);
                }
            });
            definitions.add(definition);
        });
        return definitions;
    }

    public void dispatch(SlashCommandInteractionEvent event) {
        var module = find(event.getName(), event.getSubcommandGroup(), event.getSubcommandName());
        if (module == null) {
            event.reply("Unknown command.").setEphemeral(true).queue();
            return;
        }
        try {
            module.handle(event);
        } catch (RuntimeException failure) {
            log.warn("Failed to handle command {}", event.getFullCommandName(), failure);
            if (event.isAcknowledged()) {
                event.getHook().editOriginal("Command failed. Please try again.").queue();
            } else {
                event.reply("Command failed. Please try again.").setEphemeral(true).queue();
            }
        }
    }

    public void dispatchAutoComplete(CommandAutoCompleteInteractionEvent event) {
        var module = find(event.getName(), event.getSubcommandGroup(), event.getSubcommandName());
        if (module == null) {
            event.replyChoices(List.of()).queue();
            return;
        }
        try {
            module.handleAutoComplete(event);
        } catch (RuntimeException failure) {
            log.warn("Failed to autocomplete command {}", event.getFullCommandName(), failure);
            if (!event.isAcknowledged()) {
                event.replyChoices(List.of()).queue();
            }
        }
    }

    private SubcommandModule find(String root, String group, String subcommand) {
        if (subcommand == null) {
            return null;
        }
        return tree.getOrDefault(root, Map.of())
                .getOrDefault(Objects.toString(group, ""), Map.of()).get(subcommand);
    }
}
