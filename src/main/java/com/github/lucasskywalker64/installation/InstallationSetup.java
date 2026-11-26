package com.github.lucasskywalker64.installation;

import com.github.lucasskywalker64.installation.InstallationConfiguration.DiscordUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck;
import com.github.lucasskywalker64.installation.InstallationConfiguration.SetupStep;
import com.github.lucasskywalker64.installation.InstallationConfiguration.Snapshot;
import com.github.lucasskywalker64.installation.InstallationConfiguration.StorageUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.TwitchUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.UpdateResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class InstallationSetup {
    private final InstallationConfigurationRepository repository;
    private final InstallationConfigurationService configuration;
    private final ProviderConfigurationChecks providers;
    private final TransactionTemplate transactions;

    InstallationSetup(InstallationConfigurationRepository repository, InstallationConfigurationService configuration,
                      ProviderConfigurationChecks providers, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.configuration = configuration;
        this.providers = providers;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(5);
    }

    public Snapshot saveProviders(DiscordUpdate discord, TwitchUpdate twitch) {
        if (discord == null || twitch == null) {
            throw new Rejected(Map.of("providers", "Supply the Discord and Twitch settings."));
        }
        if (!discord.botEnabled()) {
            throw new Rejected(Map.of("discord.botEnabled", "Enable the Discord bot to complete setup."));
        }
        return saveVerified(SetupStep.PROVIDERS, discord, twitch, null, () -> {
            accept(configuration.updateDiscord(discord));
            accept(configuration.updateTwitch(twitch));
            return configuration.updateSetupStep(SetupStep.STORAGE).configuration();
        });
    }

    public Snapshot saveDeployment(String publicUrl) {
        return transactions.execute(_ -> {
            var saved = repository.lock();
            if (saved.setupStep == SetupStep.PROVIDERS) {
                return accept(configuration.updatePublicUrl(publicUrl));
            }
            requireStep(saved, SetupStep.SERVER);
            accept(configuration.updatePublicUrl(publicUrl));
            return configuration.updateSetupStep(SetupStep.PROVIDERS).configuration();
        });
    }

    public Snapshot saveStorage(StorageUpdate update) {
        return saveStorage(SetupStep.STORAGE, update);
    }

    public Snapshot updateDiscord(DiscordUpdate update) {
        return saveVerified(SetupStep.COMPLETE, update, null, null, () -> accept(configuration.updateDiscord(update)));
    }

    public Snapshot updateTwitch(TwitchUpdate update) {
        return saveVerified(SetupStep.COMPLETE, null, update, null, () -> accept(configuration.updateTwitch(update)));
    }

    public Snapshot updatePublicUrl(String publicUrl) {
        return transactions.execute(_ -> {
            requireStep(repository.lock(), SetupStep.COMPLETE);
            return accept(configuration.updatePublicUrl(publicUrl));
        });
    }

    public Snapshot updateStorage(StorageUpdate update) {
        return saveStorage(SetupStep.COMPLETE, update);
    }

    private Snapshot saveStorage(SetupStep step, StorageUpdate update) {
        return saveVerified(step, null, null, update, () -> {
            var verifiedAt = update.enabled() ? Instant.now().truncatedTo(ChronoUnit.MILLIS) : null;
            var snapshot = accept(configuration.updateStorage(update, verifiedAt));
            return step == SetupStep.STORAGE ? configuration.updateSetupStep(SetupStep.COMPLETE).configuration() : snapshot;
        });
    }

    private Snapshot saveVerified(SetupStep step, DiscordUpdate discord, TwitchUpdate twitch, StorageUpdate storage,
                                  Supplier<Snapshot> save) {
        var current = repository.findById(1).orElseThrow();
        requireStep(current, step);
        var inputs = providerInputs(current, discord, twitch, storage);
        var verification = providers.verify(inputs);
        if (!verification.errors().isEmpty()) {
            throw new Rejected(verification.errors(), verification.checks());
        }

        // A concurrent save can change a credential referenced by KEEP while the provider is checking it.
        return transactions.execute(_ -> {
            var saved = repository.lock();
            requireStep(saved, step);
            if (!inputs.equals(providerInputs(saved, discord, twitch, storage))) {
                throw new ChangedDuringVerification();
            }
            return save.get().withProviderChecks(verification.checks());
        });
    }

    private static ProviderConfigurationChecks.Inputs providerInputs(InstallationConfigurationEntity saved,
            DiscordUpdate discord, TwitchUpdate twitch, StorageUpdate storage) {
        var errors = new LinkedHashMap<String, String>();
        var discordSecrets = discord == null ? null : InstallationConfigurationService.validateDiscord(saved, discord, errors);
        var twitchSecret = twitch == null ? null : InstallationConfigurationService.validateTwitch(saved, twitch, errors);
        var storageSecrets = storage == null ? null : InstallationConfigurationService.validateStorage(saved, storage, errors);
        check(errors);
        return new ProviderConfigurationChecks.Inputs(
                discord != null && discord.botEnabled() ? discordSecrets.botToken() : null,
                discord != null && discord.oauthEnabled()
                        ? new ProviderConfigurationChecks.OAuth(discord.clientId(), discordSecrets.oauthSecret()) : null,
                twitch != null && twitch.enabled() ? new ProviderConfigurationChecks.OAuth(twitch.clientId(), twitchSecret) : null,
                storage != null && storage.enabled() ? new ProviderConfigurationChecks.Storage(storage.endpoint(),
                        storage.bucket(), storage.region(), storageSecrets.accessKey(), storageSecrets.secretKey()) : null);
    }

    private static Snapshot accept(UpdateResult result) {
        check(result.errors());
        return result.configuration();
    }

    private static void requireStep(InstallationConfigurationEntity saved, SetupStep step) {
        if (saved.setupStep != step) {
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
        private final Map<String, ProviderCheck> checks;

        Rejected(Map<String, String> errors) {
            this(errors, Map.of());
        }

        Rejected(Map<String, String> errors, Map<String, ProviderCheck> checks) {
            this.errors = Map.copyOf(errors);
            this.checks = Map.copyOf(checks);
        }

        public Map<String, String> errors() {
            return errors;
        }

        public Map<String, ProviderCheck> checks() {
            return checks;
        }
    }

    public static class WrongStep extends RuntimeException {
    }

    public static class ChangedDuringVerification extends RuntimeException {
    }
}
