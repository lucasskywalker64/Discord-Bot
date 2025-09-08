package com.github.lucasskywalker64.commands.general;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.repository.SettingsRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

public class GeneralMemberCountRemove implements SubcommandModule {

    private final SettingsRepository settingsRepository = SettingsRepository.getInstance();

    @Override public String getRootName() { return "general"; }
    @Override public String getSubcommandName() { return "removemembercount"; }
    @Override public String getDescription() { return "Removes the member count and deletes the voice channel"; }

    @Override public SubcommandData definition() { return new SubcommandData(getSubcommandName(), getDescription()); }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        try {
            if (!settingsRepository.get("member_count_channel").isEmpty()) {
                event.reply("No existing member count to remove.").setEphemeral(true).queue();
                return;
            }
            BotMain.removeMemberCount();
            settingsRepository.set("member_count_channel", "");
            event.reply("Member count successfully removed.").setEphemeral(true).queue();
        } catch (Exception e) {
            Logger.error(e);
            event.reply("ERROR: Failed to remove member count! Please contact the developer.")
                    .setEphemeral(true).queue();
        }
    }
}
