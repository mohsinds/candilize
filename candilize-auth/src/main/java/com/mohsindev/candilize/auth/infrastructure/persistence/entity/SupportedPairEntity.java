package com.mohsindev.candilize.auth.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "supported_pairs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportedPairEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(name = "base_asset", nullable = false, length = 10)
    private String baseAsset;

    @Column(name = "quote_asset", nullable = false, length = 10)
    private String quoteAsset;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
