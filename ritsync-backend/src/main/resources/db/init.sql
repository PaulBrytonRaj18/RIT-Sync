-- RIT-Sync Database Schema
-- Run once to create the database and demo users

CREATE DATABASE IF NOT EXISTS ritsync_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ritsync_db;

-- ── Demo Users ──────────────────────────────────────────────────
-- Password: demo1234  (BCrypt of "demo1234")
INSERT IGNORE INTO users (
    name, email, password_hash, role, department,
    roll_number, employee_id, phone,
    is_active, created_at, updated_at
) VALUES
-- Admin
(
    'System Administrator', 'admin@ritsync.com',
    '$2a$12$LwZzStPTiipUBmFCIzKXx.GbTKq7EWPFv2P9YpTk2Vz5oy0yIOFYm',
    'ADMIN', 'IT',
    NULL, NULL, NULL,
    TRUE, NOW(), NOW()
),
-- Teacher
(
    'Prof. Venkatesh Rajan', 'teacher@ritsync.com',
    '$2a$12$LwZzStPTiipUBmFCIzKXx.GbTKq7EWPFv2P9YpTk2Vz5oy0yIOFYm',
    'TEACHER', 'Computer Science',
    NULL, 'EMP001', NULL,
    TRUE, NOW(), NOW()
),
-- Student
(
    'Arjun Kumar', 'student@ritsync.com',
    '$2a$12$LwZzStPTiipUBmFCIzKXx.GbTKq7EWPFv2P9YpTk2Vz5oy0yIOFYm',
    'STUDENT', 'Computer Science',
    '21CS001', NULL, NULL,
    TRUE, NOW(), NOW()
);
