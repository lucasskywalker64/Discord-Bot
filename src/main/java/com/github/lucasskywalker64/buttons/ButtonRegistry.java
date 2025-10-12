package com.github.lucasskywalker64.buttons;

import com.github.lucasskywalker64.exceptions.RateLimitException;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.tinylog.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class ButtonRegistry {

    private final Map<String, ButtonModule> modules = new HashMap<>();

    public ButtonRegistry(Collection<ButtonModule> modules) {
        for (ButtonModule module : modules) {
            this.modules.putIfAbsent(module.getId(), module);
        }
    }

    public void dispatch(ButtonInteractionEvent event) {
        ButtonModule target = modules.get(event.getButton().getId());
        if (target == null) {
            Logger.error("Cannot find ButtonModule with id {}", event.getButton().getId());
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            return;
        }

        try {
            target.handle(event);
        } catch (RateLimitException e) {
            event.reply("You’ve reached the maximum number of open tickets. " +
                    "Please close one before opening another. Thanks for your understanding!")
                    .setEphemeral(true).queue();
        } catch (Exception e) {
            Logger.error(e);
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
        }
    }
}
