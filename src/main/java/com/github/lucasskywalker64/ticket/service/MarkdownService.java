package com.github.lucasskywalker64.ticket.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Collections;
import java.util.List;

public class MarkdownService {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        List<Extension> extensions = Collections.singletonList(StrikethroughExtension.create());
        parser = Parser.builder().extensions(extensions).build();
        renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    public String format(Message message) {
        String content = message.getContentRaw();

        for (Role role : message.getMentions().getRoles()) {
            content = content.replace(role.getAsMention(), "<span class=\"mention\">@" + escapeHtml(role.getName()) + "</span>");
        }
        for (User user : message.getMentions().getUsers()) {
            content = content.replace(user.getAsMention(), "<span class=\"mention\">@" + escapeHtml(user.getEffectiveName()) + "</span>");
        }
        for (GuildChannel channel : message.getMentions().getChannels()) {
            content = content.replace(channel.getAsMention(), "<span class=\"mention\">#" + escapeHtml(channel.getName()) + "</span>");
        }

        content = content.replaceAll("\\|\\|(.*?)\\|\\|", "<span class=\"spoiler\">$1</span>");
        if (content.startsWith("-# ")) {
            content = content.replace("-#", "<span class=\"small-text\">");
            content += "</span>";
        }

        Node document = parser.parse(content);
        String html = renderer.render(document);

        if (html.startsWith("<p>") && html.endsWith("</p>\n")) {
            return html.substring(3, html.length() - 5);
        }

        return html;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
