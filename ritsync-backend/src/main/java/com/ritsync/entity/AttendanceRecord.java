package com.ritsync.entity;

import com.ritsync.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id","student_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.ABSENT;

    @Column(nullable = false) @Builder.Default private Boolean faceVerified       = false;
    @Column(nullable = false) @Builder.Default private Boolean gpsVerified        = false;
    @Column(nullable = false) @Builder.Default private Boolean deviceVerified     = false;
    @Column(nullable = false) @Builder.Default private Boolean livenessVerified   = false;
    @Column(nullable = false) @Builder.Default private Boolean beaconVerified     = false;

    @Builder.Default private Integer blinkCount = 0;

    /** Number of verification attempts the student made for this session (capped). */
    @Column(nullable = false) @Builder.Default private Integer attemptCount = 0;

    /** SHA-256 of the submitted live descriptor; identical re-submissions are rejected as replays. */
    @Column(length = 64)
    private String faceDescriptorHash;

    private Double gpsAccuracy;

    private String beaconUuid;
    private String beaconToken;
    private Integer beaconRssi;

    @Column(nullable = false) @Builder.Default private Boolean manuallyOverridden = false;

    @Column(columnDefinition = "TEXT")
    private String overrideReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "overridden_by")
    private User overriddenBy;

    private LocalDateTime markedAt;

    private Double userLat;
    private Double userLng;

    @CreationTimestamp @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp   @Column(nullable = false)                    private LocalDateTime updatedAt;
}
