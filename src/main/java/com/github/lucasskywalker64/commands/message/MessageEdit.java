package com.github.lucasskywalker64.commands.message;

import com.github.lucasskywalker64.commands.SubcommandModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

@SuppressWarnings("DataFlowIssue")
public class MessageEdit implements SubcommandModule {

    @Override public String getRootName() { return "message"; }
    @Override public String getSubcommandName() { return "edit"; }
    @Override public String getDescription() { return "Edit the text of a specific message or embed"; }

    @Override
    public SubcommandData definition() {
        return new SubcommandData(getSubcommandName(), getDescription())
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Channel containing the message", true)
                        .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD))
                .addOption(OptionType.STRING, "messageid", "The id of the message/embed that should be edited.", true)
                .addOption(OptionType.BOOLEAN, "embed", "Whether the message is an embed", true)
                .addOption(OptionType.STRING, "message", "New message (use \\n for newline)")
                .addOption(OptionType.STRING, "title", "Embed title")
                .addOption(OptionType.STRING, "image", "Embed image url");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        MessageChannel channel = event.getOption("channel").getAsChannel().asStandardGuildMessageChannel();
        String messageId = event.getOption("messageid").getAsString();

        if (event.getOption("embed").getAsBoolean()) {
            Message old = channel.retrieveMessageById(messageId).complete();
            EmbedBuilder embed = new EmbedBuilder();
            if (event.getOption("image") != null) embed.setThumbnail(event.getOption("image").getAsString());
            if (event.getOption("title") != null) embed.setTitle(event.getOption("title").getAsString());
            if (event.getOption("message") != null) {
                embed.setDescription(event.getOption("message").getAsString().replace("\\n", "\n"));
            } else {
                embed.setDescription(old.getEmbeds().isEmpty() ? "" : old.getEmbeds().getFirst().getDescription());
            }
            if (event.getOption("image") != null || event.getOption("title") != null
                    || event.getOption("message") != null) {
                channel.editMessageEmbedsById(messageId, embed.build()).complete();
                event.reply("Embed edited.").setEphemeral(true).queue();
            } else {
                event.reply("Can't edit embed without at least one of the following: message, title, image.")
                        .setEphemeral(true).queue();
            }
        } else {
            if (event.getOption("message") != null) {
                channel.editMessageById(messageId, event.getOption("message").getAsString().replace("\\n", "\n")).complete();
                event.reply("Message edited.").setEphemeral(true).queue();
            } else {
                event.reply("Can't edit message without message content.").setEphemeral(true).queue();
            }
        }
    }
}
