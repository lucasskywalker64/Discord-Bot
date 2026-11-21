package com.github.lucasskywalker64.installation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import static com.github.lucasskywalker64.installation.InstallationConfiguration.SetupStep;
import static com.github.lucasskywalker64.installation.InstallationConfiguration.State;

// Internal persistence model. Browser reads use the secret-free Snapshot only.
@Entity
@Table(name = "installation_configuration")
class InstallationConfigurationEntity {

    @Id
    int id;
    boolean discordBotEnabled;
    @Column(columnDefinition = "text")
    String discordBotToken;
    boolean discordOauthEnabled;
    @Column(columnDefinition = "text")
    String discordClientId;
    @Column(columnDefinition = "text")
    String discordClientSecret;
    @Column(columnDefinition = "text")
    String discordCallbackUrl;
    long discordOauthRevision;
    long discordBotRevision;
    @Enumerated(EnumType.STRING)
    State discordState;
    @Column(columnDefinition = "text")
    String discordFailure;
    boolean twitchEnabled;
    @Column(columnDefinition = "text")
    String twitchClientId;
    @Column(columnDefinition = "text")
    String twitchClientSecret;
    @Column(columnDefinition = "text")
    String twitchCallbackUrl;
    long twitchOauthRevision;
    boolean storageEnabled;
    @Column(columnDefinition = "text")
    String storageEndpoint;
    @Column(columnDefinition = "text")
    String storageBucket;
    @Column(columnDefinition = "text")
    String storageRegion;
    @Column(columnDefinition = "text")
    String storageAccessKey;
    @Column(columnDefinition = "text")
    String storageSecretKey;
    Instant storageVerifiedAt;
    @Column(columnDefinition = "text")
    String publicUrl;
    @Enumerated(EnumType.STRING)
    SetupStep setupStep;

    protected InstallationConfigurationEntity() {
    }
}
