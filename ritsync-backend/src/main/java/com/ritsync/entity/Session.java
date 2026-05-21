package com.ritsync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Session {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(length = 200)
    private String title;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal geofenceLat;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal geofenceLng;

    @Column(nullable = false)
    @Builder.Default
    private Integer geofenceRadiusM = 100;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isOpen = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enableLiveness = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enableBle = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enableZkp = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isMakeup = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AttendanceRecord> attendanceRecords = new ArrayList<>();
}
