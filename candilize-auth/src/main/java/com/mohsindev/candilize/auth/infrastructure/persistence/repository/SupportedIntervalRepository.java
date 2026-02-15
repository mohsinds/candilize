package com.mohsindev.candilize.auth.infrastructure.persistence.repository;

import com.mohsindev.candilize.auth.infrastructure.persistence.entity.SupportedIntervalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportedIntervalRepository extends JpaRepository<SupportedIntervalEntity, Long> {

    List<SupportedIntervalEntity> findAllByEnabledTrue();

    Optional<SupportedIntervalEntity> findByIntervalCode(String intervalCode);
}
