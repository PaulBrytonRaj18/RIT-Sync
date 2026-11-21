# RIT-Sync — Smart Attendance Management Platform

A full-stack attendance management platform with **triple verification**: facial recognition, GPS geofencing, and device binding.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + Vite, Tailwind CSS, Zustand, face-api.js |
| PWA | Vite PWA Plugin, Service Worker, IndexedDB (idb) |
| Backend | Spring Boot 3.2, Spring Security, Spring Data JPA |
| Database | MySQL 8+ |
| Auth | JWT (access + refresh tokens) |
| Analytics | Recharts |

---

## Project Structure

```
ritsync/
├── ritsync-frontend/          # React PWA
│   └── src/
│       ├── components/
│       │   ├── auth/          # Login, registration
│       │   ├── attendance/    # Marking UI components
│       │   ├── dashboard/     # Charts, stats cards
│       │   ├── face/          # Face camera widget
│       │   └── shared/        # Layout, nav, toasts
│       ├── pages/
│       │   ├── student/       # StudentDashboard, MarkAttendancePage, History
│       │   ├── teacher/       # TeacherDashboard, SessionManagement
│       │   └── admin/         # AdminDashboard, UserManagement, Analytics
│       ├── hooks/
│       │   ├── useFaceVerification.js   # Face-api.js wrapper
│       │   └── useGeofence.js           # Haversine GPS check
│       ├── services/
│       │   ├── api.js                   # Axios + JWT interceptors
│       │   └── syncService.js           # Offline → background sync
│       ├── store/
│       │   ├── authStore.js             # Zustand auth state
│       │   └── attendanceStore.js       # Zustand attendance state
│       └── utils/
│           └── db.js                    # IndexedDB (idb) wrapper
│
└── ritsync-backend/           # Spring Boot
    └── src/main/java/com/ritsync/
        ├── model/             # JPA entities: User, Course, Session, Record, AuditLog
        ├── repository/        # Spring Data JPA repositories
        ├── service/           # AttendanceService, AuthService, AlertService
        ├── controller/        # REST controllers
        ├── security/          # JwtUtils, JwtAuthFilter
        ├── config/            # SecurityConfig, CorsConfig
        ├── dto/               # Request/Response DTOs
        └── exception/         # Custom exceptions
```

---

## Setup

### Prerequisites
- Node.js 18+
- Java 17+
- MySQL 8+ (or Docker)
- Maven is **not** required — use the bundled wrapper (`./mvnw`, or `mvnw.cmd` on Windows)

### Configuration (required)
The app reads secrets from environment variables and **refuses to start** if
`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` or `ENCRYPTION_KEY` are missing.
For local development, put them in `ritsync-backend/.env` (gitignored, loaded automatically):

```bash
cd ritsync-backend
cp .env.example .env
# then fill in DB_USERNAME / DB_PASSWORD and generate the two secrets:
openssl rand -base64 32   # -> JWT_SECRET
openssl rand -base64 32   # -> ENCRYPTION_KEY
```

### Database setup
Either start MySQL with Docker (uses the same `.env`):
```bash
docker compose up -d mysql
```
or create the database yourself, then seed demo users after the first app start
(Hibernate creates the tables):
```bash
mysql -u root -p < src/main/resources/db/init.sql
```

### Backend
```bash
cd ritsync-backend
./mvnw spring-boot:run     # API on http://localhost:8080/api
./mvnw test                # run the test suite (uses in-memory H2, no MySQL needed)
```

### Frontend
```bash
cd ritsync-frontend
npm install
npm run dev
# Open http://localhost:3000 (Vite proxies /api to the backend on :8080)
```

### Demo accounts (password: demo1234)
| Email | Role |
|---|---|
| student@rit.edu | Student |
| teacher@rit.edu | Teacher |
| admin@rit.edu   | Admin |

---

## Face-api.js Models Setup

Download the models and place them in `ritsync-frontend/public/models/`:

```bash
cd ritsync-frontend/public
mkdir models && cd models

# Download from face-api.js CDN
curl -O https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/weights/tiny_face_detector_model-shard1
curl -O https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/weights/tiny_face_detector_model-weights_manifest.json
curl -O https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/weights/face_landmark_68_tiny_model-shard1
curl -O https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/weights/face_landmark_68_tiny_model-weights_manifest.json
curl -O https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/weights/face_recognition_model-shard1
curl -O https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/weights/face_recognition_model-shard2
curl -O https://raw.githubusercontent.com/justadudewhohacks/face-api.js/master/weights/face_recognition_model-weights_manifest.json
```

