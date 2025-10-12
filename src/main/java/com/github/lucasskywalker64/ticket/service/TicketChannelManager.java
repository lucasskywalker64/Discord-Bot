package com.github.lucasskywalker64.ticket.service;

import com.github.lucasskywalker64.BotMain;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;

import java.util.EnumSet;
import java.util.List;

import static com.github.lucasskywalker64.BotConstants.GREEN;

@SuppressWarnings("DataFlowIssue")
public class TicketChannelManager {

    private final JDA jda = BotMain.getContext().jda();
    private final TicketConfigHolder configHolder;
    private final EnumSet<Permission> permissions;

    public TicketChannelManager(TicketConfigHolder configHolder) {
        this.configHolder = configHolder;
        permissions = EnumSet.of(
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_ATTACH_FILES);
    }

    TextChannel createChannelForTicket(int ticketId, long openerId) {
        return jda.getGuilds().getFirst().getCategoryById(configHolder.get().ticketsCategoryId())
                .createTextChannel(String.format("ticket-%d", ticketId))
                .addMemberPermissionOverride(
                        openerId,
                        permissions,
                        null).complete();
    }

    void postIntroMessage(long channelId) {
        TextChannel textChannel = jda.getGuilds().getFirst().getTextChannelById(channelId);
        textChannel.sendMessageFormat("-# ||%s||", jda.getGuilds().getFirst().getRoleById(configHolder.get().supportRoleId())
                .getAsMention()).queue();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(GREEN)
                .setDescription(String.format("""
                        Thank you for contacting support.
                        Please describe your issue and wait patiently.
                        A staff member will be with you once one is available.
                        -# Note: The ticket will automatically close after %d hours if you do not write anything.""",
                        configHolder.get().autoCloseAfter()));
        String lockEmoji = "\uD83D\uDD12";
        String handEmoji = "\uD83D\uDD90";
        ActionRow actionRow = ActionRow.of(
                Button.danger("ticketClose", lockEmoji + " Close"),
                Button.danger("ticketCloseReason", lockEmoji + " Close With Reason"),
                Button.success("ticketClaim", handEmoji + "️ Claim")
        );
        textChannel.sendMessageEmbeds(embed.build()).setComponents(actionRow).queue();
    }

    void addMemberToChannel(TextChannel channel, List<Member> members) {
        TextChannelManager manager = channel.getManager();
        for (Member member : members)
            manager = manager.putMemberPermissionOverride(member.getIdLong(), permissions, null);
        manager.queue();
    }

    void removeMemberFromChannel(TextChannel channel, List<Member> members) {
        TextChannelManager manager = channel.getManager();
        for (Member member : members)
            manager = manager.removePermissionOverride(member.getIdLong());
        manager.queue();
    }
}
