package com.github.lucasskywalker64.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dashboard_account")
@DynamicUpdate
class DashboardAccount {
    @Id
    UUID id;
    String discordId;
    String discordUsername;
    @Column(name = "discord_login_version")
    long loginVersion;
    String primaryTwitchId;
    String managedGuildId;
    String recoveryCodeHash;
    String recoveryLinkHash;
    Instant recoveryLinkExpiresAt;

    protected DashboardAccount() {
    }

    void changeDiscord(DiscordOAuthClient.Identity identity) {
        discordId = identity.id();
        discordUsername = identity.username();
        loginVersion++;
        recoveryCodeHash = null;
        clearRecoveryLink();
    }

    void clearRecoveryLink() {
        recoveryLinkHash = null;
        recoveryLinkExpiresAt = null;
    }
}
