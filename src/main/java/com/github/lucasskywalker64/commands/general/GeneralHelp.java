package com.github.lucasskywalker64.commands.general;

import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand;
import net.dv8tion.jda.api.interactions.commands.Command.SubcommandGroup;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("DataFlowIssue")
public class GeneralHelp implements SubcommandModule {

    @Override public String getRootName() { return "general"; }
    @Override public String getSubcommandName() { return "help"; }
    @Override public String getDescription() { return "Displays a categorized list of commands"; }

    @Override public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        List<Command> commands = event.getGuild().retrieveCommands().complete();
        commands.sort(Comparator.comparing(Command::getName));

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Help");
        embed.setColor(Color.GREEN);

        for (Command root : commands) {
            String rootTitle = "/" + root.getName() + " — " + root.getDescription();

            List<String> lines = new ArrayList<>();

            // Ungrouped subcommands
            List<Subcommand> ungrouped = new ArrayList<>(root.getSubcommands());
            ungrouped.sort(Comparator.comparing(Subcommand::getName));
            for (Subcommand sub : ungrouped) {
                String mention = "</" + root.getName() + " " + sub.getName() + ":" + root.getId() + ">";
                String desc = sub.getDescription();
                lines.add("- " + mention + (desc.isEmpty() ? "" : " — " + desc));
            }

            // Grouped subcommands
            List<SubcommandGroup> groups = new ArrayList<>(root.getSubcommandGroups());
            groups.sort(Comparator.comparing(SubcommandGroup::getName));
            for (SubcommandGroup group : groups) {
                lines.add("" ); // visual spacer if previous lines exist will be collapsed by addFieldSafe title repeats
                String groupHeader = "[" + group.getName() + "]" + (group.getDescription().isEmpty() ? "" : " — " + group.getDescription());
                lines.add(groupHeader);

                List<Subcommand> subs = new ArrayList<>(group.getSubcommands());
                subs.sort(Comparator.comparing(Subcommand::getName));
                for (Subcommand sub : subs) {
                    String mention = "</" + root.getName() + " " + group.getName() + " " + sub.getName() + ":" + root.getId() + ">";
                    String desc = sub.getDescription();
                    lines.add("　• " + mention + (desc.isEmpty() ? "" : " — " + desc));
                }
            }

            if (lines.isEmpty()) {
                lines.add("(no subcommands)");
            }

            CommandUtil.addFieldSafe(embed, rootTitle, lines, false);
        }

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}
