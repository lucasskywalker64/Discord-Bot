package com.github.lucasskywalker64.listener.command;

import com.github.lucasskywalker64.commands.RootRegistry;

import java.awt.*;
import java.util.*;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class SlashCommandListener extends ListenerAdapter {

    private final RootRegistry registry;

    public SlashCommandListener(RootRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        registry.dispatch(event);
    }
}
