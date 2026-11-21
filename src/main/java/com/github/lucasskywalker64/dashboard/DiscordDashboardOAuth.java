package com.github.lucasskywalker64.dashboard;

import com.github.lucasskywalker64.installation.InstallationConfigurationService;
import com.github.lucasskywalker64.installation.InstallationConfigurationService.DiscordOAuthSettings;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

@Service
public class DiscordDashboardOAuth {
    public static final String CALLBACK = "/dashboard/oauth/discord/callback";
    static final String TEST_STATE_PREFIX = "admin.";
    static final Duration ATTEMPT_LIFETIME = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 10_000;

    private final InstallationConfigurationService installation;
    private final DiscordOAuthClient discord;
    private final Clock clock;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private final Map<String, Proof> proofs = new HashMap<>();
    private final Map<String, AdminTest> launches = new HashMap<>();

    DiscordDashboardOAuth(InstallationConfigurationService installation, DiscordOAuthClient discord, Clock clock) {
        this.installation = installation;
        this.discord = discord;
        this.clock = clock;
    }

    @Nullable DiscordOAuthSettings settings() {
        var settings = installation.discordOAuthSettings();
        if (!settings.enabled() || settings.callbackUrl() == null) {
            return null;
        }
        var callback = URI.create(settings.callbackUrl());
        boolean allowedTransport = "https".equals(callback.getScheme())
                || "localhost".equalsIgnoreCase(callback.getHost()) || "127.0.0.1".equals(callback.getHost())
                || "[::1]".equals(callback.getHost());
        return allowedTransport && CALLBACK.equals(callback.getRawPath()) && callback.getRawQuery() == null
                ? settings : null;
    }

    URI begin(DiscordOAuthSettings settings, String browser, Purpose purpose, @Nullable String sessionId) {
        return registerAttempt(settings, browser, purpose, sessionId, null, clock.instant().plus(ATTEMPT_LIFETIME));
    }

    synchronized @Nullable Attempt take(@Nullable String state, @Nullable String browser) {
        var attempt = attempts.get(state);
        if (attempt == null || !Objects.equals(attempt.browser(), browser)) {
            return null;
        }
        attempts.remove(state);
        return clock.instant().isBefore(attempt.expiresAt()) ? attempt : null;
    }

    @Nullable DiscordOAuthSettings settings(long revision) {
        var current = settings();
        return current != null && current.revision() == revision ? current : null;
    }

    @Nullable DiscordOAuthSettings settings(Attempt attempt) {
        return clock.instant().isBefore(attempt.expiresAt()) ? settings(attempt.revision()) : null;
    }

    public AdminTest startTest(Predicate<AdminTest> sessionIsCurrent) {
        var settings = settings();
        if (settings == null) {
            throw new UnavailableSettings();
        }
        var now = clock.instant();
        String launch = DashboardTokens.create();
        URI launchUrl = URI.create(settings.callbackUrl() + "?test=" + launch);
        var test = new AdminTest(launchUrl, settings.revision(), now.plus(ATTEMPT_LIFETIME), sessionIsCurrent);
        synchronized (this) {
            launches.values().removeIf(pending -> !now.isBefore(pending.expiresAt));
            if (launches.size() >= MAX_ATTEMPTS) {
                throw new AttemptsFull();
            }
            launches.put(launch, test);
        }
        return test;
    }

    @Nullable TestLaunch launchTest(String launch, String browser) {
        AdminTest test;
        synchronized (this) {
            test = launches.remove(launch);
        }
        if (test == null || !test.active(clock.instant())) {
            return null;
        }
        var settings = settings(test.revision);
        if (settings == null) {
            return null;
        }
        return new TestLaunch(registerAttempt(settings, browser, Purpose.ADMIN_TEST, null, test, test.expiresAt),
                settings.callbackUrl());
    }

    public TestStatus testStatus(@Nullable AdminTest test) {
        if (test == null) {
            return TestStatus.IDLE;
        }
        if (settings(test.revision) == null) {
            return TestStatus.EXPIRED;
        }
        return test.result == TestStatus.PENDING && !clock.instant().isBefore(test.expiresAt)
                ? TestStatus.EXPIRED : test.result;
    }

