package com.github.lucasskywalker64.installation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface InstallationConfigurationRepository extends JpaRepository<InstallationConfigurationEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select configuration from InstallationConfigurationEntity configuration where configuration.id = 1")
    InstallationConfigurationEntity lock();
}
