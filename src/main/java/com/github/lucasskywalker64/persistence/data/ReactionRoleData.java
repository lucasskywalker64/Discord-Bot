package com.github.lucasskywalker64.persistence.data;

/**
 *
 * @param channelId
 * @param messageId
 * @param roleId
 * @param roleName
 * @param emoji
 */
public record ReactionRoleData(
        String channelId,
        String messageId,
        String roleId,
        String roleName,
        String emoji) {}
