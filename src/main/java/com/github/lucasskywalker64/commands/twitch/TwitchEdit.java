package com.github.lucasskywalker64.commands.twitch;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.api.twitch.TwitchImpl;
import com.github.lucasskywalker64.commands.CommandUtil;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import com.github.lucasskywalker64.persistence.repository.TwitchRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

@SuppressWarnings("DataFlowIssue")
public class TwitchEdit implements SubcommandModule {

    private final TwitchRepository repository = TwitchRepository.getInstance();

    @Override public String getRootName() { return "twitch"; }
    @Override public String getSubcommandName() { return "edit"; }
    @Override public String getDescription() { return "Edit a Twitch notification"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOption(OptionType.STRING, "username", "The username of the Twitch channel.", true)
                .addOption(OptionType.STRING, "message", "The new message.")
                .addOption(OptionType.ROLE,   "role",    "The new role.");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        TwitchImpl twitch = BotMain.getContext().twitch();
        event.deferReply(true).queue();
        if (twitch == null) {
            CommandUtil.handleNoTwitchService(event);
            return;
        }
        try {
            List<TwitchData> list = repository.loadAll();
            if (list.isEmpty()) {
                event.getHook().sendMessage("There are no Twitch notifications.").queue();
                return;
            }
            String user = event.getOption("username").getAsString();
            int idx = IntStream.range(0, list.size())
                    .filter(i -> list.get(i).username().equalsIgnoreCase(user))
                    .findFirst().orElse(-1);
            if (idx == -1) {
                event.getHook().sendMessage(String.format("There is no notification for the user %s", user)).queue();
                return;
            }
            TwitchData data = list.get(idx);
            data.updateList(event, list, idx);
            repository.saveAll(list, false);
            twitch.load();
            event.getHook().sendMessage("Twitch notification edited.").queue();
        } catch (IOException e) {
            Logger.error(e);
            event.getHook().sendMessage("ERROR: Failed to edit Twitch notification. " +
                    "Please contact the developer.").queue();
        }
    }
}
