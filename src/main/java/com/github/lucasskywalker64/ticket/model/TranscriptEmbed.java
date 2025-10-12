package com.github.lucasskywalker64.ticket.model;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.AuthorInfo;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.MessageEmbed.Footer;
import net.dv8tion.jda.api.entities.MessageEmbed.ImageInfo;

import java.awt.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record TranscriptEmbed(
        String url,
        String title,
        String description,
        String color,
        EmbedAuthor author,
        EmbedImage image,
        EmbedFooter footer,
        List<EmbedField> fields
) {
    public TranscriptEmbed(MessageEmbed embed, Guild guild) {
        this(
                embed.getUrl(),
                embed.getTitle(),
                formatDescription(embed.getDescription(), guild),
                formatColor(embed.getColor()),
                embed.getAuthor() != null ? new EmbedAuthor(embed.getAuthor()) : null,
                embed.getImage() != null ? new EmbedImage(embed.getImage()) : null,
                embed.getFooter() != null ? new EmbedFooter(embed.getFooter()) : null,
                embed.getFields().stream().map(EmbedField::new).collect(Collectors.toList())
        );
    }

    private static String formatDescription(String description, Guild guild) {
        Pattern pattern = Pattern.compile("<@[0-9]{17,20}>");
        Matcher matcher = pattern.matcher(description);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String memberId = matcher.group().substring(2, matcher.group().length() - 1);
            String replacement = "@" + guild.retrieveMemberById(memberId).complete().getEffectiveName();
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String formatColor(Color color) {
        if (color == null) {
            return null;
        }
        return String.format("#%06x", color.getRGB() & 0xFFFFFF);
    }

    public record EmbedAuthor(String name, String iconUrl) {
        public EmbedAuthor(AuthorInfo author) {
            this(author.getName(), author.getIconUrl());
        }
    }

    public record EmbedImage(String url) {
        public EmbedImage(ImageInfo image) {
            this(image.getUrl());
        }
    }

    public record EmbedFooter(String text) {
        public EmbedFooter(Footer footer) {
            this(footer.getText());
        }
    }

    public record EmbedField(String name, String value, boolean isInline) {
        public EmbedField(Field field) {
            this(field.getName(), field.getValue(), field.isInline());
        }
    }
}
