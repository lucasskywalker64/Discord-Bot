package com.github.lucasskywalker64.discord;

import com.github.lucasskywalker64.guild.GuildConfigurationRepository;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    DiscordListener discordListener(GuildConfigurationRepository configurations) {
        return new DiscordListener(configurations);
    }

    @Bean
    DiscordLifecycle discordLifecycle(JDABuilder builder, DiscordListener listener) {
        return new DiscordLifecycle(builder, listener);
    }

    @ConfigurationProperties("discord")
    public record DiscordProperties(String token) {
    }
}
