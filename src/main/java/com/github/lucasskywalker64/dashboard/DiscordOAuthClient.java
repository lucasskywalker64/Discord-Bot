package com.github.lucasskywalker64.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.lucasskywalker64.installation.InstallationConfigurationService.DiscordOAuthSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.regex.Pattern;

@Component
class DiscordOAuthClient {
    private static final Pattern SNOWFLAKE = Pattern.compile("[1-9][0-9]{0,19}");
    private static final int MAX_USERNAME_LENGTH = 64;
    private final RestClient api;

    DiscordOAuthClient(@Value("${dashboard.discord.api-base:https://discord.com/api/v10}") String apiBase) {
        var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        transport.setReadTimeout(Duration.ofSeconds(5));
        api = RestClient.builder().baseUrl(apiBase).requestFactory(transport).build();
    }

    URI authorization(DiscordOAuthSettings settings, String state, String verifier, boolean promptForConsent) {
        return UriComponentsBuilder.fromUriString("https://discord.com/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("scope", "identify")
                .queryParam("prompt", promptForConsent ? "consent" : "none")
                .queryParam("client_id", "{client}")
                .queryParam("redirect_uri", "{callback}")
                .queryParam("state", "{state}")
                .queryParam("code_challenge_method", "S256")
                .queryParam("code_challenge", DashboardTokens.challenge(verifier))
                .encode().buildAndExpand(settings.clientId(), settings.callbackUrl(), state).toUri();
    }

    String exchange(DiscordOAuthSettings settings, String code, String verifier) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", settings.clientId());
        form.add("client_secret", settings.clientSecret());
        form.add("redirect_uri", settings.callbackUrl());
        form.add("code", code);
        form.add("code_verifier", verifier);
        var token = api.post().uri("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Token.class);
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()
                || !"Bearer".equalsIgnoreCase(token.tokenType())) {
            throw new RestClientException("Discord did not return a bearer token");
        }
        return token.accessToken();
    }

    Identity identify(DiscordOAuthSettings settings, String code, String verifier) {
        String token = exchange(settings, code, verifier);
        var identity = api.get().uri("/users/@me").headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(Identity.class);
        if (identity == null || !validId(identity.id()) || identity.username() == null
                || identity.username().isBlank() || identity.username().length() > MAX_USERNAME_LENGTH) {
            throw new RestClientException("Discord did not return a valid identity");
        }
        return identity;
    }

    private static boolean validId(String id) {
        if (id == null || !SNOWFLAKE.matcher(id).matches()) {
            return false;
        }
        try {
            Long.parseUnsignedLong(id);
            return true;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    record Identity(String id, String username) {
    }

    private record Token(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("token_type") String tokenType) {
    }
}
