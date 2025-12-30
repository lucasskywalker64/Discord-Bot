package com.github.lucasskywalker64.listener;

import com.github.lucasskywalker64.selectmenus.SelectMenuRegistry;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;

public class SelectMenuListener extends ListenerAdapter {

    private final SelectMenuRegistry registry;

    public SelectMenuListener(SelectMenuRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onStringSelectInteraction(@NonNull StringSelectInteractionEvent event) {
        registry.dispatch(event);
    }
}
