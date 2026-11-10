package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.commands.CommandRegistry;
import com.github.lucasskywalker64.commands.SubcommandModule;
import com.github.lucasskywalker64.commands.general.GeneralPing;
import com.github.lucasskywalker64.guild.GuildConfigurationRepository;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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

    @Bean
    CommandRegistry commandRegistry(List<SubcommandModule> modules) {
        return new CommandRegistry(modules);
    }

    @Bean
    DiscordListener discordListener(GuildConfigurationRepository configurations, CommandRegistry commands) {
        return new DiscordListener(configurations, commands);
    }

    @Bean
    DiscordLifecycle discordLifecycle(JDABuilder builder, DiscordListener listener) {
        return new DiscordLifecycle(builder, listener);
    }

    @ConfigurationProperties("discord")
    public record DiscordProperties(String token) {
    }
}
