package com.github.lucasskywalker64.persistence.data;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.List;

public interface Data<T extends Data<T>> {
    T self();

    T withMessage(String message);
    T withRoleId(String role);
    default void updateList(SlashCommandInteractionEvent event, List<T> updatedList, int index) {
        T data = self();
        if (event.getOption("message") != null) {
            data = data.withMessage(event.getOption("message").getAsString());
        }
        if (event.getOption("role") != null) {
            String roleId = event.getOption("role").getAsRole().getId();
            data = data.withRoleId(roleId);
        }
        updatedList.set(index, data);
    }
}
