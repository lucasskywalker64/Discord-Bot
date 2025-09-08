package com.github.lucasskywalker64.commands.general;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.repository.SettingsRepository;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

@SuppressWarnings("DataFlowIssue")
public class GeneralMemberCountAdd implements SubcommandModule {

    private final SettingsRepository settingsRepository = SettingsRepository.getInstance();

    @Override public String getRootName() { return "general"; }
    @Override public String getSubcommandName() { return "addmembercount"; }
    @Override public String getDescription() { return "Add a member count"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "The vc that displays the member count", true)
                        .setChannelTypes(ChannelType.VOICE));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        try {
            if (!settingsRepository.get("member_count_channel").isEmpty()) {
                event.reply("Member count already added please remove it first before adding a new one.")
                        .setEphemeral(true).queue();
                return;
            }
            settingsRepository.set("member_count_channel", event.getOption("channel").getAsString());
            BotMain.scheduleUpdateMemberCount();
            event.reply("Member count successfully added!").setEphemeral(true).queue();
        } catch (Exception e) {
            Logger.error(e);
            event.reply("ERROR: Failed to add member count! Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }
}
