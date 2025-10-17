package com.github.lucasskywalker64.ticket.model;

import com.github.lucasskywalker64.ticket.service.MarkdownService;

import java.util.List;

public record TranscriptData(
        String channelName,
        List<TranscriptMessage> messages,
        String ticketId,
        String guildId,
        MarkdownService markdownService
) {}
