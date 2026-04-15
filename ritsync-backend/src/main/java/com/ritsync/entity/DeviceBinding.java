package com.ritsync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_bindings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","device_fingerprint"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceBinding {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String deviceFingerprint;

    @Column(length = 120)
    private String deviceName;

    @Column(length = 60)
    private String platform;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isTrusted = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime boundAt;

    private LocalDateTime lastSeenAt;
    
    private String verificationToken;
    
    private LocalDateTime verificationExpiry;
    
    private LocalDateTime verifiedAt;
}
