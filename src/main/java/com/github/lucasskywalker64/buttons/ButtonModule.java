package com.github.lucasskywalker64.buttons;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public interface ButtonModule {
    String getId();
    void handle(ButtonInteractionEvent event);
}
