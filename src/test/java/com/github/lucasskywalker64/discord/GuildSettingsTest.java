package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.DiscordBotApplication;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@Testcontainers
@ExtendWith(MockitoExtension.class)
// The shared Discord fixture supplies success, rejection and failure responses; each request uses one path.
@MockitoSettings(strictness = Strictness.LENIENT)
class GuildSettingsTest {

    private static final String GUILD_A = "100000000000000001";
    private static final String GUILD_B = "100000000000000002";
    private static final String OWNER_A = "300000000000000001";
    private static final String OWNER_B = "300000000000000002";
    private static final String CHANNEL_A = "200000000000000001";
    private static final String CHANNEL_B = "200000000000000002";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private JDA discord;

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void ownersSetIndependentChannelsAndReadThemAfterRestart() throws Exception {
        provisionGuilds();
        try (var application = startApplication()) {
            var guildA = guild(GUILD_A, OWNER_A);
            var guildB = guild(GUILD_B, OWNER_B);
            assertEquals("Member-count voice channel is not set.", invoke(application, guildA, OWNER_A, null));
            assertEquals("Member-count voice channel set to <#200000000000000001>.",
                    invoke(application, guildA, OWNER_A, channel(guildA, CHANNEL_A)));
            assertEquals("Member-count voice channel set to <#200000000000000002>.",
                    invoke(application, guildB, OWNER_B, channel(guildB, CHANNEL_B)));
            assertEquals("Member-count voice channel set to <#200000000000000003>.",
                    invoke(application, guildA, OWNER_A, channel(guildA, "200000000000000003")));
        }
        try (var application = startApplication()) {
            assertEquals("Member-count voice channel: <#200000000000000003>.",
                    invoke(application, guild(GUILD_A, OWNER_A), OWNER_A, null));
            assertEquals("Member-count voice channel: <#200000000000000002>.",
                    invoke(application, guild(GUILD_B, OWNER_B), OWNER_B, null));
        }
    }

    @Test
    void rejectsNonOwnersForReadsAndWritesAndUsesCurrentOwnership() throws Exception {
        provisionGuilds();
        try (var application = startApplication()) {
            var guildA = guild(GUILD_A, OWNER_A);
            var guildB = guild(GUILD_B, OWNER_B);
            invoke(application, guildA, OWNER_A, channel(guildA, CHANNEL_A));
            invoke(application, guildB, OWNER_B, channel(guildB, CHANNEL_B));
            String denial = "Only the current guild owner can read or change this setting.";
            assertEquals(denial, invoke(application, guildA, OWNER_B, null));
            assertEquals(denial, invoke(application, guildA, OWNER_B, channel(guildA, "200000000000000003")));
            when(guildA.getOwnerIdLong()).thenReturn(Long.parseUnsignedLong(OWNER_B));
            assertEquals(denial, invoke(application, guildA, OWNER_A, null));
            assertEquals("Member-count voice channel: <#200000000000000001>.",
                    invoke(application, guildA, OWNER_B, null));
            assertEquals("Member-count voice channel: <#200000000000000002>.",
                    invoke(application, guildB, OWNER_B, null));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "text", "missing"})
    void rejectsInvalidChannelsWithoutChangingEitherGuild(String kind) throws Exception {
        provisionGuilds();
        try (var application = startApplication()) {
            var guildA = guild(GUILD_A, OWNER_A);
            var guildB = guild(GUILD_B, OWNER_B);
            invoke(application, guildA, OWNER_A, channel(guildA, CHANNEL_A));
            invoke(application, guildB, OWNER_B, channel(guildB, CHANNEL_B));
            var invalid = channel(kind.equals("foreign") ? guildB : guildA, "200000000000000003");
            if (kind.equals("text")) {
                when(invalid.getType()).thenReturn(ChannelType.TEXT);
            } else if (kind.equals("missing")) {
                when(guildA.getVoiceChannelById("200000000000000003")).thenReturn(null);
            }
            assertEquals("Choose an existing voice channel in this guild.",
                    invoke(application, guildA, OWNER_A, invalid));
            assertEquals("Member-count voice channel: <#200000000000000001>.",
                    invoke(application, guildA, OWNER_A, null));
            assertEquals("Member-count voice channel: <#200000000000000002>.",
                    invoke(application, guildB, OWNER_B, null));
        }
    }

