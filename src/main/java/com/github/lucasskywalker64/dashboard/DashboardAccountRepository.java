package com.github.lucasskywalker64.dashboard;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface DashboardAccountRepository extends JpaRepository<DashboardAccount, UUID> {
    @NativeQuery("""
            INSERT INTO dashboard_account (id, discord_id, discord_username)
            VALUES (:id, :discordId, :username)
            ON CONFLICT (discord_id) DO UPDATE SET discord_username = EXCLUDED.discord_username
            RETURNING *
            """)
    DashboardAccount signIn(UUID id, String discordId, String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DashboardAccount a where a.id = :id")
    Optional<DashboardAccount> lock(UUID id);

    Optional<DashboardAccount> findByRecoveryCodeHash(String hash);

    Optional<DashboardAccount> findByRecoveryLinkHash(String hash);

    Slice<DashboardAccount> findByDiscordUsernameContainingIgnoreCaseOrDiscordIdContaining(
            String username, String discordId, Pageable pageable);
}
