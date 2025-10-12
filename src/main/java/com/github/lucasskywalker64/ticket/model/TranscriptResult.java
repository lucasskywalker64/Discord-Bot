package com.github.lucasskywalker64.ticket.model;

import net.dv8tion.jda.api.entities.Message;

import java.util.List;

public record TranscriptResult(String html, String json, List<Message> messages) {
}
