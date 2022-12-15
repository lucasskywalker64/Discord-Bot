package me.lucasskywalker.listeners;

import me.lucasskywalker.BotMain;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.entities.RoleImpl;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class ReactionRoleManager extends ListenerAdapter {

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        try {
            FileReader fileReader = new FileReader(new File(BotMain.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParentFile().getPath() + "/reaction-roles.csv");

            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(";")
                    .setHeader("messageId", "roleId", "emoji")
                    .setSkipHeaderRecord(true)
                    .build();

            Iterable<CSVRecord> csvIterable = csvFormat.parse(fileReader);

            List<String> messageId = new ArrayList<>();
            List<Long> roleId = new ArrayList<>();
            List<String> emoji = new ArrayList<>();

            for(CSVRecord record : csvIterable) {
                messageId.add(record.get("messageId"));
                if(!record.get("roleId").contains("@"))
                    roleId.add(Long.valueOf(record.get("roleId")));
                else
                    roleId.add(Long.valueOf(record.get("roleId")
                            .substring(3, record.get("roleId").lastIndexOf(">"))));

                emoji.add(record.get("emoji").replace("<", "").replace(">", "")
                        .replaceFirst(":", ""));
            }

            for(int i = 0; i < messageId.size(); i++) {
                if(messageId.get(i).equals(event.getMessageId())
                        && emoji.get(i).equals(event.getEmoji().getAsReactionCode()) && !event.getUser().isBot()) {
                    event.getGuild().addRoleToMember(event.getMember(), new RoleImpl(roleId.get(i), event.getGuild()))
                            .queue();
                }
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        try {
            FileReader fileReader = new FileReader(new File(BotMain.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParentFile().getPath() + "/reaction-roles.csv");

            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(";")
                    .setHeader("messageId", "roleId", "emoji")
                    .setSkipHeaderRecord(true)
                    .build();

            Iterable<CSVRecord> csvIterable = csvFormat.parse(fileReader);

            List<String> messageId = new ArrayList<>();
            List<Long> roleId = new ArrayList<>();
            List<String> emoji = new ArrayList<>();

            for (CSVRecord record : csvIterable) {
                messageId.add(record.get("messageId"));
                if(!record.get("roleId").contains("@"))
                    roleId.add(Long.valueOf(record.get("roleId")));
                else
                    roleId.add(Long.valueOf(record.get("roleId")
                            .substring(3, record.get("roleId").lastIndexOf(">"))));
                emoji.add(record.get("emoji").replace("<", "").replace(">", "")
                        .replaceFirst(":", ""));
            }

            for (int i = 0; i < messageId.size(); i++) {
                if (messageId.get(i).equals(event.getMessageId())
                        && emoji.get(i).equals(event.getEmoji().getAsReactionCode()) && !event.getUser().isBot()) {
                    event.getGuild().removeRoleFromMember(event.getMember(),
                            new RoleImpl(roleId.get(i), event.getGuild())).queue();
                }
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }


}
