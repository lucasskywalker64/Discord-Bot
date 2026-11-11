package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.commands.CommandRegistry;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.commands.general.GeneralPing;
import com.github.lucasskywalker64.commands.general.GeneralMemberCountAdd;
import com.github.lucasskywalker64.guild.GuildConfigurationRepository;
import com.github.lucasskywalker64.guild.ConfiguredGuilds;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "discord.enabled", havingValue = "true")
@EnableConfigurationProperties(DiscordConfiguration.DiscordProperties.class)
public class DiscordConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JDABuilder discordBuilder(DiscordProperties properties) {
        return JDABuilder.createLight(properties.token());
    }

    @Bean
    GeneralPing generalPing() {
        return new GeneralPing();
    }

    @Bean(destroyMethod = "close")
    ExecutorService guildSettingsExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    GeneralMemberCountAdd generalMemberCountAdd(GuildConfigurationRepository configurations,
                                               ExecutorService guildSettingsExecutor) {
        return new GeneralMemberCountAdd(configurations, guildSettingsExecutor);
    }

    @Bean
    CommandRegistry commandRegistry(List<SubcommandModule> modules) {
        return new CommandRegistry(modules);
    }

    @Bean
    ConfiguredGuilds configuredGuilds(GuildConfigurationRepository configurations) {
        return new ConfiguredGuilds(configurations);
    }

    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean
    ScheduledExecutorService discordRegistrationScheduler() {
        return Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("discord-registration").factory());
    }

    @Bean
    GuildCommandRegistration guildCommandRegistration(ConfiguredGuilds configuredGuilds, CommandRegistry commands,
                                                      ScheduledExecutorService discordRegistrationScheduler) {
        return new GuildCommandRegistration(configuredGuilds, commands, discordRegistrationScheduler);
    }

    @Bean
    DiscordListener discordListener(ConfiguredGuilds configuredGuilds, CommandRegistry commands,
                                     GuildCommandRegistration registrations) {
        return new DiscordListener(configuredGuilds, commands, registrations);
    }

    @Bean
    DiscordLifecycle discordLifecycle(JDABuilder builder, DiscordListener listener, GuildCommandRegistration registrations) {
        return new DiscordLifecycle(builder.setEnableShutdownHook(false).addEventListeners(listener), registrations);
    }

    @ConfigurationProperties("discord")
    public record DiscordProperties(String token) {
    }
}
