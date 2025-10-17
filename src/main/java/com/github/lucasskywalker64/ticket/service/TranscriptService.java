package com.github.lucasskywalker64.ticket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.lucasskywalker64.ticket.model.TranscriptData;
import com.github.lucasskywalker64.ticket.model.TranscriptMessage;
import com.github.lucasskywalker64.ticket.model.TranscriptResult;
import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TranscriptService {

    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    public TranscriptService() {
        if ("prod".equals(System.getProperty("app.env", "prod"))) {
            templateEngine = TemplateEngine.createPrecompiled(Path.of("jte-classes"), ContentType.Html);
        } else {
            CodeResolver codeResolver = new DirectoryCodeResolver(Path.of(new File(System.getProperty("user.dir")).getParent(),"src/main/jte"));
            templateEngine = TemplateEngine.create(codeResolver, ContentType.Html);
        }
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public CompletableFuture<TranscriptResult> generateTranscript(MessageChannel channel) {
        CompletableFuture<TranscriptResult> future = new CompletableFuture<>();

        List<Message> messages = new ArrayList<>();
        channel.getHistory().retrievePast(100).queue(initialMessages -> {
            if (initialMessages.isEmpty()) {
                try {
                    future.complete(buildTranscriptData(messages, channel.getName()));
                } catch (JsonProcessingException e) {
                    future.completeExceptionally(e);
                }
                return;
            }
            messages.addAll(initialMessages);
            long oldestMessageId = messages.getLast().getIdLong();
            fetchHistoryRecursively(channel, oldestMessageId, messages, future);
        }, future::completeExceptionally);

        return future;
    }

    public String generateTranscriptFromJson(String json) throws JsonProcessingException {
        List<TranscriptMessage> messages = objectMapper.readValue(json, new TypeReference<>() {});
        return buildTranscriptData(messages, messages.getFirst().channelName).html();
    }

    private void fetchHistoryRecursively(
            MessageChannel channel,
            long anchorMessageId,
            List<Message> messages,
            CompletableFuture<TranscriptResult> future) {
        channel.getHistoryBefore(anchorMessageId, 100).queue(history -> {
            List<Message> olderMessages = history.getRetrievedHistory();
            if (olderMessages.isEmpty()) {
                Collections.reverse(messages);
                try {
                    future.complete(buildTranscriptData(messages, channel.getName()));
                } catch (JsonProcessingException e) {
                    future.completeExceptionally(e);
                }
                return;
            }
            messages.addAll(olderMessages);
            long nextAnchorId = olderMessages.getLast().getIdLong();
            fetchHistoryRecursively(channel, nextAnchorId, messages, future);
        }, throwable -> {
            Collections.reverse(messages);
            try {
                future.complete(buildTranscriptData(messages, channel.getName()));
            } catch (JsonProcessingException e) {
                future.completeExceptionally(e);
            }
        });
    }

    private TranscriptResult buildTranscriptData(List<?> messages, String channelName) throws JsonProcessingException {
        List<TranscriptMessage> transcriptMessages;
        boolean isMessages = false;
        String guildId;
        if (messages.getFirst() instanceof Message) {
            transcriptMessages = messages.stream()
                    .map(Message.class::cast)
                    .map(TranscriptMessage::new)
                    .toList();
            isMessages = true;
            guildId = ((Message) messages.getFirst()).getGuildId();
        } else {
            transcriptMessages = messages.stream()
                    .map(TranscriptMessage.class::cast)
                    .toList();
            guildId = ((TranscriptMessage) messages.getFirst()).guildId;
        }

        String json = objectMapper.writeValueAsString(transcriptMessages);

        String ticketId = channelName.substring(channelName.indexOf("-") + 1);
        TranscriptData data = new TranscriptData(channelName, transcriptMessages, ticketId, guildId, new MarkdownService());
        StringOutput out = new StringOutput();
        templateEngine.render("transcript.jte", data, out);
        if (isMessages)
            return new TranscriptResult(out.toString(), json, (List<Message>) messages);
        else
            return new TranscriptResult(out.toString(), json, null);
    }
}
