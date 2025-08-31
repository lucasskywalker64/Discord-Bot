package com.github.lucasskywalker64.listener.role;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.github.lucasskywalker64.persistence.data.ReactionRoleData;
import com.github.lucasskywalker64.persistence.repository.ReactionRoleRepository;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.entities.RoleImpl;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;

@SuppressWarnings({"java:S1192", "DataFlowIssue"})
public class ReactionRoleManager extends ListenerAdapter {

    private static final List<String> messageIds = new ArrayList<>();
    private static final List<Long> roles = new ArrayList<>();
    private static final List<String> emojis = new ArrayList<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ReactionRoleRepository repository = ReactionRoleRepository.getInstance();

    public void load() {
        List<ReactionRoleData> allData = repository.loadAll();
        messageIds.clear();
        roles.clear();
        emojis.clear();

        for (ReactionRoleData data : allData) {
            messageIds.add(data.messageId());
            roles.add(data.role());
            emojis.add(data.emoji());
        }
    }

    public void scheduleLoad() {
        scheduler.scheduleAtFixedRate(this::load, 0, 1, TimeUnit.DAYS);
    }



    @Override
    @SuppressWarnings("java:S3776")
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        try {
            User user = event.retrieveUser().complete();

            for (int i = 0; i < messageIds.size(); i++) {
                if (messageIds.get(i).equals(event.getMessageId()) &&
                        emojis.get(i).equals(event.getEmoji().getFormatted())
                        && !user.isBot()) {
                    Logger.info("Reaction equals reaction role");
                    int finalI = i;
                    event.getGuild().addRoleToMember(user, new RoleImpl(roles.get(i), event.getGuild()))
                            .submit()
                            .whenComplete((unused, roleError) -> {
                                if (roleError == null) {
                                    Logger.info("Role {} added to {}", event.getGuild()
                                            .getRoleById(roles.get(finalI)).getName(), user.getName());
                                    user.openPrivateChannel().submit()
                                            .thenCompose(privateChannel -> privateChannel
                                                    .sendMessage("The role " + event.getGuild()
                                                                    .getRoleById(roles.get(finalI)).getName()
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
            User user = event.retrieveUser().complete();

            for (int i = 0; i < messageIds.size(); i++) {
                if (messageIds.get(i).equals(event.getMessageId()) &&
                        emojis.get(i).equals(event.getEmoji().getFormatted())
                        && !user.isBot()) {
                    Logger.info("Reaction equals reaction role");
                    int finalI = i;
                    event.getGuild().removeRoleFromMember(user, new RoleImpl(roles.get(i), event.getGuild()))
                            .submit()
                            .whenComplete((unused, roleError) -> {
                                if (roleError == null) {
                                    Logger.info("Role {} removed from {}", event.getGuild()
                                            .getRoleById(roles.get(finalI)).getName(), user.getName());
                                    user.openPrivateChannel().submit()
                                            .thenCompose(privateChannel -> privateChannel
                                                    .sendMessage("The role " + event.getGuild()
                                                                    .getRoleById(roles.get(finalI)).getName()
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

    public void cleanUp() throws InterruptedException {
        scheduler.awaitTermination(10, TimeUnit.SECONDS);
    }

    public ReactionRoleManager() {
        Logger.info("Starting Reaction Role Manager...");
        scheduleLoad();
    }
}
