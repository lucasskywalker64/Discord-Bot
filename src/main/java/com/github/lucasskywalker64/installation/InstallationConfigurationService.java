package com.github.lucasskywalker64.installation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.github.lucasskywalker64.installation.InstallationConfiguration.DiscordUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.DiscordView;
import com.github.lucasskywalker64.installation.InstallationConfiguration.SecretUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.SetupStep;
import com.github.lucasskywalker64.installation.InstallationConfiguration.Snapshot;
import com.github.lucasskywalker64.installation.InstallationConfiguration.State;
import com.github.lucasskywalker64.installation.InstallationConfiguration.Status;
import com.github.lucasskywalker64.installation.InstallationConfiguration.StorageUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.StorageView;
import com.github.lucasskywalker64.installation.InstallationConfiguration.TwitchUpdate;
import com.github.lucasskywalker64.installation.InstallationConfiguration.TwitchView;
import com.github.lucasskywalker64.installation.InstallationConfiguration.UpdateResult;

@Service
@Transactional(timeout = 5)
public class InstallationConfigurationService {

    private final InstallationConfigurationRepository configurations;
    private final ApplicationEventPublisher events;

    public InstallationConfigurationService(InstallationConfigurationRepository configurations, ApplicationEventPublisher events) {
        this.configurations = configurations;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Snapshot read() {
        return snapshot(configurations.findById(1).orElseThrow());
    }

    public UpdateResult updateDiscord(DiscordUpdate update) {
        var saved = configurations.lock();
        var errors = new LinkedHashMap<String, String>();
        var secrets = validateDiscord(saved, update, errors);
        if (!errors.isEmpty()) {
            return new UpdateResult(snapshot(saved), errors);
        }
        String token = secrets.botToken().value();
        String clientSecret = secrets.oauthSecret().value();
        if (!Objects.equals(saved.discordClientId, update.clientId())
                || !Objects.equals(saved.discordClientSecret, clientSecret)) {
            saved.discordOauthRevision++;
        }
        if (saved.discordBotEnabled != update.botEnabled() || !Objects.equals(saved.discordBotToken, token)) {
            saved.discordBotRevision++;
            saved.discordState = update.botEnabled() ? State.CONNECTING : State.DISABLED;
        }
        saved.discordBotEnabled = update.botEnabled();
        saved.discordBotToken = token;
        saved.discordOauthEnabled = update.oauthEnabled();
        saved.discordClientId = update.clientId();
        saved.discordClientSecret = clientSecret;
        saved.discordCallbackUrl = update.callbackUrl();
        events.publishEvent(new Changed(saved.discordBotRevision));
        return new UpdateResult(snapshot(saved), Map.of());
    }

    public UpdateResult updatePublicUrl(String publicUrl) {
        var saved = configurations.lock();
        var errors = new LinkedHashMap<String, String>();
        url("publicUrl", publicUrl, false, errors);
        if (errors.isEmpty()) {
            saved.publicUrl = publicUrl;
            events.publishEvent(new Changed(saved.discordBotRevision));
        }
        return new UpdateResult(snapshot(saved), errors);
    }

    public UpdateResult updateTwitch(TwitchUpdate update) {
        var saved = configurations.lock();
        var errors = new LinkedHashMap<String, String>();
        String clientSecret = validateTwitch(saved, update, errors).value();
        if (!errors.isEmpty()) {
            return new UpdateResult(snapshot(saved), errors);
        }
        if (!Objects.equals(saved.twitchClientId, update.clientId())
                || !Objects.equals(saved.twitchClientSecret, clientSecret)) {
            saved.twitchOauthRevision++;
        }
        saved.twitchEnabled = update.enabled();
        saved.twitchClientId = update.clientId();
        saved.twitchClientSecret = clientSecret;
        saved.twitchCallbackUrl = update.callbackUrl();
        events.publishEvent(new Changed(saved.discordBotRevision));
        return new UpdateResult(snapshot(saved), Map.of());
    }

    UpdateResult updateStorage(StorageUpdate update, Instant verifiedAt) {
        var saved = configurations.lock();
        var errors = new LinkedHashMap<String, String>();
        var secrets = validateStorage(saved, update, errors);
        if (!errors.isEmpty()) {
            return new UpdateResult(snapshot(saved), errors);
        }
        saved.storageEnabled = update.enabled();
        saved.storageEndpoint = update.endpoint();
        saved.storageBucket = update.bucket();
        saved.storageRegion = update.region();
        saved.storageAccessKey = secrets.accessKey().value();
        saved.storageSecretKey = secrets.secretKey().value();
        saved.storageVerifiedAt = update.enabled() ? verifiedAt : null;
        events.publishEvent(new Changed(saved.discordBotRevision));
        return new UpdateResult(snapshot(saved), Map.of());
    }

    public UpdateResult updateSetupStep(SetupStep step) {
        var saved = configurations.lock();
        if (step == null) {
            return new UpdateResult(snapshot(saved), Map.of("setupStep", "Choose a setup step."));
        }
        saved.setupStep = step;
        return new UpdateResult(snapshot(saved), Map.of());
    }

    public UpdateResult retryDiscord() {
        var saved = configurations.lock();
        if (saved.discordBotEnabled) {
            saved.discordBotRevision++;
            saved.discordState = State.CONNECTING;
            events.publishEvent(new Changed(saved.discordBotRevision));
        }
        return new UpdateResult(snapshot(saved), Map.of());
    }

    // Server-only connection input. Never expose this as a browser operation.
    @Transactional(readOnly = true)
    public DiscordConnectionSettings discordConnectionSettings() {
        var saved = configurations.findById(1).orElseThrow();
        return new DiscordConnectionSettings(saved.discordBotEnabled, saved.discordBotToken, saved.discordBotRevision);
    }

    public void reportDiscordState(long revision, State state) {
        var saved = configurations.lock();
        if (saved.discordBotRevision == revision && saved.discordBotEnabled) {
            saved.discordState = state;
            if (state == State.UNAVAILABLE) {
                saved.discordFailure = "Discord connection failed. Check the bot token and provider availability.";
            }
        }
    }

    public record DiscordConnectionSettings(boolean enabled, @JsonIgnore String token, long revision) {
        @Override
        public String toString() {
            return "DiscordConnectionSettings[enabled=" + enabled + ", token=[redacted], revision=" + revision + "]";
        }
    }

    private static Snapshot snapshot(InstallationConfigurationEntity saved) {
        var status = new Status(saved.discordState, switch (saved.discordState) {
            case DISABLED -> "Enable Discord and supply a bot token.";
            case CONNECTING -> "Connecting to Discord.";
            case CONNECTED -> "Discord is connected.";
            case UNAVAILABLE -> "Discord is unavailable. Check the bot token and provider status, then retry.";
            case CONFIGURED -> "Discord is configured.";
        }, saved.discordFailure);
        return new Snapshot(new DiscordView(saved.discordBotEnabled, present(saved.discordBotToken),
                saved.discordOauthEnabled, saved.discordClientId, present(saved.discordClientSecret),
                saved.discordCallbackUrl, saved.discordOauthRevision, status),
                new TwitchView(saved.twitchEnabled, saved.twitchClientId, present(saved.twitchClientSecret),
                        saved.twitchCallbackUrl, saved.twitchOauthRevision, configuredStatus(saved.twitchEnabled)),
                new StorageView(saved.storageEnabled, saved.storageEndpoint, saved.storageBucket, saved.storageRegion,
                        present(saved.storageAccessKey), present(saved.storageSecretKey), saved.storageEnabled
                                ? new Status(State.CONFIGURED, "Storage settings are saved.", null)
                                : configuredStatus(false), saved.storageVerifiedAt),
                saved.publicUrl, saved.setupStep);
    }

    private static Status configuredStatus(boolean enabled) {
        return new Status(enabled ? State.CONFIGURED : State.DISABLED,
                enabled ? "Configuration saved. Connectivity is checked by the consuming feature."
                        : "This capability is disabled.", null);
    }

    private static String secret(String field, SecretUpdate update, String saved, Map<String, String> errors) {
        if (update == null || update.action() == null) {
            errors.put(field, "Choose KEEP, REPLACE or CLEAR for this secret.");
            return saved;
        }
        return switch (update.action()) {
            case KEEP -> saved;
            case CLEAR -> null;
            case REPLACE -> {
                required(field, update.value(), errors);
                yield update.value();
            }
        };
    }

    static DiscordSecrets validateDiscord(InstallationConfigurationEntity saved, DiscordUpdate update,
                                           Map<String, String> errors) {
        String token = secret("discord.botToken", update.botToken(), saved.discordBotToken, errors);
        String clientSecret = secret("discord.clientSecret", update.clientSecret(), saved.discordClientSecret, errors);
        if (update.botEnabled()) {
            required("discord.botToken", token, errors);
        }
        if (update.oauthEnabled()) {
            required("discord.clientId", update.clientId(), errors);
            required("discord.clientSecret", clientSecret, errors);
            url("discord.callbackUrl", update.callbackUrl(), true, errors);
        }
        return new DiscordSecrets(SecretUpdate.replace(token), SecretUpdate.replace(clientSecret));
    }

    static SecretUpdate validateTwitch(InstallationConfigurationEntity saved, TwitchUpdate update,
                                       Map<String, String> errors) {
        String clientSecret = secret("twitch.clientSecret", update.clientSecret(), saved.twitchClientSecret, errors);
        if (update.enabled()) {
            required("twitch.clientId", update.clientId(), errors);
            required("twitch.clientSecret", clientSecret, errors);
            url("twitch.callbackUrl", update.callbackUrl(), true, errors);
        }
        return SecretUpdate.replace(clientSecret);
    }

    static StorageSecrets validateStorage(InstallationConfigurationEntity saved, StorageUpdate update,
                                           Map<String, String> errors) {
        String accessKey = secret("storage.accessKey", update.accessKey(), saved.storageAccessKey, errors);
        String secretKey = secret("storage.secretKey", update.secretKey(), saved.storageSecretKey, errors);
        if (update.enabled()) {
            url("storage.endpoint", update.endpoint(), true, errors);
            required("storage.bucket", update.bucket(), errors);
            required("storage.region", update.region(), errors);
            required("storage.accessKey", accessKey, errors);
            required("storage.secretKey", secretKey, errors);
        }
        return new StorageSecrets(SecretUpdate.replace(accessKey), SecretUpdate.replace(secretKey));
    }

    record DiscordSecrets(SecretUpdate botToken, SecretUpdate oauthSecret) {
    }

    record StorageSecrets(SecretUpdate accessKey, SecretUpdate secretKey) {
    }

    private static void required(String field, String value, Map<String, String> errors) {
        if (!present(value)) {
            errors.put(field, "This field is required when the capability is enabled.");
        }
    }

    private static void url(String field, String value, boolean required, Map<String, String> errors) {
        if (!present(value) && !required) {
            return;
        }
        try {
            var uri = URI.create(value == null ? "" : value);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                errors.put(field, "Use an absolute HTTP or HTTPS URL without credentials or a fragment.");
            }
        } catch (IllegalArgumentException _) {
            errors.put(field, "Use a valid HTTP or HTTPS URL.");
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    public record Changed(long discordBotRevision) {
    }
}
