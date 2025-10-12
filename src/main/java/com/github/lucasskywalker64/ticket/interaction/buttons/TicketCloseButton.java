package com.github.lucasskywalker64.ticket.interaction.buttons;

import com.github.lucasskywalker64.buttons.ButtonModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.*;

public class TicketCloseButton implements ButtonModule {


    @Override
    public String getId() { return "ticketClose"; }

    @Override
    public void handle(ButtonInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.GREEN);
        embed.setTitle("Close Confirmation");
        embed.setDescription("Please confirm that you want to close this ticket");
        ActionRow action = ActionRow.of(
                Button.success("ticketCloseConfirm", "✔ Close")
        );
        event.replyEmbeds(embed.build()).setComponents(action).setEphemeral(true).queue();
    }
}
