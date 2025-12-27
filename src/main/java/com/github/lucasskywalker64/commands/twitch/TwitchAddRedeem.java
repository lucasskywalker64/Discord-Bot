package com.github.lucasskywalker64.commands.twitch;

import com.github.lucasskywalker64.BotContext;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import com.github.twitch4j.helix.domain.CustomReward;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.sql.SQLException;
import java.util.List;

import static com.github.lucasskywalker64.BotConstants.INTERNAL_ERROR;

public class TwitchAddRedeem implements SubcommandModule {

    private final TwitchRepository repo = TwitchRepository.getInstance();
    private final BotContext context = BotMain.getContext();

    @Override public String getRootName() { return "twitch"; }
    @Override public String getSubcommandName() { return "add-redeem"; }
    @Override public String getDescription() { return "Add a channel point redeem for tracking"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(
                        OptionType.STRING,
                        "redeem",
                        "Start typing a Twitch redeem name",
                        true,
                        true);
    }

    @Override
    public void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (context.twitch() == null) return;
        String broadcasterId = context.twitch().getBroadcasterId();

        List<CustomReward> rewards = context.twitchCacheService().getOrFetch(broadcasterId);
        String input = event.getFocusedOption().getValue().toLowerCase();

        List<Command.Choice> choices = rewards.stream()
                .filter(reward -> reward.getTitle().toLowerCase().startsWith(input))
                .limit(25)
                .map(reward -> new Command.Choice(reward.getTitle(), reward.getId()))
                .toList();
        event.replyChoices(choices).queue();
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        if (context.twitch() == null) {
            CommandUtil.handleNoTwitchService(event);
            return;
        }
        List<String> existingRedemptionIds;
        try {
            existingRedemptionIds = repo.loadRedemptions();
        } catch (SQLException e) {
            Logger.error(e);
            event.getHook().sendMessage(INTERNAL_ERROR).queue();
            return;
        }
        String redemptionId = event.getOption("redeem").getAsString();
        if (existingRedemptionIds.contains(redemptionId)) {
            event.getHook().sendMessage("This redemption is already being tracked.").queue();
            return;
        }
        try {
            repo.saveRedemption(redemptionId);
        } catch (SQLException e) {
            Logger.error(e);
            event.getHook().sendMessage(INTERNAL_ERROR).queue();
            return;
        }
        context.twitch().load();
        event.getHook().sendMessage("Redemption added.").queue();
    }
}
