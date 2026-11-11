package com.github.lucasskywalker64.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

public class CommandRegistry {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<CommandPath, SubcommandModule> modules = new HashMap<>();
    private final List<CommandData> definitions;

    public CommandRegistry(Collection<SubcommandModule> modules) {
        Map<String, SlashCommandData> roots = new TreeMap<>();
        Map<String, Map<String, SubcommandGroupData>> groups = new TreeMap<>();
        for (var module : modules) {
            var definition = module.definition();
            var root = definition.root();
            var group = definition.group();
            var subcommand = SubcommandData.fromData(definition.subcommand().toData());
            var path = new CommandPath(root.name(), group == null ? "" : group.name(), subcommand.getName());
            if (this.modules.putIfAbsent(path, module) != null) {
                throw new IllegalStateException("Duplicate subcommand: " + path.root() + "/" + path.group() + "/" + path.subcommand());
            }
            var rootData = roots.computeIfAbsent(root.name(), ignored -> Commands.slash(root.name(), root.description())
                    .setDefaultPermissions(root.defaultPermissions()));
            if (!rootData.getDescription().equals(root.description())
                    || !rootData.getDefaultPermissions().equals(root.defaultPermissions())) {
                throw new IllegalStateException("Conflicting root metadata: " + root.name());
            }
            if (group == null) {
                rootData.addSubcommands(subcommand);
            } else {
                var groupData = groups.computeIfAbsent(root.name(), ignored -> new TreeMap<>())
                        .computeIfAbsent(group.name(), ignored -> new SubcommandGroupData(group.name(), group.description()));
                if (!groupData.getDescription().equals(group.description())) {
                    throw new IllegalStateException("Conflicting group metadata: " + root.name() + "/" + group.name());
                }
                groupData.addSubcommands(subcommand);
            }
        }
        groups.forEach((root, rootGroups) -> {
            var rootData = roots.get(root);
            for (var subcommand : rootData.getSubcommands()) {
                if (rootGroups.containsKey(subcommand.getName())) {
                    throw new IllegalStateException("Conflicting subcommand and group name: " + root + "/" + subcommand.getName());
                }
            }
            rootData.addSubcommandGroups(rootGroups.values());
        });
        definitions = List.copyOf(roots.values());
    }

    public List<CommandData> definitions() {
        return definitions.stream().map(definition -> CommandData.fromData(definition.toData())).toList();
    }

    public void dispatch(SlashCommandInteractionEvent event) {
        var module = find(event.getName(), event.getSubcommandGroup(), event.getSubcommandName());
        if (module == null) {
            event.reply("Unknown command.").setEphemeral(true).queue();
            return;
        }
        execute(() -> module.handle(new CommandContext(event.getGuild().getId(), event.getUser().getId()), event), failure -> {
            log.warn("Failed to handle command {}", event.getFullCommandName(), failure);
            if (event.isAcknowledged()) {
                return event.getHook().editOriginal("Command failed. Please try again.").submit();
            }
            return event.reply("Command failed. Please try again.").setEphemeral(true).submit();
        });
    }

    public void dispatchAutoComplete(CommandAutoCompleteInteractionEvent event) {
        var module = find(event.getName(), event.getSubcommandGroup(), event.getSubcommandName());
        if (module == null) {
            event.replyChoices(List.of()).queue();
            return;
        }
        execute(() -> module.handleAutoComplete(
                new CommandContext(event.getGuild().getId(), event.getUser().getId()), event), failure -> {
            log.warn("Failed to autocomplete command {}", event.getFullCommandName(), failure);
            if (!event.isAcknowledged()) {
                return event.replyChoices(List.of()).submit();
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private void execute(Supplier<CompletionStage<?>> operation, Function<Throwable, CompletionStage<?>> recover) {
        CompletableFuture.completedFuture(null)
                .thenCompose(ignored -> operation.get())
                .handle((ignored, failure) -> failure == null ? CompletableFuture.completedFuture(null) : recover.apply(failure))
                .thenCompose(response -> response)
                .exceptionally(failure -> {
                    log.warn("Failed to send command failure response", failure);
                    return null;
                });
    }

    private SubcommandModule find(String root, String group, String subcommand) {
        if (subcommand == null) {
            return null;
        }
        return modules.get(new CommandPath(root, Objects.toString(group, ""), subcommand));
    }

    private record CommandPath(String root, String group, String subcommand) {
    }
}
