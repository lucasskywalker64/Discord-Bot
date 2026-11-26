package com.github.lucasskywalker64.dashboard;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(timeout = 5)
public class DashboardRecovery {
    private static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
    private static final int ACCOUNTS_PER_PAGE = 25;

    private final DashboardAccountRepository accounts;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final DiscordDashboardOAuth oauth;

    DashboardRecovery(DashboardAccountRepository accounts, JdbcClient jdbc, Clock clock, DiscordDashboardOAuth oauth) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.clock = clock;
        this.oauth = oauth;
    }

    @Transactional(readOnly = true)
    public AccountPage listAccounts(String query, int page) {
        var rows = accounts.findByDiscordUsernameContainingIgnoreCaseOrDiscordIdContaining(query, query,
                PageRequest.of(page, ACCOUNTS_PER_PAGE, Sort.by("discordId")));
        var now = clock.instant();
        return new AccountPage(rows.map(account -> new AccountSummary(account.id, account.discordId,
                account.discordUsername, account.loginVersion, account.primaryTwitchId, account.managedGuildId,
                account.recoveryLinkExpiresAt != null && now.isBefore(account.recoveryLinkExpiresAt)
                        ? account.recoveryLinkExpiresAt : null)).getContent(), rows.hasNext());
    }

    public IssuedLink issueLink(UUID id, long expectedLoginVersion, String administrator, String reason) {
        var settings = oauth.settings();
        if (settings == null) {
            throw new DiscordDashboardOAuth.UnavailableSettings();
        }
        var account = accounts.lock(id).orElseThrow(AccountMissing::new);
        if (account.loginVersion != expectedLoginVersion) {
            throw new StaleAccount();
        }
        String token = CredentialKind.ISSUED_LINK.newToken();
        var now = clock.instant();
        var expiresAt = now.plus(LINK_LIFETIME);
        if (account.recoveryLinkExpiresAt != null && now.isBefore(account.recoveryLinkExpiresAt)) {
            audit(id, "LINK_REVOKED", administrator, null, account.discordId, null);
        }
        account.recoveryLinkHash = DashboardTokens.hash(token);
        account.recoveryLinkExpiresAt = expiresAt;
        audit(id, "LINK_ISSUED", administrator, reason, account.discordId, null);
        var url = URI.create(settings.callbackUrl()).resolve("/dashboard/#recovery=" + token);
        return new IssuedLink(url, expiresAt);
    }

    public void revokeLink(UUID id, String administrator) {
        var account = accounts.lock(id).orElseThrow(AccountMissing::new);
        if (account.recoveryLinkHash != null) {
            account.clearRecoveryLink();
            audit(id, "LINK_REVOKED", administrator, null, account.discordId, null);
        }
    }

    @Transactional(readOnly = true)
    Optional<Claim> begin(String credential) {
        String token = credential.strip();
        for (var kind : CredentialKind.values()) {
            if (!kind.matches(token)) {
                continue;
            }
            String hash = DashboardTokens.hash(token);
            var account = switch (kind) {
                case SAVED_CODE -> accounts.findByRecoveryCodeHash(hash);
                case ISSUED_LINK -> accounts.findByRecoveryLinkHash(hash);
            };
            return account.filter(candidate -> matches(kind, hash, candidate))
                    .map(candidate -> new Claim(candidate.id, candidate.loginVersion, kind, hash,
                            new DiscordOAuthClient.Identity(candidate.discordId, candidate.discordUsername)));
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    boolean current(Claim claim) {
        return accounts.findById(claim.accountId()).filter(account -> matches(claim, account)).isPresent();
    }

    void complete(Claim claim, DiscordOAuthClient.Identity replacement) {
        var account = accounts.lock(claim.accountId()).filter(candidate -> matches(claim, candidate))
                .orElseThrow(InvalidCredential::new);
        String previousDiscordId = account.discordId;
        account.changeDiscord(replacement);
        accounts.flush();
        audit(account.id, claim.kind() == CredentialKind.SAVED_CODE ? "SAVED_CODE_USED" : "ISSUED_LINK_USED",
                null, null, previousDiscordId, replacement.id());
    }

    private boolean matches(Claim claim, DashboardAccount account) {
        return account.loginVersion == claim.loginVersion() && matches(claim.kind(), claim.hash(), account);
    }

    private boolean matches(CredentialKind kind, String hash, DashboardAccount account) {
        return switch (kind) {
            case SAVED_CODE -> hash.equals(account.recoveryCodeHash);
            case ISSUED_LINK -> hash.equals(account.recoveryLinkHash)
                    && account.recoveryLinkExpiresAt != null && clock.instant().isBefore(account.recoveryLinkExpiresAt);
        };
    }

    private void audit(UUID accountId, String action, @Nullable String actor, @Nullable String reason,
                       String previousDiscordId, @Nullable String newDiscordId) {
        jdbc.sql("""
                INSERT INTO dashboard_recovery_audit
                    (account_id, occurred_at, action, actor, reason, previous_discord_id, new_discord_id)
                VALUES (:accountId, :occurredAt, :action, :actor, :reason, :previousDiscordId, :newDiscordId)
                """)
                .param("accountId", accountId).param("occurredAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("action", action).param("actor", actor).param("reason", reason)
                .param("previousDiscordId", previousDiscordId).param("newDiscordId", newDiscordId).update();
    }

    enum CredentialKind {
        SAVED_CODE("code_"), ISSUED_LINK("link_");

        private final String prefix;

        CredentialKind(String prefix) {
            this.prefix = prefix;
        }

        String newToken() {
            return prefix + DashboardTokens.create();
        }

        private boolean matches(String token) {
            return token.startsWith(prefix) && DashboardTokens.valid(token.substring(prefix.length()));
        }
    }

    record Claim(UUID accountId, long loginVersion, CredentialKind kind, String hash,
                 DiscordOAuthClient.Identity current) {
    }

    public record AccountSummary(UUID id, String discordId, String discordUsername, long loginVersion,
                                 @Nullable String primaryTwitchId,
                                 @Nullable String managedGuildId, @Nullable Instant recoveryLinkExpiresAt) {
    }

    public record AccountPage(List<AccountSummary> accounts, boolean hasMore) {
    }

    public record IssuedLink(URI url, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedLink[expiresAt=" + expiresAt + "]";
        }
    }

    public static final class AccountMissing extends RuntimeException {
    }

    public static final class StaleAccount extends RuntimeException {
    }

    static final class InvalidCredential extends RuntimeException {
    }
}
