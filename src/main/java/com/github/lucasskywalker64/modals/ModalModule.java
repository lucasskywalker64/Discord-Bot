package com.github.lucasskywalker64.modals;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

public interface ModalModule {
    String getId();
    void handle(ModalInteractionEvent event);
}
