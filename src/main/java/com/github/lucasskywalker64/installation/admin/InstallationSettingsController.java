package com.github.lucasskywalker64.installation.admin;

import com.github.lucasskywalker64.installation.InstallationConfiguration.DiscordUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.Snapshot;
import com.github.lucasskywalker64.installation.InstallationConfiguration.StorageUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.TwitchUpdate;
import com.github.lucasskywalker64.installation.InstallationConfigurationService;
import com.github.lucasskywalker64.installation.InstallationSetup;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/api")
class InstallationSettingsController {
    private final InstallationConfigurationService configuration;
    private final InstallationSetup setup;

    InstallationSettingsController(InstallationConfigurationService configuration, InstallationSetup setup) {
        this.configuration = configuration;
        this.setup = setup;
    }

    @GetMapping("/configuration")
    Snapshot configuration() {
        return configuration.read();
    }

    @PostMapping("/setup/providers")
    Snapshot setupProviders(@RequestBody ProvidersUpdate update) {
        return setup.saveProviders(update.discord(), update.twitch());
    }

    @PostMapping("/setup/deployment")
    Snapshot setupDeployment(@RequestBody DeploymentUpdate update) {
        return setup.saveDeployment(update.publicUrl());
    }

    @PostMapping("/setup/storage")
    Snapshot setupStorage(@RequestBody StorageUpdate update) {
        return setup.saveStorage(update);
    }

    @PostMapping("/configuration/discord")
    Snapshot saveDiscord(@RequestBody DiscordUpdate update) {
        return setup.updateDiscord(update);
    }

    @PostMapping("/configuration/twitch")
    Snapshot saveTwitch(@RequestBody TwitchUpdate update) {
        return setup.updateTwitch(update);
    }

    @PostMapping("/configuration/deployment")
    Snapshot saveDeployment(@RequestBody DeploymentUpdate update) {
        return setup.updatePublicUrl(update.publicUrl());
    }

    @PostMapping("/configuration/storage")
    Snapshot saveStorage(@RequestBody StorageUpdate update) {
        return setup.updateStorage(update);
    }

    @PostMapping("/configuration/discord/retry")
    Snapshot retryDiscord() {
        return configuration.retryDiscord().configuration();
    }

    @ExceptionHandler(InstallationSetup.Rejected.class)
    ResponseEntity<Map<String, Map<String, String>>> invalid(InstallationSetup.Rejected rejected) {
        return ResponseEntity.badRequest().body(Map.of("errors", rejected.errors()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Map<String, String>>> malformedSettings() {
        return ResponseEntity.badRequest().body(Map.of("errors", Map.of("form", "Check the submitted settings.")));
    }

    @ExceptionHandler(InstallationSetup.WrongStep.class)
    ResponseEntity<Map<String, Map<String, String>>> wrongStep() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("errors", Map.of("setupStep", "Setup has moved to a different step. Reload the console.")));
    }

    record ProvidersUpdate(DiscordUpdate discord, TwitchUpdate twitch) {
    }

    record DeploymentUpdate(String publicUrl) {
    }
}
