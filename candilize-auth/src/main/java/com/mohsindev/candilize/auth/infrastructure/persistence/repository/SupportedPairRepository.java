package com.mohsindev.candilize.auth.infrastructure.persistence.repository;

import com.mohsindev.candilize.auth.infrastructure.persistence.entity.SupportedPairEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportedPairRepository extends JpaRepository<SupportedPairEntity, Long> {

    List<SupportedPairEntity> findAllByEnabledTrue();

    Optional<SupportedPairEntity> findBySymbol(String symbol);
}
