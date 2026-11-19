package com.github.lucasskywalker64;

import com.github.lucasskywalker64.installation.admin.LocalAdminRecovery;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class DiscordBotApplication {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("reset-local-admin")) {
            LocalAdminRecovery.run(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        SpringApplication.run(DiscordBotApplication.class, args);
    }
}
