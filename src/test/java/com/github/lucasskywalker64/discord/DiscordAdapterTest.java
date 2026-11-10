package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.DiscordBotApplication;
import com.github.lucasskywalker64.guild.GuildConfiguration;
import com.github.lucasskywalker64.guild.GuildConfigurationRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class DiscordAdapterTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Mock
    private JDA discord;

    @Mock(answer = Answers.RETURNS_SELF)
    private JDABuilder builder;

    @BeforeEach
    void configureConnection() throws InterruptedException {
        when(builder.build()).thenReturn(discord);
        when(discord.awaitShutdown(Duration.ofSeconds(10))).thenReturn(true);
    }

    @Test
    void registersPingWhenAConfiguredGuildIsJoined() {
        try (var application = startApplication()) {
            String guildId = "100000000000000001";
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration(guildId, null));
            var guild = guild(guildId);
            var registration = mock(CommandListUpdateAction.class, RETURNS_SELF);
            when(guild.updateCommands()).thenReturn(registration);

            listener().onEvent(new GuildJoinEvent(discord, 1, guild));

            verify(registration).queue();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "100000000000000003")
    void rejectsPingWithoutAConfiguredGuild(String guildId) {
        try (var application = startApplication()) {
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration("100000000000000001", null));
            var interaction = ping(guildId == null ? null : guild(guildId));
            var reply = mock(ReplyCallbackAction.class, RETURNS_SELF);
            when(interaction.deferReply()).thenReturn(reply);

            listener().onEvent(new SlashCommandInteractionEvent(discord, 1, interaction));

            verify(reply).setContent("Use this command in a configured guild.");
            verify(reply).setEphemeral(true);
            verify(reply).queue();
            verify(discord, never()).getRestPing();
        }
    }

    @Test
    void answersPingInTheActualGuildAfterAcknowledgingIt() {
        try (var application = startApplication()) {
            application.getBean(GuildConfigurationRepository.class).saveAllAndFlush(List.of(
                    new GuildConfiguration("100000000000000001", null),
                    new GuildConfiguration("100000000000000002", null)));
            var listener = listener();
            RestAction<Long> restPing = mock();
            when(discord.getRestPing()).thenReturn(restPing);
            when(restPing.submit()).thenReturn(CompletableFuture.completedFuture(42L));
            when(discord.getGatewayPing()).thenReturn(17L);

            for (String guildId : List.of("100000000000000001", "100000000000000002")) {
                clearInvocations(discord);
                var interaction = ping(guild(guildId));
                var acknowledgement = mock(ReplyCallbackAction.class, RETURNS_SELF);
                var hook = mock(InteractionHook.class);
                WebhookMessageEditAction<Message> response = mock();
                var acknowledged = new CompletableFuture<InteractionHook>();
                when(interaction.deferReply()).thenReturn(acknowledgement);
                when(acknowledgement.submit()).thenReturn(acknowledged);
                when(hook.editOriginal(anyString())).thenReturn(response);
                when(response.submit()).thenReturn(CompletableFuture.completedFuture(null));

                listener.onEvent(new SlashCommandInteractionEvent(discord, 1, interaction));

                verify(discord, never()).getRestPing();
                acknowledged.complete(hook);

                var order = inOrder(acknowledgement, hook);
                order.verify(acknowledgement).setEphemeral(true);
                order.verify(acknowledgement).submit();
                order.verify(hook).editOriginal("Rest ping: 42 ms\nGateway ping: 17 ms");
                verify(response).submit();
            }
        }
    }

    @Test
    void registersPingInEachConfiguredGuildAndStopsWithSpring() throws InterruptedException {
        try (var application = startApplication()) {
            application.getBean(GuildConfigurationRepository.class).saveAllAndFlush(List.of(
                    new GuildConfiguration("100000000000000001", null),
                    new GuildConfiguration("100000000000000002", null)));
            var listener = listener();

            for (String guildId : List.of("100000000000000001", "100000000000000002")) {
                Guild guild = guild(guildId);
                var registration = mock(CommandListUpdateAction.class, RETURNS_SELF);
                when(guild.updateCommands()).thenReturn(registration);

                listener.onEvent(new GuildReadyEvent(discord, 1, guild));

                var commands = ArgumentCaptor.forClass(CommandData[].class);
                verify(registration).addCommands(commands.capture());
                var definition = commands.getValue()[0].toData();
                assertEquals("general", definition.getString("name"));
                assertEquals("ping", definition.getArray("options").getObject(0).getString("name"));
                assertEquals("0", definition.getString("default_member_permissions"));
                verify(registration).queue();
            }

            Guild unknown = guild("100000000000000003");
            listener.onEvent(new GuildReadyEvent(discord, 1, unknown));
            verify(unknown, never()).updateCommands();
        }

        verify(discord).shutdown();
        verify(discord).awaitShutdown(Duration.ofSeconds(10));
        verify(discord, never()).shutdownNow();
    }

    @Test
    void forcesShutdownIfGracefulShutdownTimesOut() throws InterruptedException {
        when(discord.awaitShutdown(Duration.ofSeconds(10))).thenReturn(false);
        when(discord.awaitShutdown(Duration.ofSeconds(5))).thenReturn(true);
        try (var ignored = startApplication()) {
            // Closing Spring must also stop a gateway with pending requests.
        }

        var order = inOrder(discord);
        order.verify(discord).shutdown();
        order.verify(discord).awaitShutdown(Duration.ofSeconds(10));
        order.verify(discord).shutdownNow();
        order.verify(discord).awaitShutdown(Duration.ofSeconds(5));
    }

    private ConfigurableApplicationContext startApplication() {
        var application = new SpringApplication(DiscordBotApplication.class);
        application.addInitializers(context -> ((GenericApplicationContext) context)
                .registerBean("testDiscordBuilder", JDABuilder.class, () -> builder));
        return application.run(
                "--server.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--discord.enabled=true",
                "--spring.main.banner-mode=off",
                "--logging.level.root=OFF");
    }

    private ListenerAdapter listener() {
        var listener = ArgumentCaptor.forClass(Object.class);
        verify(builder).addEventListeners(listener.capture());
        return (ListenerAdapter) listener.getValue();
    }

    private static Guild guild(String id) {
        var guild = mock(Guild.class);
        when(guild.getId()).thenReturn(id);
        return guild;
    }

    private static SlashCommandInteraction ping(Guild guild) {
        var interaction = mock(SlashCommandInteraction.class);
        when(interaction.getGuild()).thenReturn(guild);
        when(interaction.getName()).thenReturn("general");
        when(interaction.getSubcommandName()).thenReturn("ping");
        return interaction;
    }
}
