package com.github.lucasskywalker64.ticket.service;

import com.github.lucasskywalker64.exceptions.InvalidParameterException;
import com.github.lucasskywalker64.exceptions.RateLimitException;
import com.github.lucasskywalker64.ticket.model.Ticket;
import com.github.lucasskywalker64.ticket.model.TicketStatus;
import com.github.lucasskywalker64.ticket.persistence.TicketRepository;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.tinylog.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TicketService {

    private final TicketRepository repository;
    private final TicketChannelManager channelManager;
    private final TicketConfigHolder configHolder;
    private final TicketLogger ticketLogger;
    private final TranscriptService transcriptService;
    private final AttachmentUploaderService attachmentUploader;
    private final TicketListService ticketListService;
    private final TicketSchedulerService ticketScheduler;
    private final Set<Long> openChannels;

    public TicketService(TicketChannelManager channelManager, TicketConfigHolder configHolder, TicketLogger ticketLogger) {
        repository = TicketRepository.getInstance();
        this.channelManager = channelManager;
        this.configHolder = configHolder;
        this.ticketLogger = ticketLogger;
        transcriptService = new TranscriptService();
        attachmentUploader = new AttachmentUploaderService();
        ticketListService = new TicketListService();
        openChannels = new HashSet<>();
        ticketScheduler = new TicketSchedulerService(configHolder, this);
    }

    public Ticket createTicket(long openerId) throws SQLException, RateLimitException {
        if (repository.findByOpenerId(openerId).stream().filter(Ticket::isOpen).toList().size() >= configHolder.get().maxOpenTicketsPerUser()) {
            throw new RateLimitException();
        }
        int ticketId = repository.getNewTicketId();
        TextChannel channel = channelManager.createChannelForTicket(ticketId, openerId);
        Ticket ticket = new Ticket(ticketId, openerId, channel.getIdLong());
        repository.saveTicket(ticket);
        ticketLogger.logTicketCreated(ticket);
        channelManager.postIntroMessage(ticket.channelId());
        openChannels.add(ticket.channelId());
        ticketScheduler.scheduleTicketClosure(ticket.id(), channel);
        return ticket;
    }

    public CompletableFuture<Boolean> closeTicket(TextChannel channel, long closerId, @Nullable String reason) throws SQLException, NoSuchElementException {
        ZonedDateTime closedAt = ZonedDateTime.now();
        final Ticket[] ticket = {repository.findByChannelId(channel.getIdLong()).orElseThrow(NoSuchElementException::new)};
        return transcriptService.generateTranscript(channel)
                .thenCompose(result -> {
                    try {
                        attachmentUploader.uploadAttachmentsForTranscript(result.messages(), ticket[0].id());
                    } catch (Exception e) {
                        Logger.error(e, "Failed to upload transcript for ticket {}", ticket[0].id());
                        return CompletableFuture.failedFuture(e);
                    }
                    return CompletableFuture.completedFuture(result);
                })
                .thenCompose(result -> {
                    Set<Member> authorizedUsers = new HashSet<>();
                    Guild guild = channel.getGuild();

                    for (PermissionOverride override : channel.getPermissionOverrides()) {
                        if (!override.getAllowed().contains(Permission.VIEW_CHANNEL)) continue;

                        if (override.isMemberOverride()) {
                            Member member = override.getMember();
                            if (member != null && !member.getUser().isBot()) {
                                authorizedUsers.add(member);
                            }
                        } else if (override.isRoleOverride()) {
                            Role role = override.getRole();
                            if (role != null) {
                                authorizedUsers.addAll(guild.findMembersWithRoles(role).get().stream()
                                        .filter(member -> !member.getUser().isBot()).toList());
                            }
                        }
                    }

                    Member owner = guild.getOwner();
                    if (owner != null) {
                        authorizedUsers.add(owner);
                    }

                    try {
                        repository.saveAuthorizedUsers(ticket[0].id(), authorizedUsers);
                    } catch (SQLException e) {
                        Logger.error(e, "Failed to save authorized users for ticket {}", ticket[0].id());
                        return CompletableFuture.failedFuture(e);
                    }
                    return CompletableFuture.completedFuture(result);
                })
                .thenCompose(result -> {
                    ticket[0] = ticket[0].closeTicket(closedAt, closerId, reason, result.html(), result.json());
                    try {
                        repository.closeTicket(ticket[0]);
                    } catch (SQLException e) {
                        Logger.error(e, "Failed to save closed ticket for ticket {}", ticket[0].id());
                        return CompletableFuture.failedFuture(e);
                    }
                    ticketLogger.logTicketClosed(ticket[0]);
                    return CompletableFuture.completedFuture(null);
                })
                .thenCompose(v -> channel.delete().submit())
                .thenApply(v -> {
                    openChannels.remove(ticket[0].channelId());
                    return true;
                });
    }

    public boolean claimTicket(GuildChannel channel, Member claimer) throws SQLException {
        Role supportRole = channel.getGuild().getRoleById(configHolder.get().supportRoleId());
        if (!(claimer.getRoles().contains(supportRole) || claimer.hasPermission(Permission.ADMINISTRATOR))) return false;
        repository.updateClaimerId(repository.findByChannelId(channel.getIdLong())
                .orElseThrow(NoSuchElementException::new).claimTicket(claimer.getIdLong()));
        return true;
    }

    public String listTicketsHtml(List<Ticket> tickets, String guildId) {
        return ticketListService.generateHtml(tickets, guildId);
    }

    public void ticketMessageReceived(long channelId, long memberId) {
        Optional<Ticket> ticketOptional = Optional.empty();
        try {
            ticketOptional = repository.findByChannelId(channelId);

            if (ticketOptional.isPresent()) {
                Ticket ticket = ticketOptional.get();
                if (ticket.status().equals(TicketStatus.PENDING) && memberId == ticket.openerId()) {
                    ticket = ticket.withStatus(TicketStatus.OPEN);
                    ticketScheduler.cancelTicketClosure(ticket.id());
                    repository.updateStatus(ticket);
                }
            }
        } catch (SQLException e) {
            Logger.error(e);
        }
    }

    public void addMembers(SlashCommandInteractionEvent event, List<Member> members) throws InvalidParameterException, SQLException {
        Optional<Ticket> ticketOptional = repository.findByChannelId(event.getOption("ticket-channel").getAsChannel().asTextChannel().getIdLong());
        if (ticketOptional.isEmpty()) {
            throw new InvalidParameterException(1002);
        }
        channelManager.addMemberToChannel(event.getOption("ticket-channel").getAsChannel().asTextChannel(), members);
    }

    public void removeMembers(TextChannel channel, List<Member> members) throws InvalidParameterException, SQLException {
        Optional<Ticket> ticketOptional = repository.findByChannelId(channel.getIdLong());
        if (ticketOptional.isEmpty()) {
            throw new InvalidParameterException(1002);
        }

        channelManager.removeMemberFromChannel(channel, members);
    }

    public TranscriptService getTranscriptService() {
        return transcriptService;
    }
}
