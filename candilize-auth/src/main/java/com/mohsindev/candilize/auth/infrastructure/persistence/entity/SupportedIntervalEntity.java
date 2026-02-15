package com.mohsindev.candilize.auth.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "supported_intervals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportedIntervalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interval_code", nullable = false, unique = true, length = 5)
    private String intervalCode;

    @Column(length = 50)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
