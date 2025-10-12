package com.github.lucasskywalker64.ticket.service;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.ticket.model.TicketScheduleData;
import com.github.lucasskywalker64.ticket.model.TicketStatus;
import com.github.lucasskywalker64.ticket.persistence.TicketRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class TicketSchedulerService {

    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<Integer, ScheduledFuture<?>> scheduledTasks;
    private final TicketRepository repository;
    private final TicketService service;
    private final TicketConfigHolder configHolder;
    private final JDA jda;

    public TicketSchedulerService(TicketConfigHolder configHolder, TicketService service) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduledTasks = new ConcurrentHashMap<>();
        repository = TicketRepository.getInstance();
        this.service = service;
        this.configHolder = configHolder;
        jda = BotMain.getContext().jda();

        List<TicketScheduleData> pendingTasks = new ArrayList<>();
        try {
            pendingTasks = repository.getAllScheduledClosures();
        } catch (SQLException e) {
            Logger.error(e);
        }
        for (TicketScheduleData data : pendingTasks) {
            int ticketId = data.ticketId();
            long closeTime = data.closeTimeEpoch();
            long now = System.currentTimeMillis();
            TextChannel channel = jda.getTextChannelById(data.channelId());
            if (closeTime <= now) {
                scheduler.schedule(createCloseTask(ticketId, channel), 0, TimeUnit.MILLISECONDS);
            } else {
                long remainingTime = closeTime - now;
                scheduler.schedule(createCloseTask(ticketId, channel), remainingTime, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void scheduleTicketClosure(int ticketId, TextChannel channel) {
        Runnable closeTask = createCloseTask(ticketId, channel);
        int delayInHours = configHolder.get().autoCloseAfter();
        long closeTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(delayInHours);
        ScheduledFuture<?> scheduledFuture = scheduler.schedule(closeTask, delayInHours, TimeUnit.HOURS);
        scheduledTasks.put(ticketId, scheduledFuture);
        try {
            repository.saveScheduledClosure(new TicketScheduleData(ticketId, closeTime, channel.getIdLong()));
        } catch (SQLException e) {
            Logger.error(e);
        }
    }

    public void cancelTicketClosure(int ticketId) {
        ScheduledFuture<?> future = scheduledTasks.get(ticketId);

        if (future != null) {
            future.cancel(false);
            scheduledTasks.remove(ticketId);
            try {
                repository.deleteScheduledClosure(ticketId);
            } catch (SQLException e) {
                Logger.error(e);
            }
        }
    }

    private @NotNull Runnable createCloseTask(int ticketId, TextChannel channel) {
        return () -> {
            TicketStatus currentStatus;
            try {
                currentStatus = repository.getTicketStatus(ticketId);
            } catch (SQLException e) {
                Logger.error(e);
                scheduledTasks.remove(ticketId);
                return;
            }

            if (currentStatus == TicketStatus.PENDING) {
                try {
                    service.closeTicket(channel, jda.getSelfUser().getIdLong(), "Autoclose due to inactivity");
                } catch (SQLException e) {
                    Logger.error(e);
                }
            }
            scheduledTasks.remove(ticketId);
        };
    }
}
