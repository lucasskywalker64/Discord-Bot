package com.github.lucasskywalker64.installation;

import com.github.lucasskywalker64.installation.InstallationConfiguration.DiscordUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.SetupStep;
import com.github.lucasskywalker64.installation.InstallationConfiguration.Snapshot;
import com.github.lucasskywalker64.installation.InstallationConfiguration.StorageUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.TwitchUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.UpdateResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Service
@Transactional(timeout = 5)
public class InstallationSetup {
    private final InstallationConfigurationRepository repository;
    private final InstallationConfigurationService configuration;

    InstallationSetup(InstallationConfigurationRepository repository, InstallationConfigurationService configuration) {
        this.repository = repository;
        this.configuration = configuration;
    }

    public Snapshot saveProviders(DiscordUpdate discord, TwitchUpdate twitch) {
        requireStep(SetupStep.PROVIDERS);
        if (discord == null || twitch == null) {
            throw new Rejected(Map.of("providers", "Supply the Discord and Twitch settings."));
        }
        if (!discord.botEnabled()) {
            throw new Rejected(Map.of("discord.botEnabled", "Enable the Discord bot to complete setup."));
        }
        var errors = new LinkedHashMap<>(configuration.updateDiscord(discord).errors());
        errors.putAll(configuration.updateTwitch(twitch).errors());
        check(errors);
        return configuration.updateSetupStep(SetupStep.DEPLOYMENT).configuration();
    }

    public Snapshot saveDeployment(String publicUrl) {
        requireStep(SetupStep.DEPLOYMENT);
        check(configuration.updatePublicUrl(publicUrl).errors());
        return configuration.updateSetupStep(SetupStep.STORAGE).configuration();
    }

    public Snapshot saveStorage(StorageUpdate update) {
        requireStep(SetupStep.STORAGE);
        check(configuration.updateStorage(update).errors());
        return configuration.updateSetupStep(SetupStep.COMPLETE).configuration();
    }

    public Snapshot updateDiscord(DiscordUpdate update) {
        return updateSettings(() -> configuration.updateDiscord(update));
    }

    public Snapshot updateTwitch(TwitchUpdate update) {
        return updateSettings(() -> configuration.updateTwitch(update));
    }

    public Snapshot updatePublicUrl(String publicUrl) {
        return updateSettings(() -> configuration.updatePublicUrl(publicUrl));
    }

    public Snapshot updateStorage(StorageUpdate update) {
        return updateSettings(() -> configuration.updateStorage(update));
    }

    private Snapshot updateSettings(Supplier<UpdateResult> operation) {
        requireStep(SetupStep.COMPLETE);
        UpdateResult result = operation.get();
        check(result.errors());
        return result.configuration();
    }

    private void requireStep(SetupStep step) {
        if (repository.lock().setupStep != step) {
            throw new WrongStep();
        }
    }

    private static void check(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new Rejected(errors);
        }
    }

    public static class Rejected extends RuntimeException {
        private final Map<String, String> errors;

        Rejected(Map<String, String> errors) {
            this.errors = Map.copyOf(errors);
        }

        public Map<String, String> errors() {
            return errors;
        }
    }

    public static class WrongStep extends RuntimeException {
    }
}