---

## Verification model (what the server enforces)

Every attendance decision is made **on the server** from submitted evidence; client-reported
"verified" flags are ignored.

| Check | Enforced rule |
|---|---|
| Eligibility | Student must be actively enrolled in the course; session open and within its time window; max 5 attempts per session |
| Face | Live 128-d descriptor must be within 0.55 of the enrolled template. Near-identical matches (< 0.02) and byte-identical re-submissions are rejected as replays. Enrollment is one-time; an admin can reset it |
| Liveness | When the session enables it: ~5 s of facial landmarks are analysed server-side for a real blink (EAR open→closed→open) and natural motion |
| GPS | Within the session geofence **and** reported accuracy ≤ 150 m |
| BLE beacon | When the session enables it: rotating token must match the beacon of *this* session, unexpired, with RSSI ≥ threshold |
| Device | First device a user signs in from is trusted; later devices need email confirmation, and only one device is trusted at a time |
| Ownership | Teachers can only manage their own courses/sessions/leaves; admins can manage all |

**Honest limits.** Face descriptors, landmarks, GPS coordinates, beacon RSSI and the device
fingerprint are all produced by the browser, so a determined attacker who scripts the API can
still forge them. These checks make proxy attendance substantially harder from a normal browser;
they are not cryptographic proof of presence. The web client has no real BLE beacon reader yet,
so leave BLE disabled unless you pair it with dedicated beacon hardware/app.

## Core Features Built

### Phase 1 — Scaffold & Auth ✅
- React PWA with Vite + Tailwind CSS
- Spring Boot 3 + MySQL + JWT auth
- Role-based routing: Student / Teacher / Admin
- Device ID fingerprinting and binding
- Zustand global state with persistence

### Phase 2 — Triple Verification ✅
- **Face recognition**: face-api.js on-device, no server upload needed
  - Liveness detection via eye blink
  - Euclidean distance comparison (threshold 0.5)
- **GPS geofencing**: Haversine formula, server-side re-verification
  - Configurable per-classroom radius (default 50m)
- **Device binding**: Stable browser fingerprint, locked on first use
- Session codes: 6-character alphanumeric, expire in N minutes

### Phase 3 — Offline-first ✅
- IndexedDB queue via `idb` library
- Automatic sync on network restore
- Service worker + Background Sync API
- Pending count badge in nav

### Phase 4 — Dashboards ✅
- **Student**: Overall %, per-course breakdown, alert banner below 75%
- **Teacher**: Live session code display, countdown timer, mark count
- **Admin**: Department bar chart, monthly trend, at-risk table, audit log
- Charts via Recharts

### Phase 5 — Manual override & Audit ✅
- Manual attendance marking by instructor
- Full audit log with action types, actor, timestamp
- Async email alerts via Spring Mail

---

## Remaining pages (Phase continuation)

The following are scaffolded as placeholders, ready to be built next:
- Student attendance history with filters
- Teacher course detail + student list
- Admin user management (CRUD)
- Admin analytics with predictions
- Face enrollment flow
- Excuse submission workflow

---

## Environment Variables

### Backend
```
DB_URL=jdbc:mysql://localhost:3306/ritsync_db
DB_USER=ritsync
DB_PASSWORD=ritsync123
JWT_SECRET=<32+ char secret>
MAIL_HOST=smtp.gmail.com
MAIL_USER=noreply@ritsync.app
MAIL_PASS=<app password>
CORS_ORIGINS=http://localhost:5173
```

### Frontend
```
VITE_API_URL=http://localhost:8080/api
```

---

## Architecture Diagram

```
Student PWA (React)
     │
     ├── 1. Camera → face-api.js → descriptor (on device, no server needed)
     ├── 2. GPS → navigator.geolocation → lat/lng
     ├── 3. Device ID → fingerprint hash
     │
     └──► POST /api/attendance/mark
               │
               ├── Validate session code + expiry
               ├── Check enrollment
               ├── Verify device binding
               ├── Verify GPS geofence (Haversine)
               ├── Save AttendanceRecord
               ├── Trigger async alert check
               └── Log to audit_logs
```
