package com.mohsindev.candilize.service;

import com.mohsindev.candilize.api.dto.request.PairRequest;
import com.mohsindev.candilize.api.exception.EntityNotFoundException;
import com.mohsindev.candilize.api.dto.response.IntervalResponse;
import com.mohsindev.candilize.api.dto.response.PairResponse;
import com.mohsindev.candilize.infrastructure.enums.ExchangeName;
import com.mohsindev.candilize.infrastructure.persistence.entity.SupportedIntervalEntity;
import com.mohsindev.candilize.infrastructure.persistence.entity.SupportedPairEntity;
import com.mohsindev.candilize.infrastructure.persistence.repository.SupportedIntervalRepository;
import com.mohsindev.candilize.infrastructure.persistence.repository.SupportedPairRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SupportedPairRepository pairRepository;
    private final SupportedIntervalRepository intervalRepository;

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "supportedPairs", key = "'all'")
    public List<PairResponse> getAllPairs() {
        return pairRepository.findAll().stream()
                .map(this::toPairResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "supportedPairs", allEntries = true)
    public PairResponse addPair(PairRequest request) {
        if (pairRepository.findBySymbol(request.symbol()).isPresent()) {
            throw new IllegalArgumentException("Pair already exists: " + request.symbol());
        }
        SupportedPairEntity entity = SupportedPairEntity.builder()
                .symbol(request.symbol().toUpperCase())
                .baseAsset(request.baseAsset().toUpperCase())
                .quoteAsset(request.quoteAsset().toUpperCase())
                .enabled(true)
                .build();
        entity = pairRepository.save(entity);
        log.info("Added pair: {}", entity.getSymbol());
        return toPairResponse(entity);
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "supportedPairs", allEntries = true)
    public PairResponse updatePair(Long id, Boolean enabled) {
        SupportedPairEntity entity = pairRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pair", id));
        if (enabled != null) {
            entity.setEnabled(enabled);
        }
        entity = pairRepository.save(entity);
        return toPairResponse(entity);
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "supportedPairs", allEntries = true)
    public void deletePair(Long id) {
        if (!pairRepository.existsById(id)) {
            throw new EntityNotFoundException("Pair", id);
        }
        pairRepository.deleteById(id);
        log.info("Deleted pair id: {}", id);
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "supportedIntervals", key = "'all'")
    public List<IntervalResponse> getAllIntervals() {
        return intervalRepository.findAll().stream()
                .map(this::toIntervalResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "supportedIntervals", allEntries = true)
    public IntervalResponse updateInterval(Long id, Boolean enabled) {
        SupportedIntervalEntity entity = intervalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interval", id));
        if (enabled != null) {
            entity.setEnabled(enabled);
        }
        entity = intervalRepository.save(entity);
        return toIntervalResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableExchanges() {
        return List.of(ExchangeName.MEXC.getCode(), ExchangeName.BINANCE.getCode());
    }

    @Transactional(readOnly = true)
    public List<SupportedPairEntity> getEnabledPairs() {
        return pairRepository.findAllByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public List<SupportedIntervalEntity> getEnabledIntervals() {
        return intervalRepository.findAllByEnabledTrue();
    }

    private PairResponse toPairResponse(SupportedPairEntity e) {
        return new PairResponse(e.getId(), e.getSymbol(), e.getBaseAsset(), e.getQuoteAsset(), e.getEnabled());
    }

    private IntervalResponse toIntervalResponse(SupportedIntervalEntity e) {
        return new IntervalResponse(e.getId(), e.getIntervalCode(), e.getDescription(), e.getEnabled());
    }
}
