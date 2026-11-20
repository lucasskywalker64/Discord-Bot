package com.github.lucasskywalker64.installation;

import com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck;
import com.github.lucasskywalker64.installation.InstallationConfiguration.SecretUpdate;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck.FAILED;
import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck.SUCCEEDED;
import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck.UNAVAILABLE;

@Component
class ProviderConfigurationChecks {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private final OkHttpClient http = new OkHttpClient.Builder().callTimeout(TIMEOUT)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    private final JsonMapper json = new JsonMapper();
    private final String discordApi;
    private final String twitchApi;

    ProviderConfigurationChecks() {
        this("https://discord.com/api/v10", "https://id.twitch.tv");
    }

    ProviderConfigurationChecks(String discordApi, String twitchApi) {
        this.discordApi = discordApi;
        this.twitchApi = twitchApi;
    }

    Verification verify(Inputs inputs) {
        var checks = new LinkedHashMap<String, ProviderCheck>();
        var errors = new LinkedHashMap<String, String>();
        if (inputs.botToken() != null) {
            checks.put("discord.botToken", discordBot(inputs.botToken().value()));
        }
        if (inputs.discordOAuth() != null) {
            checks.put("discord.clientSecret", discordOAuth(inputs.discordOAuth()));
        }
        if (inputs.twitch() != null) {
            checks.put("twitch.clientSecret", twitch(inputs.twitch()));
        }
        if (inputs.storage() != null) {
            var result = storage(inputs.storage());
            checks.put("storage.endpoint", result.state());
            if (result.error() != null) {
                errors.put("storage.endpoint", result.error());
            }
        }
        checks.forEach((field, state) -> {
            if (state == UNAVAILABLE) {
                errors.put(field, "The provider could not verify these settings right now. Try again.");
            } else if (state == FAILED) {
                errors.putIfAbsent(field, "The provider rejected these credentials. Check them and try again.");
            }
        });
        return new Verification(Map.copyOf(checks), Map.copyOf(errors));
    }

    private ProviderCheck discordBot(String token) {
        if (!token.chars().allMatch(character -> character > 32 && character < 127)) {
            return FAILED;
        }
        return request(new Request.Builder().url(discordApi + "/users/@me")
                .header("Authorization", "Bot " + token).build(), response -> response.path("bot").asBoolean(false));
    }

    private ProviderCheck discordOAuth(OAuth credentials) {
        var form = new FormBody.Builder().add("grant_type", "client_credentials").add("scope", "identify").build();
        return request(new Request.Builder().url(discordApi + "/oauth2/token").post(form)
                .header("Authorization", Credentials.basic(credentials.clientId(), credentials.secret().value())).build(),
                ProviderConfigurationChecks::containsToken);
    }

    private ProviderCheck twitch(OAuth credentials) {
        var form = new FormBody.Builder().add("grant_type", "client_credentials")
                .add("client_id", credentials.clientId()).add("client_secret", credentials.secret().value()).build();
        return request(new Request.Builder().url(twitchApi + "/oauth2/token").post(form).build(),
                ProviderConfigurationChecks::containsToken);
    }

    private ProviderCheck request(Request request, Predicate<JsonNode> accepted) {
        try (var response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return failure(response.code());
            }
            return accepted.test(json.readTree(response.body().byteStream())) ? SUCCEEDED : FAILED;
        } catch (IOException | JacksonException _) {
            return UNAVAILABLE;
        }
    }

    private static boolean containsToken(JsonNode response) {
        return !response.path("access_token").asString("").isBlank();
    }

    private static StorageCheck storage(Storage credentials) {
        try (var client = S3Client.builder().endpointOverride(URI.create(credentials.endpoint()))
                .region(Region.of(credentials.region())).forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(credentials.accessKey().value(), credentials.secretKey().value())))
                .overrideConfiguration(config -> config.apiCallTimeout(TIMEOUT)
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).build()) {
            try {
                var response = client.headBucket(request -> request.bucket(credentials.bucket()));
                if (response.bucketRegion() == null || credentials.region().equals(response.bucketRegion())) {
                    return new StorageCheck(SUCCEEDED, null);
                }
            } catch (S3Exception rejected) {
                if (!Set.of(400, 401, 403, 404).contains(rejected.statusCode())) {
                    throw rejected;
                }
            }

            // A listing verifies the configured signing region and can return detailed errors after a rejected HEAD.
            var listing = client.listObjectsV2(request -> request.bucket(credentials.bucket()).maxKeys(1));
            if (!credentials.bucket().equals(listing.name())) {
                return new StorageCheck(FAILED, "The endpoint did not return a listing for this bucket.");
            }
            return new StorageCheck(SUCCEEDED, null);
        } catch (S3Exception failure) {
            if (failure.isThrottlingException() || failure(failure.statusCode()) == UNAVAILABLE) {
                return new StorageCheck(UNAVAILABLE, null);
            }
            String code = failure.awsErrorDetails() == null ? null : failure.awsErrorDetails().errorCode();
            String message = switch (code) {
                case "AccessDenied" -> "Storage denied listing this bucket. Check the access key's permissions for it.";
                case "NoSuchBucket" -> "Storage could not find this bucket at the configured endpoint.";
                case "InvalidAccessKeyId", "Unauthorized" -> "Storage did not accept the access key ID.";
                case "SignatureDoesNotMatch" -> "Storage rejected the request signature. Check the secret key, endpoint and region.";
                case "AuthorizationHeaderMalformed", "IncorrectEndpoint", "PermanentRedirect" ->
                        "Storage rejected the endpoint or signing region.";
                case null, default -> "Storage rejected the bucket check (HTTP " + failure.statusCode()
                        + "). Check the endpoint, bucket, region and credentials.";
            };
            return new StorageCheck(FAILED, message);
        } catch (SdkClientException _) {
            return new StorageCheck(UNAVAILABLE, null);
        } catch (IllegalArgumentException _) {
            return new StorageCheck(FAILED, "Check the storage endpoint, bucket, region and credentials.");
        }
    }

    private static ProviderCheck failure(int status) {
        return status == 408 || status == 429 || status >= 500 ? UNAVAILABLE : FAILED;
    }

    record Inputs(SecretUpdate botToken, OAuth discordOAuth, OAuth twitch, Storage storage) {
    }

    record OAuth(String clientId, SecretUpdate secret) {
    }

    record Storage(String endpoint, String bucket, String region, SecretUpdate accessKey, SecretUpdate secretKey) {
    }

    record Verification(Map<String, ProviderCheck> checks, Map<String, String> errors) {
    }

    private record StorageCheck(ProviderCheck state, String error) {
    }
}