    @Test
    void registersSettingsOnlyForGuildsProvisionedBeforeStartup() throws Exception {
        provisionGuilds();
        try (var application = startApplication()) {
            var guild = guild(GUILD_A, OWNER_A);
            var registration = mock(CommandListUpdateAction.class, RETURNS_SELF);
            when(guild.updateCommands()).thenReturn(registration);
            var listener = application.getBean(DiscordListener.class);
            listener.onEvent(new GuildReadyEvent(discord, 1, guild));
            var commands = ArgumentCaptor.forClass(CommandData[].class);
            verify(registration).addCommands(commands.capture());
            var general = (SlashCommandData) commands.getValue()[0];
            assertEquals("General utilities", general.getDescription());
            assertEquals(DefaultMemberPermissions.DISABLED, general.getDefaultPermissions());
            var setting = general.getSubcommands().stream()
                    .filter(command -> command.getName().equals("addmembercount")).findFirst().orElseThrow();
            assertFalse(setting.getOptions().getFirst().isRequired());
            assertEquals(List.of(ChannelType.VOICE), List.copyOf(setting.getOptions().getFirst().getChannelTypes()));

            var unknown = guild("100000000000000003", OWNER_A);
            listener.onEvent(new GuildReadyEvent(discord, 2, unknown));
            verify(unknown, never()).updateCommands();
            assertEquals("Use this command in a configured guild.", invoke(application, unknown, OWNER_A, null));
            assertEquals("Use this command in a configured guild.", invoke(application, null, OWNER_A, null));
        }
    }

    @Test
    void reportsStorageFailureAfterAcknowledgementAndRecoversWithoutChangingSettings() throws Exception {
        provisionGuilds();
        try (var application = startApplication()) {
            var guild = guild(GUILD_A, OWNER_A);
            var jdbc = application.getBean(JdbcTemplate.class);
            jdbc.execute("ALTER TABLE guild_configuration RENAME TO unavailable_configuration");
            try {
                assertEquals("Command failed. Please try again.", invoke(application, guild, OWNER_A, null));
                assertEquals("Command failed. Please try again.",
                        invoke(application, guild, OWNER_A, channel(guild, CHANNEL_A)));
            } finally {
                jdbc.execute("ALTER TABLE unavailable_configuration RENAME TO guild_configuration");
            }
            assertEquals("Member-count voice channel is not set.", invoke(application, guild, OWNER_A, null));
        }
    }

