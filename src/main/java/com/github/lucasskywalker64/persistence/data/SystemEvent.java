package com.github.lucasskywalker64.persistence.data;

public record SystemEvent(
        String id,
        String referenceId,
        String streamerId,
        String domain,
        String eventType,
        String status,
        String payload
) {}
