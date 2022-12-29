package me.lucasskywalker.listeners;

import net.dv8tion.jda.api.entities.User;
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
            FileReader fileReader = new FileReader(new File(ReactionRoleManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/reaction-roles.csv");

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
                            .substring(record.get("roleId").indexOf("&") + 1, record.get("roleId")
                                    .lastIndexOf(">"))));
                emoji.add(record.get("emoji"));
            }

            for(int i = 0; i < messageId.size(); i++) {
                if(messageId.get(i).equals(event.getMessageId())
                        && emoji.get(i).equals(event.getEmoji().getFormatted()) && !event.getUser().isBot()) {
                    event.getGuild().addRoleToMember(event.getMember(), new RoleImpl(roleId.get(i), event.getGuild()))
                            .queue();
                    int finalI = i;
                    event.getUser().openPrivateChannel()
                            .flatMap(privateChannel -> privateChannel
                                    .sendMessage("The role "
                                            + event.getGuild().getRoleById(roleId.get(finalI)).getName()
                                            + " has been successfully added to you!"))
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
            FileReader fileReader = new FileReader(new File(ReactionRoleManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile().getPath()
                    + "/bot_files/reaction-roles.csv");

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
                    roleId.add(Long.valueOf(record.get("roleId").substring(record.get("roleId").indexOf("&") + 1,
                            record.get("roleId").lastIndexOf(">"))));
                emoji.add(record.get("emoji"));
            }

            User user = event.retrieveUser().complete();

            for (int i = 0; i < messageId.size(); i++) {
                if (messageId.get(i).equals(event.getMessageId())
                        && emoji.get(i).replaceFirst("a", "").equals(event.getEmoji().getFormatted())
                        && !user.isBot()) {
                    event.getGuild().removeRoleFromMember(user,
                            new RoleImpl(roleId.get(i), event.getGuild())).queue();
                    int finalI = i;
                    user.openPrivateChannel()
                            .flatMap(privateChannel -> privateChannel
                                    .sendMessage("The role "
                                            + event.getGuild().getRoleById(roleId.get(finalI)).getName()
                                            + " has been successfully removed from you!"))
                            .queue();
                }
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }


}
