package com.github.lucasskywalker64.installation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public final class InstallationConfiguration {

    private InstallationConfiguration() {
    }

    public enum SetupStep { PROVIDERS, DEPLOYMENT, STORAGE, COMPLETE }

    public enum SecretAction { KEEP, REPLACE, CLEAR }

    public enum State { DISABLED, CONFIGURED, CONNECTING, CONNECTED, UNAVAILABLE }

    public enum ProviderCheck { SUCCEEDED, FAILED, UNAVAILABLE }

    public record SecretUpdate(SecretAction action, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String value) {
        public static SecretUpdate keep() {
            return new SecretUpdate(SecretAction.KEEP, null);
        }

        public static SecretUpdate replace(String value) {
            return new SecretUpdate(SecretAction.REPLACE, value);
        }

        public static SecretUpdate clear() {
            return new SecretUpdate(SecretAction.CLEAR, null);
        }

        @Override
        public String toString() {
            return "SecretUpdate[action=" + action + ", value=[redacted]]";
        }
    }

    public record DiscordUpdate(boolean botEnabled, SecretUpdate botToken, boolean oauthEnabled,
                                String clientId, SecretUpdate clientSecret, String callbackUrl) {
    }

    public record TwitchUpdate(boolean enabled, String clientId, SecretUpdate clientSecret, String callbackUrl) {
    }

    public record StorageUpdate(boolean enabled, String endpoint, String bucket, String region,
                                SecretUpdate accessKey, SecretUpdate secretKey) {
    }

    public record Status(State state, String message, String lastFailure) {
    }

    public record DiscordView(boolean botEnabled, boolean botTokenConfigured, boolean oauthEnabled,
                              String clientId, boolean clientSecretConfigured, String callbackUrl,
                              long oauthRevision, Status status) {
    }

    public record TwitchView(boolean enabled, String clientId, boolean clientSecretConfigured, String callbackUrl,
                             long oauthRevision, Status status) {
    }

    public record StorageView(boolean enabled, String endpoint, String bucket, String region,
                              boolean accessKeyConfigured, boolean secretKeyConfigured, Status status) {
    }

    public record Snapshot(DiscordView discord, TwitchView twitch, StorageView storage, String publicUrl,
                           SetupStep setupStep, @JsonInclude(JsonInclude.Include.NON_EMPTY)
                           Map<String, ProviderCheck> providerChecks) {
        Snapshot(DiscordView discord, TwitchView twitch, StorageView storage, String publicUrl, SetupStep setupStep) {
            this(discord, twitch, storage, publicUrl, setupStep, Map.of());
        }

        Snapshot withProviderChecks(Map<String, ProviderCheck> checks) {
            return new Snapshot(discord, twitch, storage, publicUrl, setupStep, Map.copyOf(checks));
        }
    }

    public record UpdateResult(Snapshot configuration, Map<String, String> errors) {
        public UpdateResult {
            errors = Map.copyOf(errors);
        }
    }
}
