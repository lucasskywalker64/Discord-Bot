package com.github.lucasskywalker64;

import com.github.lucasskywalker64.installation.admin.LocalAdminRecovery;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

import java.util.Arrays;

@SpringBootApplication
public class DiscordBotApplication {
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("reset-local-admin")) {
            LocalAdminRecovery.run(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        SpringApplication.run(DiscordBotApplication.class, args);
    }
}
