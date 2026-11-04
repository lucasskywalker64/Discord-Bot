package com.github.lucasskywalker64.ticket.service;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.ticket.model.Ticket;
import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TicketListService {

    private final TemplateEngine templateEngine;
    private final Dotenv config;
    private final JDA jda;

    public TicketListService() {
        if ("prod".equals(System.getProperty("app.env", "prod"))) {
            templateEngine = TemplateEngine.createPrecompiled(Path.of("jte-classes"), ContentType.Html);
        } else {
            CodeResolver codeResolver = new DirectoryCodeResolver(Path.of("src/main/jte"));
            templateEngine = TemplateEngine.create(codeResolver, ContentType.Html);
        }

        config = BotMain.getContext().config();
        jda = BotMain.getContext().jda();
    }

    public static String getEffectiveName(Guild guild, long userId) {
        if (userId == 0) return "";
        if (guild != null) {
            var member = guild.getMemberById(userId);
            if (member != null) return member.getEffectiveName();
        }
        try {
            if (guild != null) {
                var user = guild.getJDA().getUserById(userId);
                if (user != null) return user.getEffectiveName();
                return guild.getJDA().retrieveUserById(userId).complete().getEffectiveName();
            }
        } catch (Exception ignored) {
        }
        return "User " + userId;
    }

    public String generateHtml(List<Ticket> tickets, String guildId) {
        Map<String, Object> params = new HashMap<>();
        params.put("ticketList", tickets);
        params.put("guild", jda.getGuildById(guildId));
        params.put("config", config);
        StringOutput out = new StringOutput();
        templateEngine.render("ticket_list.jte", params, out);
        return out.toString();
    }
}
