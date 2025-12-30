package com.github.lucasskywalker64.selectmenus;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

public interface SelectMenuModule {
    String getId();
    void handle(StringSelectInteractionEvent event);
}
