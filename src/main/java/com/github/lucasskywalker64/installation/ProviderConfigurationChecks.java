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
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck.FAILED;
import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck.SUCCEEDED;
import static com.github.lucasskywalker64.installation.InstallationConfiguration.ProviderCheck.UNAVAILABLE;

@Component
class ProviderConfigurationChecks {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String STORAGE_TEST_CONTENT = "Discord Bot storage validation\n";
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
                errors.putIfAbsent(field, "The provider could not verify these settings right now. Try again.");
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
                .region(Region.of(credentials.region()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(credentials.accessKey().value(), credentials.secretKey().value())))
                .overrideConfiguration(config -> config.apiCallTimeout(TIMEOUT)
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).build()) {
            return storageRoundTrip(client, credentials.bucket());
        } catch (SdkException failure) {
            return storageFailure("setup", failure);
        } catch (IllegalArgumentException _) {
            return new StorageCheck(FAILED, "Check the storage endpoint, bucket, region and credentials.");
        }
    }

    private static StorageCheck storageRoundTrip(S3Client client, String bucket) {
        String key = "discord-bot/validation/" + UUID.randomUUID() + ".txt";
        String version;
        try {
            version = client.putObject(request -> request.bucket(bucket).key(key).contentType("text/plain"),
                    RequestBody.fromString(STORAGE_TEST_CONTENT)).versionId();
        } catch (SdkException failure) {
            var upload = storageFailure("upload", failure);
            // A timed-out request may have reached the provider.
            return upload.state() == UNAVAILABLE ? deleteStorageProbe(client, bucket, key, null, upload) : upload;
        }

        StorageCheck download;
        try {
            var downloaded = requestProbeVersion(version, versionId ->
                    client.getObjectAsBytes(request -> request.bucket(bucket).key(key).versionId(versionId)));
            download = STORAGE_TEST_CONTENT.equals(downloaded.asUtf8String()) ? new StorageCheck(SUCCEEDED, null)
                    : new StorageCheck(FAILED, "Storage test download returned different contents from the upload.");
        } catch (SdkException failure) {
            download = storageFailure("download", failure);
        }
        return deleteStorageProbe(client, bucket, key, version, download);
    }

    private static StorageCheck deleteStorageProbe(S3Client client, String bucket, String key, String version,
                                                   StorageCheck result) {
        StorageCheck cleanup;
        try {
            boolean removed = requestProbeVersion(version, versionId -> {
                var deleted = client.deleteObject(request -> request.bucket(bucket).key(key).versionId(versionId));
                return versionId != null || !Boolean.TRUE.equals(deleted.deleteMarker());
            });
            if (removed) {
                return result;
            }
            cleanup = new StorageCheck(UNAVAILABLE, "Storage did not confirm removal of the test object version.");
        } catch (SdkException failure) {
            cleanup = storageFailure("cleanup", failure);
        }
        String details = cleanup.error() + " Test object: s3://" + bucket + "/" + key;
        return result.state() == SUCCEEDED ? new StorageCheck(cleanup.state(), details)
                : new StorageCheck(result.state(), result.error() + " " + details);
    }

    private static <T> T requestProbeVersion(String version, Function<String, T> request) {
        try {
            return request.apply(version);
        } catch (S3Exception response) {
            // Some S3-compatible providers return a version ID on upload but reject it on read or delete.
            if (version != null && response.statusCode() == 501 && response.awsErrorDetails() != null
                    && "NotImplemented".equals(response.awsErrorDetails().errorCode())) {
                return request.apply(null);
            }
            throw response;
        }
    }

    private static StorageCheck storageFailure(String step, SdkException failure) {
        if (failure instanceof ApiCallTimeoutException) {
            return new StorageCheck(UNAVAILABLE, "Storage test " + step + " timed out. Try again.");
        }
        if (!(failure instanceof S3Exception response)) {
            return new StorageCheck(UNAVAILABLE, "Storage test " + step + " could not finish. Try again.");
        }
        if (response.isThrottlingException() || failure(response.statusCode()) == UNAVAILABLE) {
            return new StorageCheck(UNAVAILABLE, "Storage test " + step + " could not finish (HTTP "
                    + response.statusCode() + "). Try again.");
        }
        String code = response.awsErrorDetails() == null ? null : response.awsErrorDetails().errorCode();
        String reason = switch (code) {
            case "AccessDenied" -> "access denied for this operation.";
            case "NoSuchBucket" -> "the bucket was not found at this endpoint.";
            case "InvalidAccessKeyId", "Unauthorized" -> "the access key was not accepted.";
            case "SignatureDoesNotMatch" -> "the request signature was rejected. Check the secret key, endpoint and region.";
            case "AuthorizationHeaderMalformed", "IncorrectEndpoint", "PermanentRedirect" ->
                    "the endpoint or signing region was rejected.";
            case null, default -> "the provider returned HTTP " + response.statusCode() + ".";
        };
        return new StorageCheck(FAILED, "Storage test " + step + " failed: " + reason);
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
