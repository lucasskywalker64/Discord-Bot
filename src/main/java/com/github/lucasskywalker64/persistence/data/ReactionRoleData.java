package com.github.lucasskywalker64.persistence.data;

/**
 *
 * @param messageId
 * @param role 0 = @everyone, 1 = @here
 * @param emoji
 */
public record ReactionRoleData(
        String messageId,
        Long role,
        String emoji) {}
