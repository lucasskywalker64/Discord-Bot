package com.github.lucasskywalker64.ticket.model;

import com.github.lucasskywalker64.ticket.service.MarkdownService;
import net.dv8tion.jda.api.entities.Message;

import java.time.OffsetDateTime;
import java.util.List;

public class TranscriptMessage {

    public String channelName;
    public String authorName;
    public String avatarUrl;
    public OffsetDateTime offsetDateTime;
    public String timestampInstant;
    public String content;
    public List<TranscriptEmbed> embeds;
    public List<TranscriptAttachment> attachments;
    public String guildId;

    public TranscriptMessage(Message message) {
        channelName = message.getChannel().getName();
        authorName = message.getAuthor().getEffectiveName();
        avatarUrl = message.getAuthor().getEffectiveAvatarUrl();
        offsetDateTime = message.getTimeCreated();
        timestampInstant = offsetDateTime.toInstant().toString();
        content = new MarkdownService().format(message);
        embeds = message.getEmbeds().stream()
                .map(embed -> new TranscriptEmbed(embed, message.getGuild()))
                .toList();
        attachments = message.getAttachments().stream()
                .map(TranscriptAttachment::new)
                .toList();
        guildId = message.getGuild().getId();
    }
}
