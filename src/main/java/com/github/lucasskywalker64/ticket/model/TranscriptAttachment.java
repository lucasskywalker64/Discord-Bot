package com.github.lucasskywalker64.ticket.model;

import net.dv8tion.jda.api.entities.Message.Attachment;

public record TranscriptAttachment (
        String id,
        String fileName,
        String proxyUrl,
        long size,
        String contentType
){
    public TranscriptAttachment(Attachment attachment) {
        this(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getProxyUrl(),
                attachment.getSize(),
                attachment.getContentType()
        );
    }
}
