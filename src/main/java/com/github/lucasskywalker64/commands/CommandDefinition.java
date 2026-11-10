package com.github.lucasskywalker64.commands;

import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public record CommandDefinition(Root root, Group group, SubcommandData subcommand) {

    public record Root(String name, String description, DefaultMemberPermissions defaultPermissions) {
    }

    public record Group(String name, String description) {
    }
}
