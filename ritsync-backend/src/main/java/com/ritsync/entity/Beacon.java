package com.ritsync.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "beacons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Beacon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "uuid", length = 36, unique = true, nullable = false)
    private String uuid;

    @Column(name = "token", length = 512, nullable = false)
    private String token;

    @Column(name = "token_expiry", nullable = false)
    private LocalDateTime tokenExpiry;

    @Column(name = "rssi_threshold", nullable = false)
    @Builder.Default
    private Integer rssiThreshold = -75;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
