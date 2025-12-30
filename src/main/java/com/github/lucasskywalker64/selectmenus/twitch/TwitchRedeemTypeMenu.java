package com.github.lucasskywalker64.selectmenus.twitch;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import com.github.lucasskywalker64.selectmenus.SelectMenuModule;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.tinylog.Logger;

import java.sql.SQLException;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class TwitchRedeemTypeMenu implements SelectMenuModule {

    @Override public String getId() { return "twitch:redeem:type"; }

    @Override
    public void handle(StringSelectInteractionEvent event) {
        String rewardId = event.getComponentId().split(":")[3];
        String type = event.getValues().get(0);

        if ("FIRST".equals(type)) {
            try {
                TwitchRepository.getInstance().saveRedemption(rewardId);
                BotMain.getContext().twitch().load();
                event.reply("Setup complete! Added leaderboard tracking for this reward.")
                        .setEphemeral(true).queue();
            } catch (SQLException e) {
                Logger.error(e);
                event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
            }
        } else if ("CHECKIN".equals(type)) {
            TextInput countInput = TextInput.create("vip_count", "Redemptions required (X)",
                            TextInputStyle.SHORT)
                    .setPlaceholder("e.g., 10")
                    .setRequired(true)
                    .build();

            TextInput timeInput = TextInput.create("vip_days", "VIP duration in days (Y)",
                            TextInputStyle.SHORT)
                    .setPlaceholder("e.g., 30")
                    .setRequired(true)
                    .build();

            Modal modal = Modal.create("twitch:redeem:checkin:" + rewardId, "Check-in Configuration")
                    .addActionRow(countInput)
                    .addActionRow(timeInput)
                    .build();

            event.replyModal(modal).queue();
        }
    }
}
