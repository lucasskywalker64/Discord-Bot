package com.github.lucasskywalker64.selectmenus;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SelectMenuRegistry {

    private final Map<String, SelectMenuModule> modules = new HashMap<>();

    public SelectMenuRegistry(Collection<SelectMenuModule> modules) {
        for (SelectMenuModule module : modules)
            this.modules.putIfAbsent(module.getId(), module);
    }

    public void dispatch(StringSelectInteractionEvent event) {
        for (SelectMenuModule module : modules.values()) {
            if (event.getComponentId().startsWith(module.getId())) {
                module.handle(event);
                return;
            }
        }
    }
}
