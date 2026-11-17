package com.github.lucasskywalker64.operator;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface InstallationConfigurationRepository extends JpaRepository<InstallationConfiguration, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select configuration from InstallationConfiguration configuration where configuration.id = 1")
    InstallationConfiguration lock();
}
