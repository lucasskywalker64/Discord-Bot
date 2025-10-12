package com.github.lucasskywalker64.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.tinylog.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class CommandRegistry {

    private final Map<String, Map<String, List<SubcommandModule>>> tree = new HashMap<>();

    public CommandRegistry(Collection<SubcommandModule> modules) {
        for (SubcommandModule module : modules) {
            tree.computeIfAbsent(module.getRootName(),
                            rootName -> new HashMap<>())
                    .computeIfAbsent(Objects.toString(module.getGroupName(), ""),
                            groupName -> new ArrayList<>())
                    .add(module);
        }
        for (var byGroup : tree.values()) {
            for (var list : byGroup.values()) {
                var duplicate = list.stream()
                        .collect(Collectors.groupingBy(SubcommandModule::getSubcommandName, Collectors.counting()))
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getValue() > 1)
                        .toList();
                if (!duplicate.isEmpty()) {
                    throw new IllegalStateException("Duplicate subcommand(s) in root/group: " + duplicate);
                }
            }
        }
    }

    public List<CommandData> definitions() {
        List<CommandData> defs = new ArrayList<>();
        for (var entry : tree.entrySet()) {
            String root = entry.getKey();
            Map<String, List<SubcommandModule>> byGroup = entry.getValue();

            SlashCommandData rootData = Commands.slash(root, describeRoot(root));

            if (!"ticket".equals(root))
                rootData.setDefaultPermissions(DefaultMemberPermissions.DISABLED);

            List<SubcommandModule> ungrouped = byGroup.getOrDefault("", List.of());
            for (SubcommandModule module : ungrouped) rootData.addSubcommands(module.definition());

            for (var gEntry : byGroup.entrySet()) {
                String groupName = gEntry.getKey();
                if (groupName.isEmpty()) continue;
                SubcommandGroupData groupData = new SubcommandGroupData(groupName, describeGroup(root, groupName));
                for (SubcommandModule module : gEntry.getValue()) groupData.addSubcommands(module.definition());
                rootData.addSubcommandGroups(groupData);
            }
            defs.add(rootData);
        }
        defs.sort(Comparator.comparing(CommandData::getName));
        return defs;
    }

    public void dispatch(SlashCommandInteractionEvent event) {
        String root = event.getName();
        String group = Objects.toString(event.getSubcommandGroup(), "");
        String sub = event.getSubcommandName();

        Map<String, List<SubcommandModule>> byGroup = tree.get(root);
        if (byGroup == null) {
            Logger.error("No root handler for {}", root);
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }

        List<SubcommandModule> modules = byGroup.get(group);
        if (modules == null) {
            Logger.error("No group '{}' for root {}", group, root);
            event.reply("Something went wrong. Please contact the developer").setEphemeral(true).queue();
            return;
        }

        SubcommandModule target = modules.stream()
                .filter(m -> Objects.equals(m.getSubcommandName(), sub))
                .findFirst().orElse(null);
        if (target == null) {
            Logger.error("No subcommand '{}' (group '{}') for root {}", sub, group, root);
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }

        try {
            target.handle(event);
        } catch (Exception e) {
            Logger.error(e);
            event.reply("ERROR: Command failed. Please contact the developer.").setEphemeral(true).queue();
        }
    }

    private String describeRoot(String root) {
        return switch (root) {
            case "reactionrole" -> "Manage reaction roles";
            case "message"      -> "Message utilities";
            case "notif"        -> "Manage streaming/video notifications";
            case "shoutout"     -> "Manage Twitch shoutouts";
            case "general"      -> "General utilities";
            case "ticket"       -> "Ticket utilities";
            case "ticket-admin" -> "Ticket administration utilities";
            default -> "Commands";
        };
    }

    private String describeGroup(String root, String groupName) {
        return switch (root + ":" + groupName) {
            case "notif:youtube" -> "YouTube notifications";
            case "notif:twitch"  -> "Twitch notifications";
            default -> "Group: " + groupName;
        };
    }
}
