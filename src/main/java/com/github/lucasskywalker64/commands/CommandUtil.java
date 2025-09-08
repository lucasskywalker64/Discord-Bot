package com.github.lucasskywalker64.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.Emoji.Type;

import java.util.List;

public final class CommandUtil {

    public static boolean validateEmojis(Guild guild, List<Emoji> emojiList) {
        boolean valid = true;
        for (Emoji emoji : emojiList) {
            valid = emoji.getType().equals(Type.UNICODE) || guild.getEmojiById(((CustomEmoji) emoji).getId()) != null;
            if (!valid) break;
        }
        return valid;
    }

    public static void addFieldSafe(EmbedBuilder embed, String title, List<String> lines, boolean inline) {
        StringBuilder chunk = new StringBuilder();
        for (String line : lines) {
            if (chunk.length() + line.length() + 1 > 1024) {
                embed.addField(title, chunk.toString(), inline);
                chunk.setLength(0);
                title = ""; // only show the title once
            }
            chunk.append(line).append("\n");
        }
        if (!chunk.isEmpty()) {
            embed.addField(title, chunk.toString(), inline);
        }
    }
}
