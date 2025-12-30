package com.github.lucasskywalker64.modals.twitch;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.modals.ModalModule;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.tinylog.Logger;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class TwitchCheckinModal implements ModalModule {

    @Override public String getId() { return "twitch:redeem:checkin:"; }

    @Override
    public void handle(ModalInteractionEvent event) {
        String rewardId = event.getModalId().split(":")[3];

        try {
            int count = Integer.parseInt(event.getValue("vip_count").getAsString());
            int days = Integer.parseInt(event.getValue("vip_days").getAsString());

            TwitchRepository.getInstance().saveRedemption(rewardId);
            TwitchRepository.getInstance().saveCheckinConfig(rewardId, count, days);

            BotMain.getContext().twitch().load();

            event.reply("Setup complete! Users get VIP for " + days + " days every " + count + " redemptions.")
                    .setEphemeral(true).queue();
        } catch (NumberFormatException e) {
            event.reply("Invalid numbers provided. Please try again.").setEphemeral(true).queue();
        } catch (Exception e) {
            Logger.error(e);
            event.reply(INTERNAL_ERROR).setEphemeral(true).queue();
        }
    }
}
