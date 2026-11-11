package com.github.lucasskywalker64.commands.general;

import com.github.lucasskywalker64.commands.CommandContext;
import com.github.lucasskywalker64.commands.CommandDefinition;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.guild.GuildConfiguration;
import com.github.lucasskywalker64.guild.GuildConfigurationRepository;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public class GeneralMemberCountAdd implements SubcommandModule {

    private final GuildConfigurationRepository configurations;
    private final Executor executor;

    public GeneralMemberCountAdd(GuildConfigurationRepository configurations, Executor executor) {
        this.configurations = configurations;
        this.executor = executor;
    }

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition(
                new CommandDefinition.Root("general", "General utilities", DefaultMemberPermissions.DISABLED),
                null, new SubcommandData("addmembercount", "Read or set the member-count voice channel")
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Voice channel to store; omit to read", false)
                        .setChannelTypes(ChannelType.VOICE)));
    }

    @Override
    public CompletionStage<?> handle(CommandContext context, SlashCommandInteractionEvent event) {
        if (!Long.toUnsignedString(event.getGuild().getOwnerIdLong()).equals(context.actorId())) {
            return event.reply("Only the current guild owner can read or change this setting.")
                    .setEphemeral(true).submit();
        }
        var option = event.getOption("channel");
        String channelId = null;
        if (option != null) {
            var channel = option.getAsChannel();
            if (channel.getType() != ChannelType.VOICE || !context.guildId().equals(channel.getGuild().getId())
                    || event.getGuild().getVoiceChannelById(channel.getId()) == null) {
                return event.reply("Choose an existing voice channel in this guild.").setEphemeral(true).submit();
            }
            channelId = channel.getId();
        }
        String selectedChannelId = channelId;
        return event.deferReply(true).submit().thenCompose(hook -> CompletableFuture
                .supplyAsync(() -> readOrSet(context.guildId(), selectedChannelId), executor)
                .thenCompose(message -> hook.editOriginal(message).submit()));
    }

    private String readOrSet(String guildId, String channelId) {
        var configuration = configurations.findById(guildId).orElseThrow();
        if (channelId != null) {
            configurations.saveAndFlush(new GuildConfiguration(guildId, channelId));
            return "Member-count voice channel set to <#%s>.".formatted(channelId);
        }
        var storedChannel = configuration.getMemberCountVoiceChannelId();
        return storedChannel == null ? "Member-count voice channel is not set."
                : "Member-count voice channel: <#%s>.".formatted(storedChannel);
    }
}
