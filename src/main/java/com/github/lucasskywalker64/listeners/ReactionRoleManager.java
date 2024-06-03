package com.github.lucasskywalker64.listeners;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.github.lucasskywalker64.BotMain;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.entities.RoleImpl;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;

@SuppressWarnings("java:S1192")
public class ReactionRoleManager extends ListenerAdapter {

  private static final List<String> messageId = new ArrayList<>();
  private static final List<Long> roleId = new ArrayList<>();
  private static final List<String> emoji = new ArrayList<>();
  private static final File reactionRoleFile = BotMain.getReactionRolesFile();
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public void updateLists() {
    Logger.info("Setting up reaction role lists...");
    try (FileReader fileReader = new FileReader(reactionRoleFile)) {
      messageId.clear();
      roleId.clear();
      emoji.clear();

      Logger.info("Old lists cleared");

      CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
          .setDelimiter(";")
          .setHeader("messageId", "roleId", "emoji")
          .setSkipHeaderRecord(true)
          .build();

      Iterable<CSVRecord> csvIterable = csvFormat.parse(fileReader);

      for (CSVRecord csvRecord : csvIterable) {
        messageId.add(csvRecord.get("messageId"));
          if (!csvRecord.get("roleId").contains("@")) {
              roleId.add(Long.valueOf(csvRecord.get("roleId")));
          } else {
              roleId.add(Long.valueOf(csvRecord.get("roleId")
                  .substring(csvRecord.get("roleId").indexOf("&") + 1, csvRecord.get("roleId")
                      .lastIndexOf(">"))));
          }
        emoji.add(csvRecord.get("emoji"));
      }
      Logger.info("Lists updated");
    } catch (IOException e) {
      Logger.error(e);
    }
  }

  public void scheduleUpdateLists() {
    scheduler.scheduleAtFixedRate(this::updateLists, 0, 1, TimeUnit.DAYS);
  }

  @Override
  @SuppressWarnings("java:S3776")
  public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
    try {
      User user = Objects.requireNonNull(event.retrieveUser().complete(), "User must not be null");

      Logger.info(event.getEmoji().getFormatted());
      for (int i = 0; i < messageId.size(); i++) {
        if (messageId.get(i).equals(event.getMessageId()) &&
            emoji.get(i).equals(event.getEmoji().getFormatted())
            && !user.isBot()) {
          Logger.info("Reaction equals reaction role");
          int finalI = i;
          event.getGuild().addRoleToMember(user, new RoleImpl(roleId.get(i), event.getGuild()))
              .submit()
              .whenComplete((unused, roleError) -> {
                  if (roleError == null) {
                      Logger.info("Role {} added to {}", event.getGuild()
                          .getRoleById(roleId.get(finalI)).getName(), user.getName());
                      user.openPrivateChannel().submit()
                          .thenCompose(privateChannel -> privateChannel
                              .sendMessage("The role " + Objects.requireNonNull(event.getGuild()
                                      .getRoleById(roleId.get(finalI)),
                                  "Role must not be null").getName()
                                  + " has been successfully added to you!").submit())
                          .whenComplete((unused2, messageError) -> {
                              if (messageError != null) {
                                  Logger.info("Message could not be sent because: {}",
                                      messageError.getMessage());
                              } else {
                                  Logger.info("Message sent");
                              }
                          });
                  } else {
                      Logger.error("Could not add role because: {}", roleError.getMessage());
                  }
              });
        }
      }
    } catch (Exception e) {
      Logger.error(e);
    }
  }

  @Override
  @SuppressWarnings("java:S3776")
  public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
    try {
      User user = Objects.requireNonNull(event.retrieveUser().complete(), "User must not be null");

      for (int i = 0; i < messageId.size(); i++) {
        if (messageId.get(i).equals(event.getMessageId()) &&
            emoji.get(i).equals(event.getEmoji().getFormatted())
            && !user.isBot()) {
          Logger.info("Reaction equals reaction role");
          int finalI = i;
          event.getGuild().removeRoleFromMember(user, new RoleImpl(roleId.get(i), event.getGuild()))
              .submit()
              .whenComplete((unused, roleError) -> {
                  if (roleError == null) {
                      Logger.info("Role {} removed from {}", event.getGuild()
                          .getRoleById(roleId.get(finalI)).getName(), user.getName());
                      user.openPrivateChannel().submit()
                          .thenCompose(privateChannel -> privateChannel
                              .sendMessage("The role " + Objects.requireNonNull(event.getGuild()
                                      .getRoleById(roleId.get(finalI)),
                                  "Role must not be null").getName()
                                  + " has been successfully removed from you!").submit())
                          .whenComplete((unused2, messageError) -> {
                              if (messageError != null) {
                                  Logger.info("Message could not be sent because: {}",
                                      messageError.getMessage());
                              } else {
                                  Logger.info("Message sent");
                              }
                          });
                  } else {
                      Logger.error("Could not remove role because: {}", roleError.getMessage());
                  }
              });
        }
      }
    } catch (Exception e) {
      Logger.error(e);
    }
  }

  public void cleanUp() {
    scheduler.shutdownNow();
    messageId.clear();
    roleId.clear();
    emoji.clear();
  }

  public ReactionRoleManager() {
    Logger.info("Starting Reaction Role Manager...");
    scheduleUpdateLists();
  }
}
