-- RIT-Sync Database Schema
-- Run once during initial setup

CREATE DATABASE IF NOT EXISTS ritsync_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ritsync_db;

-- Users (students, teachers, admins)
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120)        NOT NULL,
    email           VARCHAR(150)        NOT NULL UNIQUE,
    password_hash   VARCHAR(255)        NOT NULL,
    role            ENUM('STUDENT','TEACHER','ADMIN') NOT NULL DEFAULT 'STUDENT',
    department      VARCHAR(100),
    roll_number     VARCHAR(30),                        -- for students
    employee_id     VARCHAR(30),                        -- for staff
    phone           VARCHAR(20),
    avatar_url      VARCHAR(500),
    is_active       BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_role  (role),
    INDEX idx_users_dept  (department)
) ENGINE=InnoDB;

-- Device fingerprints bound per user
CREATE TABLE IF NOT EXISTS device_bindings (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    device_fingerprint  VARCHAR(255)    NOT NULL,
    device_name         VARCHAR(120),
    platform            VARCHAR(60),
    is_trusted          BOOLEAN         NOT NULL DEFAULT TRUE,
    bound_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at        TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_device_binding (user_id, device_fingerprint),
    INDEX idx_device_fingerprint (device_fingerprint)
) ENGINE=InnoDB;

-- Encrypted face descriptor vectors (128-d float array stored as JSON blob)
CREATE TABLE IF NOT EXISTS face_descriptors (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL UNIQUE,
    descriptor      LONGTEXT        NOT NULL,   -- JSON array of float values
    enrolled_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Courses
CREATE TABLE IF NOT EXISTS courses (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(20)     NOT NULL UNIQUE,
    name            VARCHAR(150)    NOT NULL,
    instructor_id   BIGINT          NOT NULL,
    department      VARCHAR(100),
    credit_hours    INT             NOT NULL DEFAULT 3,
    semester        VARCHAR(20),
    academic_year   VARCHAR(10),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (instructor_id) REFERENCES users(id),
    INDEX idx_courses_instructor (instructor_id),
    INDEX idx_courses_dept (department)
) ENGINE=InnoDB;

-- Student-course enrollments
CREATE TABLE IF NOT EXISTS enrollments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id      BIGINT          NOT NULL,
    course_id       BIGINT          NOT NULL,
    enrolled_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (course_id)  REFERENCES courses(id) ON DELETE CASCADE,
    UNIQUE KEY uq_enrollment (student_id, course_id)
) ENGINE=InnoDB;

-- Individual class sessions
CREATE TABLE IF NOT EXISTS sessions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id           BIGINT          NOT NULL,
    title               VARCHAR(200),
    start_time          DATETIME        NOT NULL,
    end_time            DATETIME        NOT NULL,
    geofence_lat        DECIMAL(10,7)   NOT NULL,
    geofence_lng        DECIMAL(10,7)   NOT NULL,
    geofence_radius_m   INT             NOT NULL DEFAULT 100,
    is_open             BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    INDEX idx_sessions_course    (course_id),
    INDEX idx_sessions_open      (is_open),
    INDEX idx_sessions_starttime (start_time)
) ENGINE=InnoDB;

-- Attendance records (one per student per session)
CREATE TABLE IF NOT EXISTS attendance_records (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT          NOT NULL,
    student_id          BIGINT          NOT NULL,
    status              ENUM('PRESENT','ABSENT','EXCUSED','LATE') NOT NULL DEFAULT 'ABSENT',
    face_verified       BOOLEAN         NOT NULL DEFAULT FALSE,
    gps_verified        BOOLEAN         NOT NULL DEFAULT FALSE,
    device_verified     BOOLEAN         NOT NULL DEFAULT FALSE,
    manually_overridden BOOLEAN         NOT NULL DEFAULT FALSE,
    override_reason     TEXT,
    overridden_by       BIGINT,
    marked_at           TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id)    REFERENCES sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id)    REFERENCES users(id)    ON DELETE CASCADE,
    FOREIGN KEY (overridden_by) REFERENCES users(id),
    UNIQUE KEY uq_attendance (session_id, student_id),
    INDEX idx_att_student  (student_id),
    INDEX idx_att_session  (session_id),
    INDEX idx_att_status   (status)
) ENGINE=InnoDB;

-- Immutable audit trail
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id        BIGINT,
    actor_email     VARCHAR(150),
    action          VARCHAR(100)    NOT NULL,
    target_table    VARCHAR(60),
    target_id       BIGINT,
    payload         JSON,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_actor  (actor_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_time   (created_at)
) ENGINE=InnoDB;

-- Notification records
CREATE TABLE IF NOT EXISTS notifications (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    type        ENUM('THRESHOLD_WARNING','THRESHOLD_CRITICAL','SESSION_OPEN','MANUAL_OVERRIDE','GENERAL') NOT NULL,
    title       VARCHAR(200)    NOT NULL,
    message     TEXT            NOT NULL,
    is_read     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notif_user   (user_id),
    INDEX idx_notif_unread (user_id, is_read)
) ENGINE=InnoDB;

-- Seed default admin
INSERT IGNORE INTO users (name, email, password_hash, role, department)
VALUES ('System Admin', 'admin@ritsync.com',
        '$2a$12$placeholder.change.this.on.first.login.XXXXXXXXXX',
        'ADMIN', 'IT');
