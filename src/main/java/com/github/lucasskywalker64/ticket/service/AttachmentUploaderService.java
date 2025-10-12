package com.github.lucasskywalker64.ticket.service;

import com.github.lucasskywalker64.ticket.persistence.TicketRepository;
import com.github.lucasskywalker64.web.S3Uploader;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;

import java.io.InputStream;
import java.util.*;

public class AttachmentUploaderService {

    private final S3Uploader uploader;
    private final TicketRepository repository = TicketRepository.getInstance();

    public AttachmentUploaderService() {
        uploader = new S3Uploader();
    }

    public void uploadAttachmentsForTranscript(List<Message> messages, long ticketId) throws Exception {
        Set<String> keys = new LinkedHashSet<>();

        for (Message message : messages) {
            for (Attachment attachment : message.getAttachments()) {
                String sanitizedFileName = attachment.getFileName().replaceAll("[^a-zA-Z0-9_.-]", "_");
                String key = String.format("ticket-%d/%s-%s", ticketId, attachment.getId(), sanitizedFileName);

                if (!keys.add(key)) continue;

                try (InputStream in = attachment.getProxy().download().join()) {
                    long len = attachment.getSize();
                    String contentType = attachment.getContentType();
                    if (contentType == null) contentType = "application/octet-stream";
                    uploader.uploadAttachment(key, in, len, contentType);
                }
            }
        }

        repository.saveAttachmentKeysForTicket(new ArrayList<>(keys), ticketId);
    }
}
