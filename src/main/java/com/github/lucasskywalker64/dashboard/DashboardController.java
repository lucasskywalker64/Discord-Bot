package com.github.lucasskywalker64.dashboard;

import com.github.lucasskywalker64.dashboard.DashboardAccounts.Principal;
import com.github.lucasskywalker64.dashboard.DashboardWeb.Outcome;
import com.github.lucasskywalker64.dashboard.DiscordDashboardOAuth.Attempt;
import com.github.lucasskywalker64.dashboard.DiscordDashboardOAuth.Purpose;
import com.github.lucasskywalker64.installation.OAuthCallbackUrls;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import static com.github.lucasskywalker64.dashboard.DashboardWeb.dashboard;
import static com.github.lucasskywalker64.dashboard.DashboardWeb.redirect;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class DashboardController {
    private final DiscordDashboardOAuth oauth;
    private final DiscordOAuthClient discord;
    private final DashboardAccounts accounts;
    private final DashboardRecovery recovery;
    private final CookieCsrfTokenRepository csrfTokens;

    DashboardController(DiscordDashboardOAuth oauth, DiscordOAuthClient discord, DashboardAccounts accounts,
                        DashboardRecovery recovery, CookieCsrfTokenRepository csrfTokens) {
        this.oauth = oauth;
        this.discord = discord;
        this.accounts = accounts;
        this.recovery = recovery;
        this.csrfTokens = csrfTokens;
    }

    @GetMapping("/dashboard/api/session")
    SessionView session(@AuthenticationPrincipal @Nullable Principal principal, CsrfToken csrf) {
        return new SessionView(principal != null, oauth.settings() != null,
                principal != null && accounts.recent(principal), csrf.getToken());
    }

    @GetMapping("/dashboard/api/account")
    AccountView account(@AuthenticationPrincipal Principal principal) {
        return new AccountView(principal.accountId(), principal.discord().id(), principal.discord().username(),
                principal.hasRecoveryCode());
    }

    @PostMapping("/dashboard/api/recovery-code")
    RecoveryCode generateRecoveryCode(@AuthenticationPrincipal Principal principal) {
        return new RecoveryCode(accounts.generateRecoveryCode(principal));
    }

    @PostMapping("/dashboard/api/sign-in")
    ResponseEntity<Void> signIn(@AuthenticationPrincipal @Nullable Principal principal,
                                HttpServletRequest request, HttpServletResponse response) {
        return principal == null ? begin(Purpose.SIGN_IN, null, request, response) : dashboard();
    }

    @PostMapping("/dashboard/api/reauthenticate")
    ResponseEntity<Void> reauthenticate(@AuthenticationPrincipal Principal principal,
                                        HttpServletRequest request, HttpServletResponse response) {
        return begin(Purpose.REAUTHENTICATE, principal, request, response);
    }

    @PostMapping("/dashboard/api/replace-discord")
    ResponseEntity<Void> replaceDiscord(@AuthenticationPrincipal Principal principal,
                                        HttpServletRequest request, HttpServletResponse response) {
        if (!accounts.recent(principal)) {
            return dashboard(Outcome.REAUTHENTICATE);
        }
        return begin(Purpose.REPLACE, principal, request, response);
    }

    @GetMapping(path = OAuthCallbackUrls.DISCORD_PATH, params = "test")
    ResponseEntity<?> launchTest(@RequestParam String test, HttpServletResponse response) {
        String browser = DashboardTokens.create();
        var launch = oauth.launchTest(test, browser);
        if (launch == null) {
            return expiredTest();
        }
        DashboardWeb.setCookie(response, DashboardWeb.TEST_COOKIE, browser, DiscordDashboardOAuth.ATTEMPT_LIFETIME,
                secureCallback(launch.callbackUrl()));
        return redirect(launch.authorization());
    }

    @GetMapping(path = OAuthCallbackUrls.DISCORD_PATH, params = "!test")
    ResponseEntity<?> callback(@RequestParam(required = false) @Nullable String state,
                               @RequestParam(required = false) @Nullable String code,
                               @RequestParam(required = false) @Nullable String error,
                               @RequestParam(required = false) @Nullable String iss,
                               @AuthenticationPrincipal @Nullable Principal principal, HttpServletRequest request,
                               HttpServletResponse response) {
        boolean validIssuer = iss == null || "https://discord.com".equals(iss);
        String authorizationCode = validIssuer && error == null && code != null && !code.isBlank() ? code : null;
        if (state != null && state.startsWith(DiscordDashboardOAuth.TEST_STATE_PREFIX)) {
            return testCallback(state, authorizationCode, request, response);
        }
        var attempt = oauth.take(state, DashboardWeb.cookie(request, DashboardWeb.OAUTH_COOKIE));
        if (attempt == null || !Objects.equals(attempt.intent().sessionId(), sessionId(principal))) {
            return dashboard(Outcome.EXPIRED);
        }
        return accountCallback(attempt, principal, authorizationCode, validIssuer && code == null ? error : null,
                request, response);
    }

    @GetMapping("/dashboard/api/replacement")
    ResponseEntity<ReplacementView> replacement(@AuthenticationPrincipal Principal principal) {
        var proof = oauth.proof(principal.sessionId());
        if (proof == null || oauth.settings(proof.revision()) == null) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        return ResponseEntity.ok(new ReplacementView(proof.confirmation(), proof.current(), proof.proposed()));
    }

    @PostMapping("/dashboard/api/confirm-replacement")
    ResponseEntity<Void> confirmReplacement(@AuthenticationPrincipal Principal principal,
                                            @RequestParam String confirmation, HttpServletRequest request,
                                            HttpServletResponse response) {
        var proof = oauth.confirm(principal.sessionId(), confirmation);
        var settings = proof == null ? null : oauth.settings(proof.revision());
        if (settings == null) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        try {
            setSessionCookie(request, response, accounts.replaceDiscord(principal, proof.proposed()), settings.callbackUrl());
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException _) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/dashboard/api/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Principal principal, HttpServletRequest request,
                                HttpServletResponse response) {
        accounts.logout(principal);
        DashboardWeb.setCookie(response, DashboardWeb.SESSION_COOKIE, "", Duration.ZERO, request.isSecure());
        csrfTokens.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/dashboard/api/discard-replacement")
    ResponseEntity<Void> discardReplacement(@AuthenticationPrincipal Principal principal,
                                            @RequestParam String confirmation) {
        oauth.confirm(principal.sessionId(), confirmation);
        return dashboard();
    }

    @ExceptionHandler(DashboardAccounts.StaleSession.class)
    ResponseEntity<Void> staleSession() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(DashboardAccounts.ReauthenticationRequired.class)
    ResponseEntity<Void> reauthenticationRequired() {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).build();
    }

    @ExceptionHandler(DiscordDashboardOAuth.AttemptsFull.class)
    ResponseEntity<Void> attemptsFull() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private ResponseEntity<Void> begin(Purpose purpose, @Nullable Principal principal, HttpServletRequest request,
                                       HttpServletResponse response) {
        var settings = oauth.settings();
        if (settings == null) {
            return dashboard(Outcome.UNAVAILABLE);
        }
        var browser = DashboardWeb.oauthBrowser(request, response, settings.callbackUrl());
        return redirect(oauth.begin(settings, browser, purpose, sessionId(principal)));
    }

    private ResponseEntity<Void> accountCallback(Attempt attempt, @Nullable Principal principal, @Nullable String code,
                                                 @Nullable String error, HttpServletRequest request,
                                                 HttpServletResponse response) {
        var settings = oauth.settings(attempt);
        if (settings == null) {
            return dashboard(Outcome.EXPIRED);
        }
        if (code == null) {
            if (attempt.intent().purpose() == Purpose.SIGN_IN && requiresInteraction(error)) {
                var browser = DashboardWeb.oauthBrowser(request, response, settings.callbackUrl());
                return redirect(oauth.begin(settings, browser, Purpose.SIGN_IN_WITH_CONSENT, null));
            }
            return dashboard(Outcome.REJECTED);
        }
        DiscordOAuthClient.Identity identity;
        try {
            identity = discord.identify(settings, code, attempt.verifier());
        } catch (RestClientException _) {
            return dashboard(Outcome.REJECTED);
        }
        if (oauth.settings(attempt) == null) {
            return dashboard(Outcome.EXPIRED);
        }
        var claim = attempt.intent().recovery();
        if (claim != null) {
            if (!recovery.current(claim)) {
                return dashboard(Outcome.RECOVERY_INVALID);
            }
            DashboardWeb.oauthBrowser(request, response, settings.callbackUrl());
            oauth.stage(attempt, claim.current(), identity);
            return dashboard(Outcome.RECOVERY_CONFIRM);
        }
        if (attempt.intent().purpose() == Purpose.SIGN_IN || attempt.intent().purpose() == Purpose.SIGN_IN_WITH_CONSENT) {
            setSessionCookie(request, response, accounts.signIn(identity), settings.callbackUrl());
            return dashboard();
        }
        if (principal == null) {
            return dashboard(Outcome.EXPIRED);
        }
        return completeManagementAuthorization(attempt, principal, identity, settings.callbackUrl(), request, response);
    }

    private ResponseEntity<Void> completeManagementAuthorization(Attempt attempt, Principal principal,
                                                                 DiscordOAuthClient.Identity identity, String callback,
                                                                 HttpServletRequest request, HttpServletResponse response) {
        return switch (attempt.intent().purpose()) {
            case REAUTHENTICATE -> {
                var token = accounts.reauthenticate(principal, identity);
                if (token.isEmpty()) {
                    yield dashboard(Outcome.REJECTED);
                }
                setSessionCookie(request, response, token.orElseThrow(), callback);
                yield dashboard();
            }
            case REPLACE -> {
                if (!accounts.recent(principal)) {
                    yield dashboard(Outcome.REAUTHENTICATE);
                }
                oauth.stage(attempt, principal.discord(), identity);
                yield dashboard(Outcome.CONFIRM);
            }
            default -> throw new IllegalStateException("Unexpected Discord management authorization");
        };
    }

    private ResponseEntity<String> testCallback(String state, @Nullable String code, HttpServletRequest request,
                                                 HttpServletResponse response) {
        var callback = oauth.finishTest(state, DashboardWeb.cookie(request, DashboardWeb.TEST_COOKIE), code);
        if (callback == null) {
            return expiredTest();
        }
        DashboardWeb.setCookie(response, DashboardWeb.TEST_COOKIE, "", Duration.ZERO, secureCallback(callback));
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                .body("Return to the private console to see the Discord authorization test result.");
    }

    private static ResponseEntity<String> expiredTest() {
        return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.TEXT_PLAIN)
                .body("This Discord authorization test expired. Start a new test from the private console.");
    }

    private static boolean secureCallback(String callback) {
        return "https".equals(URI.create(callback).getScheme());
    }

    private static boolean requiresInteraction(@Nullable String error) {
        return switch (error) {
            case "access_denied", "interaction_required", "login_required", "consent_required",
                 "account_selection_required" -> true;
            case null, default -> false;
        };
    }

    private void setSessionCookie(HttpServletRequest request, HttpServletResponse response, String token, String callback) {
        DashboardWeb.setCookie(response, DashboardWeb.SESSION_COOKIE, token, DashboardAccounts.SESSION_LIFETIME,
                secureCallback(callback));
        csrfTokens.saveToken(null, request, response);
    }

    private static @Nullable String sessionId(@Nullable Principal principal) {
        return principal == null ? null : principal.sessionId();
    }

    record SessionView(boolean authenticated, boolean discordAvailable, boolean recent, String csrfToken) {
    }

    record AccountView(UUID id, String discordId, String discordUsername, boolean hasRecoveryCode) {
    }

    record RecoveryCode(String code) {
        @Override
        public String toString() {
            return "RecoveryCode[redacted]";
        }
    }

    record ReplacementView(String confirmation, DiscordOAuthClient.Identity current,
                           DiscordOAuthClient.Identity proposed) {
    }

}
