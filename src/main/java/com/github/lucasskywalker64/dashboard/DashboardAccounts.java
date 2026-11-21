package com.github.lucasskywalker64.dashboard;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(timeout = 5)
class DashboardAccounts {
    static final Duration SESSION_LIFETIME = Duration.ofHours(12);
    static final String AUTHORITY = "DASHBOARD_ACCOUNT";
    private static final Duration RECENT_AUTHENTICATION = Duration.ofMinutes(10);

    private final DashboardAccountRepository accounts;
    private final DashboardSessionRepository sessions;
    private final Clock clock;

    DashboardAccounts(DashboardAccountRepository accounts, DashboardSessionRepository sessions, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
    }

    String signIn(DiscordOAuthClient.Identity identity) {
        var now = clock.instant();
        var account = accounts.signIn(UUID.randomUUID(), identity.id(), identity.username());
        sessions.deleteExpired(now);
        return createSession(account, null, now);
    }

    Optional<String> reauthenticate(Principal principal, DiscordOAuthClient.Identity identity) {
        if (!principal.discord().id().equals(identity.id())) {
            return Optional.empty();
        }
        var now = clock.instant();
        var account = lockCurrentAccount(principal, now);
        account.discordUsername = identity.username();
        return Optional.of(createSession(account, principal, now));
    }

    String replaceDiscord(Principal principal, DiscordOAuthClient.Identity identity) {
        var now = clock.instant();
        if (!recent(principal)) {
            throw new ReauthenticationRequired();
        }
        var account = lockCurrentAccount(principal, now);
        if (!account.discordId.equals(identity.id())) {
            account.changeDiscord(identity);
        } else {
            account.discordUsername = identity.username();
        }
        accounts.flush();
        return createSession(account, principal, now);
    }

    String generateRecoveryCode(Principal principal) {
        if (!recent(principal)) {
            throw new ReauthenticationRequired();
        }
        var account = lockCurrentAccount(principal, clock.instant());
        String code = DashboardRecovery.CredentialKind.SAVED_CODE.newToken();
        account.recoveryCodeHash = DashboardTokens.hash(code);
        return code;
    }

    boolean recent(Principal principal) {
        return clock.instant().isBefore(principal.authenticatedAt().plus(RECENT_AUTHENTICATION));
    }

    @Transactional(readOnly = true)
    Optional<Principal> authenticate(@Nullable String token) {
        if (token == null || !DashboardTokens.valid(token)) {
            return Optional.empty();
        }
        return sessions.findCurrent(DashboardTokens.hash(token), clock.instant()).map(session ->
                new Principal(session.id, session.account.id, session.loginVersion, session.authenticatedAt,
                        new DiscordOAuthClient.Identity(session.account.discordId, session.account.discordUsername),
                        session.account.recoveryCodeHash != null));
    }

    void logout(Principal principal) {
        sessions.revoke(principal.sessionId());
    }

    private DashboardAccount lockCurrentAccount(Principal principal, Instant now) {
        var account = accounts.lock(principal.accountId()).orElseThrow(StaleSession::new);
        if (account.loginVersion != principal.loginVersion()
                || sessions.findCurrent(principal.sessionId(), now).isEmpty()) {
            throw new StaleSession();
        }
        return account;
    }

    private String createSession(DashboardAccount account, @Nullable Principal previous, Instant now) {
        if (previous != null) {
            sessions.revoke(previous.sessionId());
        }
        String token = DashboardTokens.create();
        sessions.save(new DashboardSession(DashboardTokens.hash(token), account, now, now.plus(SESSION_LIFETIME)));
        return token;
    }

    record Principal(String sessionId, UUID accountId, long loginVersion, Instant authenticatedAt,
                     DiscordOAuthClient.Identity discord, boolean hasRecoveryCode) {
    }

    static final class StaleSession extends RuntimeException {
    }

    static final class ReauthenticationRequired extends RuntimeException {
    }
}