    @Nullable String finishTest(String state, @Nullable String browser, @Nullable String code) {
        var attempt = take(state, browser);
        var test = attempt == null ? null : attempt.test();
        if (test == null || !test.active(clock.instant())) {
            return null;
        }
        var settings = settings(test.revision);
        if (settings == null) {
            return null;
        }
        var result = TestStatus.REJECTED;
        if (code != null) {
            try {
                discord.exchange(settings, code, attempt.verifier());
                result = TestStatus.SUCCEEDED;
            } catch (RestClientException _) {
                // Record only the test outcome.
            }
        }
        if (!test.active(clock.instant()) || settings(test.revision) == null) {
            return null;
        }
        test.result = result;
        return settings.callbackUrl();
    }

    synchronized void stage(Attempt attempt, DiscordOAuthClient.Identity current, DiscordOAuthClient.Identity proposed) {
        var now = clock.instant();
        proofs.values().removeIf(proof -> !now.isBefore(proof.expiresAt()));
        if (proofs.size() >= MAX_ATTEMPTS && !proofs.containsKey(attempt.sessionId())) {
            throw new AttemptsFull();
        }
        proofs.put(attempt.sessionId(), new Proof(DashboardTokens.create(), attempt.revision(), current,
                proposed, now.plus(ATTEMPT_LIFETIME)));
    }

    synchronized @Nullable Proof proof(String sessionId) {
        var proof = proofs.get(sessionId);
        if (proof != null && !clock.instant().isBefore(proof.expiresAt())) {
            proofs.remove(sessionId);
            return null;
        }
        return proof;
    }

    synchronized @Nullable Proof confirm(String sessionId, String confirmation) {
        var proof = proof(sessionId);
        if (proof == null || !proof.confirmation().equals(confirmation)) {
            return null;
        }
        proofs.remove(sessionId);
        return proof;
    }

    private synchronized URI registerAttempt(DiscordOAuthSettings settings, String browser, Purpose purpose,
                                             @Nullable String sessionId, @Nullable AdminTest test, Instant expiresAt) {
        var now = clock.instant();
        attempts.values().removeIf(attempt -> !now.isBefore(attempt.expiresAt()));
        if (attempts.size() >= MAX_ATTEMPTS) {
            throw new AttemptsFull();
        }
        var state = (purpose == Purpose.ADMIN_TEST ? TEST_STATE_PREFIX : "") + DashboardTokens.create();
        var verifier = DashboardTokens.create();
        attempts.put(state, new Attempt(browser, settings.revision(), purpose, sessionId, verifier, expiresAt, test));
        return discord.authorization(settings, state, verifier);
    }

    enum Purpose { SIGN_IN, REAUTHENTICATE, REPLACE, ADMIN_TEST }

    record Attempt(String browser, long revision, Purpose purpose, @Nullable String sessionId, String verifier,
                   Instant expiresAt, @Nullable AdminTest test) {
    }

    record Proof(String confirmation, long revision, DiscordOAuthClient.Identity current,
                 DiscordOAuthClient.Identity proposed, Instant expiresAt) {
    }

    record TestLaunch(URI authorization, String callbackUrl) {
    }

    public enum TestStatus { IDLE, PENDING, SUCCEEDED, REJECTED, EXPIRED }

    public static final class AttemptsFull extends RuntimeException {
    }

    public static final class UnavailableSettings extends RuntimeException {
    }

    public static final class AdminTest {
        private final URI launchUrl;
        private final long revision;
        private final Instant expiresAt;
        private final Predicate<AdminTest> sessionIsCurrent;
        private volatile TestStatus result = TestStatus.PENDING;

        private AdminTest(URI launchUrl, long revision, Instant expiresAt, Predicate<AdminTest> sessionIsCurrent) {
            this.launchUrl = launchUrl;
            this.revision = revision;
            this.expiresAt = expiresAt;
            this.sessionIsCurrent = sessionIsCurrent;
        }

        public URI launchUrl() {
            return launchUrl;
        }

        private boolean active(Instant now) {
            return result == TestStatus.PENDING && now.isBefore(expiresAt) && sessionIsCurrent.test(this);
        }
    }
}
