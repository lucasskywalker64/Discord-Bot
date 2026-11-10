package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.DiscordBotApplication;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.commands.CommandContext;
import com.github.lucasskywalker64.commands.CommandDefinition;
import com.github.lucasskywalker64.guild.GuildConfiguration;
import com.github.lucasskywalker64.guild.GuildConfigurationRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
    void registersModuleOwnedDescriptionsAndPermissions() {
        var module = new EchoCommand() {
            @Override
            public CommandDefinition definition() {
                return new CommandDefinition(
                        new CommandDefinition.Root("tools", "Developer tools", DefaultMemberPermissions.ENABLED),
                        new CommandDefinition.Group("diagnostics", "Run diagnostics"),
                        new SubcommandData("echo", "Echo the guild"));
            }
        };
        try (var application = startApplication(module)) {
            String guildId = "100000000000000002";
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration(guildId, null));
            var guild = guild(guildId);
            var registration = mock(CommandListUpdateAction.class, RETURNS_SELF);
            when(guild.updateCommands()).thenReturn(registration);

            listener().onEvent(new GuildReadyEvent(discord, 1, guild));

            var commands = ArgumentCaptor.forClass(CommandData[].class);
            verify(registration).addCommands(commands.capture());
            assertEquals(2, commands.getValue().length);
            var tools = commands.getValue()[1];
            assertEquals("tools", tools.getName());
            assertEquals(DefaultMemberPermissions.ENABLED, tools.getDefaultPermissions());
            var definition = tools.toData();
            assertEquals("Developer tools", definition.getString("description"));
            assertEquals("Run diagnostics", definition.getArray("options").getObject(0).getString("description"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"root-description", "root-permissions", "group-description"})
    void rejectsConflictingMetadataBeforeStartingTheGateway(String conflict) {
        var module = new EchoCommand() {
            @Override
            public CommandDefinition definition() {
                return new CommandDefinition(
                        new CommandDefinition.Root("general", "root-description".equals(conflict) ? "Different" : "General utilities",
                                "root-permissions".equals(conflict) ? DefaultMemberPermissions.ENABLED : DefaultMemberPermissions.DISABLED),
                        new CommandDefinition.Group("diagnostics", "group-description".equals(conflict) ? "Different" : "Run diagnostics"),
                        new SubcommandData("mirror", "Echo the guild"));
            }
        };
        var failure = startupFailure(new EchoCommand(), module);
        assertTrue(failure instanceof IllegalStateException);
        assertEquals("group-description".equals(conflict) ? "Conflicting group metadata: general/diagnostics"
                : "Conflicting root metadata: general", failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsSynchronousAndAsynchronousFailuresBeforeAcknowledgement(boolean synchronous) {
        var module = new EchoCommand() {
            @Override
            public CompletionStage<?> handle(CommandContext context, SlashCommandInteractionEvent event) {
                if (synchronous) {
                    throw new IllegalStateException("failed");
                }
                return CompletableFuture.failedFuture(new IllegalStateException("failed"));
            }
        };
        try (var application = startApplication(module)) {
            String guildId = "100000000000000002";
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration(guildId, null));
            var interaction = echo(guild(guildId));
            var reply = mock(ReplyCallbackAction.class, RETURNS_SELF);
            when(interaction.deferReply()).thenReturn(reply);
            when(reply.submit()).thenReturn(CompletableFuture.completedFuture(null));

            listener().onEvent(new SlashCommandInteractionEvent(discord, 1, interaction));

            verify(reply).setContent("Command failed. Please try again.");
            verify(reply).setEphemeral(true);
            verify(reply).submit();
        }
    }

    @Test
    void answersFailedAutocompleteWithNoChoices() {
        var module = new EchoCommand() {
            @Override
            public CompletionStage<?> handleAutoComplete(CommandContext context, CommandAutoCompleteInteractionEvent event) {
                return CompletableFuture.failedFuture(new IllegalStateException("failed"));
            }
        };
        try (var application = startApplication(module)) {
            String guildId = "100000000000000002";
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration(guildId, null));
            var interaction = mock(CommandAutoCompleteInteraction.class);
            var guild = guild(guildId);
            when(interaction.getGuild()).thenReturn(guild);
            when(interaction.getName()).thenReturn("general");
            when(interaction.getSubcommandGroup()).thenReturn("diagnostics");
            when(interaction.getSubcommandName()).thenReturn("echo");
            actor(interaction);
            var reply = mock(AutoCompleteCallbackAction.class);
            when(interaction.replyChoices(List.of())).thenReturn(reply);
            when(reply.submit()).thenReturn(CompletableFuture.completedFuture(null));

            listener().onEvent(new CommandAutoCompleteInteractionEvent(discord, 1, interaction));

            verify(interaction).replyChoices(List.of());
            verify(reply).submit();
        }
    }

    @Test
    void routesUsingTheRegisteredSubcommandName() {
        var module = new EchoCommand() {
            @Override
            public CommandDefinition definition() {
                var definition = super.definition();
                return new CommandDefinition(definition.root(), definition.group(), new SubcommandData("mirror", "Echo the guild"));
            }
        };
        try (var application = startApplication(module)) {
            String guildId = "100000000000000002";
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration(guildId, null));
            var interaction = mock(SlashCommandInteraction.class);
            var guild = guild(guildId);
            when(interaction.getGuild()).thenReturn(guild);
            when(interaction.getName()).thenReturn("general");
            when(interaction.getSubcommandGroup()).thenReturn("diagnostics");
            when(interaction.getSubcommandName()).thenReturn("mirror");
            actor(interaction);
            var reply = mock(ReplyCallbackAction.class, RETURNS_SELF);
            when(interaction.deferReply()).thenReturn(reply);
            when(reply.submit()).thenReturn(CompletableFuture.completedFuture(null));

            listener().onEvent(new SlashCommandInteractionEvent(discord, 1, interaction));

            verify(reply).setContent("Echo from 100000000000000002 by 300000000000000001");
            verify(reply).submit();
        }
    }

    @Test
    void reportsFailedPingInsteadOfLeavingTheDeferredReplyPending() {
        try (var application = startApplication()) {
            String guildId = "100000000000000001";
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration(guildId, null));
            var interaction = ping(guild(guildId));
            var acknowledgement = mock(ReplyCallbackAction.class, RETURNS_SELF);
            var hook = mock(InteractionHook.class);
            WebhookMessageEditAction<Message> response = mock();
            RestAction<Long> restPing = mock();
            when(interaction.deferReply()).thenReturn(acknowledgement);
            when(acknowledgement.submit()).thenReturn(CompletableFuture.completedFuture(hook));
            when(interaction.isAcknowledged()).thenReturn(true);
            when(interaction.getHook()).thenReturn(hook);
            when(discord.getRestPing()).thenReturn(restPing);
            when(restPing.submit()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
            when(hook.editOriginal("Command failed. Please try again.")).thenReturn(response);
            when(response.submit()).thenReturn(CompletableFuture.completedFuture(null));

            listener().onEvent(new SlashCommandInteractionEvent(discord, 1, interaction));

            verify(hook).editOriginal("Command failed. Please try again.");
            verify(response).submit();
        }
    }

    @Test
    void routesAutocompleteToTheInjectedModuleInItsGuild() {
        try (var application = startApplication(new EchoCommand())) {
            String guildId = "100000000000000002";
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration(guildId, null));
            var interaction = mock(CommandAutoCompleteInteraction.class);
            var guild = guild(guildId);
            when(interaction.getGuild()).thenReturn(guild);
            when(interaction.getName()).thenReturn("general");
            when(interaction.getSubcommandGroup()).thenReturn("diagnostics");
            when(interaction.getSubcommandName()).thenReturn("echo");
            actor(interaction);
            var reply = mock(AutoCompleteCallbackAction.class);
            when(interaction.replyChoices(anyCollection())).thenReturn(reply);
            when(reply.submit()).thenReturn(CompletableFuture.completedFuture(null));

            listener().onEvent(new CommandAutoCompleteInteractionEvent(discord, 1, interaction));

            ArgumentCaptor<Collection<Command.Choice>> choices = ArgumentCaptor.captor();
            verify(interaction).replyChoices(choices.capture());
            assertEquals(1, choices.getValue().size());
            assertEquals("100000000000000002", choices.getValue().iterator().next().getAsString());
            verify(reply).submit();
        }
    }

    @ParameterizedTest
    @CsvSource({"general,,missing", "general,diagnostics,missing", "other,,ping", "general,,"})
    void rejectsUnknownRoutesWithoutCallingPing(String root, String group, String subcommand) {
        try (var application = startApplication()) {
            String guildId = "100000000000000001";
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration(guildId, null));
            var interaction = mock(SlashCommandInteraction.class);
            var guild = guild(guildId);
            when(interaction.getGuild()).thenReturn(guild);
            when(interaction.getName()).thenReturn(root);
            when(interaction.getSubcommandGroup()).thenReturn(group);
            when(interaction.getSubcommandName()).thenReturn(subcommand);
            var reply = mock(ReplyCallbackAction.class, RETURNS_SELF);
            when(interaction.deferReply()).thenReturn(reply);

            listener().onEvent(new SlashCommandInteractionEvent(discord, 1, interaction));

            verify(reply).setContent("Unknown command.");
            verify(reply).setEphemeral(true);
            verify(reply).queue();
            verify(discord, never()).getRestPing();
        }
    }

    @Test
    void rejectsDuplicateRoutesBeforeStartingTheGateway() {
        var failure = startupFailure(new EchoCommand(), new EchoCommand());
        assertTrue(failure instanceof IllegalStateException);
        assertEquals("Duplicate subcommand: general/diagnostics/echo", failure.getMessage());
    }

    @Test
    void registersAndRoutesAnInjectedGroupedCommandAlongsidePing() {
        try (var application = startApplication(new EchoCommand())) {
            String guildId = "100000000000000002";
            application.getBean(GuildConfigurationRepository.class)
                    .saveAndFlush(new GuildConfiguration(guildId, null));
            var guild = guild(guildId);
            var registration = mock(CommandListUpdateAction.class, RETURNS_SELF);
            when(guild.updateCommands()).thenReturn(registration);
            var listener = listener();

            listener.onEvent(new GuildReadyEvent(discord, 1, guild));

            var commands = ArgumentCaptor.forClass(CommandData[].class);
            verify(registration).addCommands(commands.capture());
            var options = commands.getValue()[0].toData().getArray("options");
            assertEquals(2, options.length());
            var group = options.getObject(1);
            assertEquals("diagnostics", group.getString("name"));
            assertEquals("echo", group.getArray("options").getObject(0).getString("name"));

            var interaction = mock(SlashCommandInteraction.class);
            when(interaction.getGuild()).thenReturn(guild);
            when(interaction.getName()).thenReturn("general");
            when(interaction.getSubcommandGroup()).thenReturn("diagnostics");
            when(interaction.getSubcommandName()).thenReturn("echo");
            actor(interaction);
            var reply = mock(ReplyCallbackAction.class, RETURNS_SELF);
            when(interaction.deferReply()).thenReturn(reply);
            when(reply.submit()).thenReturn(CompletableFuture.completedFuture(null));

            listener.onEvent(new SlashCommandInteractionEvent(discord, 1, interaction));

            verify(reply).setContent("Echo from 100000000000000002 by 300000000000000001");
            verify(reply).submit();
            verify(discord, never()).getRestPing();
        }
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
            var interaction = mock(SlashCommandInteraction.class);
            var guild = guildId == null ? null : guild(guildId);
            when(interaction.getGuild()).thenReturn(guild);
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

    private ConfigurableApplicationContext startApplication(SubcommandModule... modules) {
        var application = new SpringApplication(DiscordBotApplication.class);
        application.addInitializers(context -> {
            var beans = (GenericApplicationContext) context;
            beans.registerBean("testDiscordBuilder", JDABuilder.class, () -> builder);
            for (int index = 0; index < modules.length; index++) {
                var module = modules[index];
                beans.registerBean("testCommand" + index, SubcommandModule.class, () -> module);
            }
        });
        return application.run(
                "--server.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--discord.enabled=true",
                "--spring.main.banner-mode=off",
                "--logging.level.root=OFF");
    }

    private Throwable startupFailure(SubcommandModule... modules) {
        reset(builder, discord);
        Throwable failure = assertThrows(RuntimeException.class, () -> startApplication(modules));
        while (failure.getCause() != null) {
            failure = failure.getCause();
        }
        verify(builder, never()).build();
        return failure;
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
        actor(interaction);
        return interaction;
    }

    private static void actor(Interaction interaction) {
        var user = mock(User.class);
        when(user.getId()).thenReturn("300000000000000001");
        when(interaction.getUser()).thenReturn(user);
    }

    private static SlashCommandInteraction echo(Guild guild) {
        var interaction = mock(SlashCommandInteraction.class);
        when(interaction.getGuild()).thenReturn(guild);
        when(interaction.getName()).thenReturn("general");
        when(interaction.getSubcommandGroup()).thenReturn("diagnostics");
        when(interaction.getSubcommandName()).thenReturn("echo");
        actor(interaction);
        return interaction;
    }

    private static class EchoCommand implements SubcommandModule {

        @Override
        public CommandDefinition definition() {
            return new CommandDefinition(
                    new CommandDefinition.Root("general", "General utilities", DefaultMemberPermissions.DISABLED),
                    new CommandDefinition.Group("diagnostics", "Run diagnostics"),
                    new SubcommandData("echo", "Echo the guild")
                            .addOption(OptionType.STRING, "query", "Search diagnostics", false, true));
        }

        @Override
        public CompletionStage<?> handle(CommandContext context, SlashCommandInteractionEvent event) {
            return event.reply("Echo from " + context.guildId() + " by " + context.actorId()).submit();
        }

        @Override
        public CompletionStage<?> handleAutoComplete(CommandContext context, CommandAutoCompleteInteractionEvent event) {
            return event.replyChoice("Guild", context.guildId()).submit();
        }
    }
}
