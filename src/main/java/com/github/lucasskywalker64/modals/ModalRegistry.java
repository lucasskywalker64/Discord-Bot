package com.github.lucasskywalker64.modals;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.tinylog.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class ModalRegistry {

    private final Map<String, ModalModule> modules = new HashMap<>();

    public ModalRegistry(Collection<ModalModule> modules) {
        for (ModalModule module : modules) {
            this.modules.putIfAbsent(module.getId(), module);
        }
    }

    public void dispatch(ModalInteractionEvent event) {
        ModalModule target = modules.get(event.getModalId());
        if (target == null) {
            Logger.error("Cannot find ModalModule with id {}", event.getModalId());
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }

        try{
            target.handle(event);
        } catch (Exception e) {
            Logger.error(e);
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
        }
    }
}