    @Test
    void acknowledgesBeforeStorageAndDoesNotBlockTheGatewayCallback() throws Exception {
        provisionGuilds();
        try (var application = startApplication(); var gateway = Executors.newSingleThreadExecutor()) {
            var guild = guild(GUILD_A, OWNER_A);
            var request = request(guild, OWNER_A, channel(guild, CHANNEL_A));
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.execute("LOCK TABLE guild_configuration IN ACCESS EXCLUSIVE MODE");
                }
                gateway.submit(() -> dispatch(application, request)).get(5, TimeUnit.SECONDS);
                assertFalse(request.result().isDone());
                gateway.submit(() -> request.acknowledged().complete(request.hook())).get(5, TimeUnit.SECONDS);
                assertFalse(request.result().isDone());
                connection.commit();
            }
            assertEquals("Member-count voice channel set to <#200000000000000001>.",
                    request.result().get(10, TimeUnit.SECONDS));
            verify(request.reply()).setEphemeral(true);
        }
    }

    private void provisionGuilds() throws InterruptedException {
        try (var application = startApplication()) {
            application.getBean(JdbcTemplate.class).update(
                    "INSERT INTO guild_configuration (guild_id) VALUES (?), (?)", GUILD_A, GUILD_B);
        }
    }

    private ConfigurableApplicationContext startApplication() throws InterruptedException {
        discord = mock(JDA.class);
        when(discord.awaitShutdown(Duration.ofSeconds(10))).thenReturn(true);
        var builder = mock(JDABuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(discord);
        var application = new SpringApplication(DiscordBotApplication.class);
        application.addInitializers(context -> ((GenericApplicationContext) context)
                .registerBean("testDiscordBuilder", JDABuilder.class, () -> builder));
        return application.run(
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--discord.enabled=true", "--spring.main.banner-mode=off", "--logging.level.root=OFF");
    }

    private String invoke(ConfigurableApplicationContext application, Guild guild, String actor,
                          GuildChannelUnion channel) throws Exception {
        var request = request(guild, actor, channel);
        dispatch(application, request);
        request.acknowledged().complete(request.hook());
        String result = request.result().get(10, TimeUnit.SECONDS);
        verify(request.reply()).setEphemeral(true);
        return result;
    }

    private void dispatch(ConfigurableApplicationContext application, Request request) {
        application.getBean(DiscordListener.class)
                .onEvent(new SlashCommandInteractionEvent(discord, 1, request.interaction()));
    }

    private static Request request(Guild guild, String actor, GuildChannelUnion channel) {
        var interaction = mock(SlashCommandInteraction.class);
        when(interaction.getGuild()).thenReturn(guild);
        when(interaction.getName()).thenReturn("general");
        when(interaction.getSubcommandName()).thenReturn("addmembercount");
        var user = mock(User.class);
        when(user.getId()).thenReturn(actor);
        when(interaction.getUser()).thenReturn(user);
        var member = mock(Member.class);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(interaction.getMember()).thenReturn(member);
        if (channel != null) {
            var option = mock(OptionMapping.class);
            when(option.getName()).thenReturn("channel");
            when(option.getAsChannel()).thenReturn(channel);
            when(interaction.getOptions()).thenReturn(List.of(option));
        }
        var result = new CompletableFuture<String>();
        var reply = mock(ReplyCallbackAction.class, RETURNS_SELF);
        when(interaction.deferReply()).thenReturn(reply);
        doAnswer(call -> { result.complete(call.getArgument(0)); return reply; })
                .when(reply).setContent(anyString());
        var hook = mock(InteractionHook.class);
        var acknowledged = new CompletableFuture<InteractionHook>();
        when(reply.submit()).thenReturn(acknowledged);
        when(interaction.isAcknowledged()).thenAnswer(call -> acknowledged.isDone());
        when(interaction.getHook()).thenReturn(hook);
        WebhookMessageEditAction<Message> response = mock();
        when(hook.editOriginal(anyString())).thenAnswer(call -> {
            String content = call.getArgument(0);
            when(response.submit()).thenAnswer(ignored -> {
                result.complete(content);
                return CompletableFuture.completedFuture(null);
            });
            return response;
        });
        return new Request(interaction, reply, hook, acknowledged, result);
    }

    private record Request(SlashCommandInteraction interaction, ReplyCallbackAction reply, InteractionHook hook,
                           CompletableFuture<InteractionHook> acknowledged, CompletableFuture<String> result) {
    }

    private static Guild guild(String id, String ownerId) {
        var guild = mock(Guild.class);
        when(guild.getId()).thenReturn(id);
        when(guild.getOwnerIdLong()).thenReturn(Long.parseUnsignedLong(ownerId));
        return guild;
    }

    private static GuildChannelUnion channel(Guild guild, String id) {
        var channel = mock(GuildChannelUnion.class);
        when(channel.getType()).thenReturn(ChannelType.VOICE);
        when(channel.getGuild()).thenReturn(guild);
        when(channel.getId()).thenReturn(id);
        when(guild.getVoiceChannelById(id)).thenReturn(mock(VoiceChannel.class));
        return channel;
    }
}
