package com.github.lucasskywalker64.persistence.data;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public interface Data<T extends Data<T>> {
    T self();

    T withMessage(String message);
    T withRole(String role);
    default void updateList(SlashCommandInteractionEvent event, List<T> updatedList, int index,
                            BiConsumer<SlashCommandInteractionEvent, List<String>> validator) {
        T data = self();
        if (event.getOption("message") != null) {
            data = data.withMessage(event.getOption("message").getAsString());
        }
        if (event.getOption("role") != null) {
            validator.accept(event, Collections.singletonList(event.getOption("role").getAsString()));
            String role = event.getOption("role").getAsString();
            data = data.withRole(role.contains("@") ?
                    role.substring(role.indexOf("&") + 1, role.indexOf(">"))
                    : role);
        }
        updatedList.set(index, data);
    }
}
