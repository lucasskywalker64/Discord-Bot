package com.github.lucasskywalker64.listener.modal;

import com.github.lucasskywalker64.modals.ModalRegistry;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;

public class ModalListener extends ListenerAdapter {

    private final ModalRegistry registry;

    public ModalListener(ModalRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        registry.dispatch(event);
    }
}
