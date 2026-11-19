package com.github.lucasskywalker64.installation.admin;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.util.Arrays;

@Import(LocalAdministrator.class)
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        JdbcClientAutoConfiguration.class})
public class LocalAdminRecovery {
    public static void run(String[] arguments) {
        var terminal = System.console();
        if (terminal == null) {
            throw new IllegalStateException("Run reset-local-admin from an interactive terminal.");
        }
        char[] password = terminal.readPassword("New local administrator password: ");
        char[] confirmation = null;
        try {
            confirmation = terminal.readPassword("Confirm new password: ");
            if (password == null || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("The passwords must match.");
            }
            reset(new String(password), arguments);
            terminal.printf("Local administrator password reset.%n");
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }

    static void reset(String password, String[] arguments) {
        if (!LocalAdministrator.validPassword(password)) {
            throw new IllegalArgumentException(LocalAdministrator.PASSWORD_LENGTH_MESSAGE);
        }
        try (var context = new SpringApplicationBuilder(LocalAdminRecovery.class)
                .web(WebApplicationType.NONE).registerShutdownHook(false).run(arguments)) {
            if (!context.getBean(LocalAdministrator.class).resetPassword(password)) {
                throw new IllegalStateException("Create the local administrator in the private console first.");
            }
        }
    }
}
