# RIT-Sync — Smart Attendance Management Platform

A full-stack attendance management platform with **triple verification**: facial recognition, GPS geofencing, and device binding.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 18 + Vite, Tailwind CSS, Zustand, face-api.js |
| PWA | Vite PWA Plugin, Service Worker, IndexedDB (idb) |
| Backend | Spring Boot 3.2, Spring Security, Spring Data JPA |
| Database | MySQL 8+ |
| Auth | JWT (access + refresh tokens) |
| Analytics | Recharts |

---

## Project Structure

```
rit-sync/
├── ritsync-backend/            # Spring Boot Backend (Maven)
│   └── src/main/java/com/ritsync/
│       ├── entity/             # JPA Entities: User, Course, Session, AttendanceRecord, etc.
│       ├── repository/         # Spring Data JPA Repositories
│       ├── service/            # Business Logic Services
│       │   ├── AttendanceService.java       # Triple verification logic
│       │   ├── SessionService.java          # Session management
│       │   ├── FaceVerificationService.java # Face recognition comparison
│       │   ├── GeofenceService.java        # GPS Haversine calculation
│       │   ├── DeviceVerificationService.java # Device fingerprint binding
│       │   ├── FaceDescriptorService.java   # Face enrollment
│       │   ├── NotificationService.java      # Attendance threshold alerts
│       │   ├── AuthService.java            # JWT authentication
│       │   └── AuditService.java          # Audit logging
│       ├── controller/         # REST API Controllers
│       │   ├── AuthController.java         # /api/auth/*
│       │   ├── AttendanceController.java   # /api/attendance/*
│       │   ├── SessionController.java      # /api/sessions/*
│       │   └── FaceDescriptorController.java # /api/users/me/face/*
│       ├── security/           # JWT Token & Authentication
│       ├── config/              # Security Configuration
│       ├── dto/                # Request/Response DTOs
│       └── exception/           # Custom Exception Handling
│
└── ritsync-frontend/          # React PWA Frontend
    └── src/
        ├── App.jsx             # Main app with role-based routing
        ├── pages/
        │   ├── auth/          # Login, Register
        │   ├── student/      # Student dashboard, mark attendance, courses
        │   ├── teacher/      # Teacher dashboard, session management
        │   └── admin/        # Admin dashboard, user management, analytics
        ├── components/
        │   ├── verification/  # Triple verification UI components
        │   │   ├── VerificationStepper.jsx  # Progress indicator
        │   │   ├── FaceStep.jsx             # Face ID capture
        │   │   ├── GpsStep.jsx              # GPS location check
        │   │   ├── DeviceStep.jsx           # Device fingerprint
        │   │   └── VerificationResult.jsx    # Result display
        │   └── shared/       # Layout components
        ├── hooks/
        │   └── useFaceVerification.js  # face-api.js wrapper
        ├── api/              # API client functions
        │   ├── client.js     # Axios instance with interceptors
        │   ├── attendance.js # Attendance endpoints
        │   ├── sessions.js    # Session endpoints
        │   └── users.js      # User & face endpoints
        ├── store/            # Zustand state management
        │   ├── authStore.js  # Authentication state
        │   └── offlineStore.js # Offline state
        ├── offline/          # IndexedDB & sync queue
        │   └── db.js         # Database operations
        └── utils/
            ├── geo.js         # Haversine distance calculation
            └── device.js      # Device fingerprinting
```

---

## Features

### Triple Verification System
1. **Face ID** - On-device face-api.js recognition with Euclidean distance < 0.55 threshold
2. **GPS Geofencing** - Haversine formula to verify student is within classroom radius
3. **Device Binding** - Browser fingerprint (djb2 hash) auto-bound on first use

### Role-Based Dashboards
- **Students**: Mark attendance, view courses, check attendance stats
- **Teachers**: Create/manage sessions, view attendance, manual overrides
- **Admins**: User management, analytics, audit logs

### Offline Support
- IndexedDB for local data caching
- Background sync queue with automatic retry
- Offline attendance marking with sync on reconnect

---

## API Endpoints

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | User registration |
| POST | `/api/auth/login` | User login (returns JWT) |

### Attendance
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/attendance/mark` | Mark attendance (triple verified) |
| GET | `/api/attendance/session/{id}` | Get session attendance records |
| GET | `/api/attendance/stats/{studentId}/course/{courseId}` | Get attendance stats |
| PUT | `/api/attendance/override/{sessionId}/student/{studentId}` | Manual override |

### Sessions
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions` | Create session |
| GET | `/api/sessions/open` | Get open sessions |
| GET | `/api/sessions/course/{courseId}` | Get course sessions |
| PATCH | `/api/sessions/{id}/open` | Open session |
| PATCH | `/api/sessions/{id}/close` | Close session |

