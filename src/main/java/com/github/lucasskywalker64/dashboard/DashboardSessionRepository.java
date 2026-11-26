package com.github.lucasskywalker64.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

interface DashboardSessionRepository extends JpaRepository<DashboardSession, String> {
    @Query("""
            select s from DashboardSession s join fetch s.account a
            where s.id = :id and s.expiresAt > :now and s.loginVersion = a.loginVersion
            """)
    Optional<DashboardSession> findCurrent(String id, Instant now);

    @Modifying
    @Query("delete from DashboardSession s where s.expiresAt <= :now")
    void deleteExpired(Instant now);

    @Modifying
    @Query("delete from DashboardSession s where s.id = :id")
    void revoke(String id);
}
