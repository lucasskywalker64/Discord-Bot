package com.github.lucasskywalker64.listener.button;

import com.github.lucasskywalker64.buttons.ButtonRegistry;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ButtonListener extends ListenerAdapter {

    private final ButtonRegistry registry;

    public ButtonListener(ButtonRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        registry.dispatch(event);
    }
}