### Face Enrollment
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/users/me/face` | Enroll face descriptor |
| GET | `/api/users/me/face/status` | Check enrollment status |

---

## Setup

### Prerequisites
- Node.js 18+
- Java 17+
- MySQL 8+
- Maven 3.8+

### Backend
```bash
cd ritsync-backend
./mvnw spring-boot:run
# or
./mvnw clean package
java -jar target/ritsync-backend-1.0.0.jar
```

### Frontend
```bash
cd ritsync-frontend
npm install
npm run dev
# Open http://localhost:5173
```

### Environment Variables
```
# Backend
DB_URL=jdbc:mysql://localhost:3306/ritsync_db
DB_USER=ritsync
DB_PASSWORD=yourpass
JWT_SECRET=<32+ char secret>

# Frontend
VITE_API_URL=http://localhost:8080/api
```

---

## Demo Accounts (password: demo1234)
| Email | Role |
|-------|------|
| student@rit.edu | Student |
| teacher@rit.edu | Teacher |
| admin@rit.edu | Admin |

---

## Triple Verification Flow

```
Student opens /student/mark
  └─► Session selection (fetches open sessions)
        └─► Step 1 — Face ID
              face-api.js loads models → opens webcam → 3-second countdown
              → captureDescriptor() → compare vs stored descriptor
              → if distance < 0.55: PASS → advance
        └─► Step 2 — GPS
              navigator.geolocation.getCurrentPosition()
              → haversineDistance(user, session.geofence)
              → if dist ≤ session.geofenceRadiusM: PASS → advance
        └─► Step 3 — Device
              getDeviceFingerprint() → djb2 hash
              → displayed to user → auto-advance
        └─► POST /api/attendance/mark
              Backend re-validates all 3 factors independently
              → AttendanceRecord saved with individual flags
        └─► VerificationResult screen
              Shows PASS/FAIL per check + overall status
              → PRESENT if all 3 verified, ABSENT otherwise
```

---

## File Descriptions

### Backend Entities

| File | Purpose |
|------|---------|
| `User.java` | User entity with role (STUDENT/TEACHER/ADMIN), device bindings, face descriptor |
| `Course.java` | Course entity with instructor, geofence defaults |
| `Session.java` | Class session with GPS geofence coordinates and radius |
| `AttendanceRecord.java` | Individual attendance with face/gps/device verification flags |
| `Enrollment.java` | Student-course enrollment relationship |
| `DeviceBinding.java` | Trusted device fingerprint storage |
| `FaceDescriptor.java` | 128-d face vector stored as JSON |
| `AuditLog.java` | Action audit trail |

### Backend Services

| File | Purpose |
|------|---------|
| `AttendanceService.java` | Main triple verification orchestrator |
| `SessionService.java` | Create/open/close sessions, seed absent records |
| `FaceVerificationService.java` | Euclidean distance comparison (threshold 0.55) |
| `GeofenceService.java` | Haversine distance math |
| `DeviceVerificationService.java` | Fingerprint lookup + auto-bind |
| `FaceDescriptorService.java` | Enroll/update face vectors |
| `NotificationService.java` | Async threshold alerts (75%/65%) |

### Frontend Components

| File | Purpose |
|------|---------|
| `VerificationStepper.jsx` | 3-step progress indicator |
| `FaceStep.jsx` | Webcam + face-api.js capture with countdown |
| `GpsStep.jsx` | Geolocation with visual geofence ring |
| `DeviceStep.jsx` | Device fingerprint display |
| `VerificationResult.jsx` | Pass/fail screen with breakdown |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Student PWA (React)                  │
├──────────┬──────────┬──────────┬──────────────────────────┤
│ Camera   │ GPS      │ Device   │ IndexedDB               │
│ face-api │ Geoloc.  │ Fingerp. │ Sync Queue              │
└────┬─────┴────┬─────┴────┬─────┴──────────┬─────────────┘
     │          │          │                 │
     ▼          ▼          ▼                 ▼
┌─────────────────────────────────────────────────────────┐
│              POST /api/attendance/mark                    │
├──────────┬──────────┬──────────┬──────────────────────────┤
│ Face     │ GPS      │ Device   │ Audit Log                │
│ Euclidean│ Haversine│ Fingerp. │ Notification             │
│ distance │ distance │ lookup   │ Threshold Check           │
└──────────┴──────────┴──────────┴─────────────────────────┘
                          │
                          ▼
                 ┌─────────────────┐
                 │  MySQL Database │
                 └─────────────────┘
```
