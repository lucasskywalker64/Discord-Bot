package com.github.lucasskywalker64.dashboard;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "dashboard_session")
class DashboardSession implements Persistable<String> {
    @Id
    String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    DashboardAccount account;
    long loginVersion;
    Instant authenticatedAt;
    Instant expiresAt;
    @Transient
    private boolean newSession = true;

    protected DashboardSession() {
    }

    DashboardSession(String id, DashboardAccount account, Instant authenticatedAt, Instant expiresAt) {
        this.id = id;
        this.account = account;
        this.loginVersion = account.loginVersion;
        this.authenticatedAt = authenticatedAt;
        this.expiresAt = expiresAt;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newSession;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newSession = false;
    }
}
