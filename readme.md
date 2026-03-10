# 🇦🇪 UAE PASS — Full-Stack Service Provider Integration

> Production-grade UAE PASS Service Provider (SP) backend + interactive Angular 21 frontend simulation — covering authentication, user linking, digital signatures, eSeal, hash signing, face biometrics, and PDPL compliance.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Repository Structure](#repository-structure)
- [Prerequisites](#prerequisites)
- [Environment Variables](#environment-variables)
- [Getting Started](#getting-started)
  - [Backend (Spring Boot)](#backend-spring-boot)
  - [Frontend (Angular)](#frontend-angular)
  - [Docker Compose (Full Stack)](#docker-compose-full-stack)
- [Backend Modules](#backend-modules)
  - [Phase 0 — Foundation](#phase-0--foundation)
  - [Phase 1 — Authentication (OIDC)](#phase-1--authentication-oidc)
  - [Phase 2 — User Linking](#phase-2--user-linking)
  - [Phase 3 — Digital Signature](#phase-3--digital-signature)
  - [Phase 4 — eSeal (PAdES + CAdES)](#phase-4--eseal-pades--cades)
  - [Phase 5 — Hash Signing](#phase-5--hash-signing)
  - [Phase 6 — Face Biometric Verification](#phase-6--face-biometric-verification)
  - [Phase 7 — Web Registration & PDPL Compliance](#phase-7--web-registration--pdpl-compliance)
- [Frontend Application](#frontend-application)
  - [Mock Services Layer](#mock-services-layer)
  - [Pages & Components](#pages--components)
  - [Routing](#routing)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [Security Controls](#security-controls)
- [UAE PASS Assessment Checklist](#uae-pass-assessment-checklist)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Profiles](#profiles)
- [License](#license)

---

## Architecture Overview

```
┌─────────────────────────────────┐        ┌──────────────────────────────────────┐
│         Angular 21 Frontend     │        │        UAE PASS Platform             │
│  (Standalone, Zoneless, Signals │  ←───→ │  stg-id.uaepass.ae / id.uaepass.ae  │
│   + 6 Mock Services for Demo)  │        │                                      │
│  localhost:4200                 │        │  · OIDC Authorization Server         │
└──────────────┬──────────────────┘        │  · Signer Process REST API           │
               │ Cookie-based session      │  · eSeal SOAP Service               │
               ▼                           │  · Face Verification API             │
┌──────────────────────────────────┐       │  · Hash Signing OAuth (hsign-as)     │
│    Spring Boot 3.2.x Backend     │ ←───→ │  · LTV SOAP Service                 │
│    (Java 17, Maven)              │       │  · Signature Verification SOAP       │
│    localhost:8080                 │       └──────────────────────────────────────┘
│                                  │
│  ┌───────┐  ┌────────────────┐   │        ┌──────────────────────────┐
│  │ Auth  │  │  Digital Sign  │   │        │  Hash-Signing Docker SDK │
│  │ Link  │  │  eSeal (SOAP)  │   │ ←───→  │  localhost:8081           │
│  │ Face  │  │  Hash Signing  │   │        │  (UAE PASS sidecar)      │
│  │ PDPL  │  │  Compliance    │   │        └──────────────────────────┘
│  └───────┘  └────────────────┘   │
│                                  │
│  JPA + Hibernate ──→ Supabase (PostgreSQL)
│  Lettuce   ──→ Upstash (Redis)
│  Local FS  ──→ /tmp/uaepass-storage/
└──────────────────────────────────┘
```

---

## Tech Stack

| Layer           | Backend                                     | Frontend                        |
|-----------------|---------------------------------------------|---------------------------------|
| **Runtime**     | Java 17, Spring Boot 3.2.5                  | Angular 21.1, TypeScript 5.9    |
| **Build**       | Maven, Docker                               | Angular CLI, npm                |
| **Database**    | Supabase (PostgreSQL)                       | localStorage (mock persistence) |
| **Cache**       | Upstash (Redis, Lettuce driver)             | —                               |
| **Auth**        | Spring Security, OAuth2, JWT (nimbus-jose)  | Mock auth service + guards      |
| **Resilience**  | Resilience4j (circuit breakers)             | —                               |
| **Logging**     | Logback + Logstash JSON encoder             | Browser console                 |
| **CI/CD**       | GitHub Actions                              | `ng build`                      |
| **Container**   | Docker, docker-compose                      | —                               |

---

## Repository Structure

```
UAE-pass/
├── demo/                              # Spring Boot Backend
│   ├── src/main/java/com/yoursp/uaepass/
│   │   ├── UaePassApplication.java    # Entry point
│   │   ├── config/                    # Security, CORS, Rate Limits, Async, Scheduler
│   │   ├── exception/                 # Global exception handler
│   │   ├── filter/                    # Correlation ID filter
│   │   ├── mock/                      # Mock SOAP + Hash SDK controllers (dev mode)
│   │   ├── model/entity/              # JPA entities (7 tables)
│   │   ├── repository/               # Spring Data JPA repositories
│   │   ├── service/                   # Audit, Storage services
│   │   └── modules/
│   │       ├── auth/                  # OIDC login, sessions, state, crypto
│   │       ├── linking/               # Auto/manual/corporate linking
│   │       ├── signature/             # PDF signing, LTV, multi-doc, verification
│   │       ├── eseal/                 # PAdES/CAdES SOAP sealing
│   │       ├── hashsigning/           # Hash-based signing via Docker SDK
│   │       ├── face/                  # Face biometric verification
│   │       ├── webreg/                # Web registration (private orgs)
│   │       └── compliance/            # Assessment checklist, data export/delete
│   ├── src/main/resources/
│   │   ├── application.yml            # Default config
│   │   ├── application-staging.yml    # Staging profile
│   │   ├── application-prod.yml       # Production profile
│   │   ├── application-mock.yml       # Mock profile (local dev)
│   │   ├── schema.sql                 # Database DDL
│   │   └── logback-spring.xml         # Structured logging
│   ├── pom.xml                        # Maven dependencies
│   ├── Dockerfile                     # Multi-stage build
│   ├── docker-compose.yml             # Full stack (app + hash SDK)
│   ├── .env.example                   # Template for env vars
│   └── .github/workflows/ci.yml      # GitHub Actions CI
│
├── frontend/                          # Angular 21 Frontend
│   ├── src/app/
│   │   ├── app.routes.ts              # All route definitions
│   │   ├── core/
│   │   │   ├── guards/                # Auth guard
│   │   │   └── services/              # 7 mock services
│   │   │       ├── mock-auth.service.ts
│   │   │       ├── mock-linking.service.ts
│   │   │       ├── mock-signature.service.ts
│   │   │       ├── mock-eseal.service.ts
│   │   │       ├── mock-hashsign.service.ts
│   │   │       ├── mock-face.service.ts
│   │   │       └── mock-compliance.service.ts
│   │   └── pages/
│   │       ├── login/                 # UAE PASS login simulation
│   │       ├── dashboard/             # Hub with 6 clickable service tiles
│   │       ├── linking/               # Account link/unlink flow
│   │       ├── signature/             # PDF signing with state machine
│   │       ├── eseal/                 # PAdES/CAdES sealing
│   │       ├── hashsign/              # Single + bulk hash signing
│   │       ├── face-verify/           # Biometric face scan animation
│   │       └── compliance/            # PDPL data export & deletion
│   ├── package.json
│   └── angular.json
│
└── readme.md                          # ← You are here
```

---

## Prerequisites

| Requirement       | Version    | Purpose                                          |
|-------------------|------------|--------------------------------------------------|
| **Java**          | 17+        | Backend runtime ([Temurin](https://adoptium.net)) |
| **Maven**         | 3.9+       | Backend build (wrapper included: `./mvnw`)       |
| **Node.js**       | 20+        | Frontend build                                    |
| **npm**           | 10+        | Frontend dependency management                    |
| **Docker**        | 24+        | Container builds (optional)                       |
| **Supabase**      | Free tier  | PostgreSQL database                               |
| **Upstash**       | Free tier  | Redis cache                                       |

---

## Environment Variables

Copy `demo/.env.example` → `demo/.env` and fill in your values:

```bash
# UAE PASS Credentials (from SP onboarding)
UAEPASS_CLIENT_ID=sandbox_stage
UAEPASS_CLIENT_SECRET=sandbox_stage
UAEPASS_BASE_URL=https://stg-id.uaepass.ae

# App URLs
APP_BASE_URL=https://your-app.up.railway.app
FRONTEND_URL=https://your-angular-app.netlify.app

# Database (Supabase PostgreSQL)
SUPABASE_DB_URL=jdbc:postgresql://db.xxxx.supabase.co:5432/postgres?sslmode=require
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=your_supabase_password

# Redis (Upstash)
UPSTASH_REDIS_URL=redis://:your_password@your-endpoint.upstash.io:6379

# Encryption Keys
IDN_ENCRYPTION_KEY=your_32_byte_hex_key_for_aes256
TOKEN_ENCRYPTION_KEY=your_32_byte_hex_key_for_tokens

# eSeal (from UAE PASS onboarding)
ESEAL_SOAP_ENDPOINT=https://eseal-stg.uaepass.ae/...
ESEAL_CERT_SUBJECT_NAME=CN=YourOrg eSeal, O=Your Org, L=Dubai, C=AE

# Hash Signing Docker SDK
HASH_SDK_URL=http://hash-signing-sdk:8081

# LTV & Verification SOAP
LTV_SOAP_ENDPOINT=https://ltv-stg.uaepass.ae/...
SIGNATURE_VERIFY_SOAP_ENDPOINT=https://verify-stg.uaepass.ae/...

# Face Verification
FACE_VERIFY_WINDOW_MINUTES=15

# Internal API
INTERNAL_API_KEY=your_random_32_char_key
```

> **⚠️ Never commit `.env` to version control.** It is already listed in `.gitignore`.

---

## Getting Started

### Backend (Spring Boot)

```bash
cd demo

# Set environment variables
source .env

# Run with Maven (default profile)
./mvnw spring-boot:run

# OR run with mock profile (local dev, no external dependencies)
SPRING_PROFILES_ACTIVE=mock ./mvnw spring-boot:run

# OR run with staging profile (auto-creates database schema)
SPRING_PROFILES_ACTIVE=staging ./mvnw spring-boot:run
```

The backend starts on **http://localhost:8080**.

Health check:
```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

### Frontend (Angular)

```bash
cd frontend

# Install dependencies
npm install

# Start dev server
npm start
# OR
npx ng serve
```

The frontend starts on **http://localhost:4200**.

> The frontend runs entirely with mock services and does **not** require the backend to be running for demonstration purposes. All UAE PASS flows are simulated with realistic delays and state machines.

### Docker Compose (Full Stack)

```bash
cd demo

# Build and run everything
docker-compose up --build

# Or in detached mode
docker-compose up -d --build
```

Services:
| Service            | Port   | Purpose                                                    |
|--------------------|--------|------------------------------------------------------------|
| `app`              | 8080   | Spring Boot backend                                        |
| `hash-signing-sdk` | 8081   | UAE PASS Hash Signing Docker SDK (replace image at deploy)  |

---

## Backend Modules

### Phase 0 — Foundation

Base Spring Boot project wired to Supabase (PostgreSQL) + Upstash (Redis), with structured JSON logging, health checks, Docker, and CI/CD.

**Key files:**
- `CorrelationIdFilter.java` — UUID per request stored in MDC, added to `X-Correlation-ID` response header
- `GlobalExceptionHandler.java` — Consistent 400/500 error responses, never exposes stack traces
- `StorageService.java` — File storage interface with `LocalStorageServiceImpl` saving to `/tmp/uaepass-storage/`
- `AuditService.java` — Logs all sensitive operations to `audit_log` table

### Phase 1 — Authentication (OIDC)

Full OpenID Connect Authorization Code flow against UAE PASS staging.

| Endpoint                   | Method | Purpose                                           |
|----------------------------|--------|---------------------------------------------------|
| `/auth/login`              | GET    | Generates state, redirects to UAE PASS authorize  |
| `/auth/callback`           | GET    | Exchanges code → token → userinfo → session       |
| `/auth/logout`             | POST   | Clears session, redirects to UAE PASS logout       |
| `/auth/me`                 | GET    | Returns authenticated user profile                 |
| `/auth/validate-token`     | POST   | Introspects token (internal SP use)                |

**Security guarantees:**
- State parameter: atomic single-use via Redis Lua script (GETDEL)
- Session: httpOnly cookie (`UAEPASS_SESSION`), Secure, SameSite=Strict
- Access token: encrypted with AES-256-GCM before Redis storage
- Emirates ID (idn): encrypted with AES-256 before database write
- Tokens never logged — `LogMaskingConverter` redacts in all log output

### Phase 2 — User Linking

Connects SP accounts to UAE PASS identities using `uaepass_uuid` as the immutable primary key.

| Scenario          | How It Works                                                                     |
|-------------------|----------------------------------------------------------------------------------|
| **Auto-Link**     | SOP2/SOP3: match by UUID first, then fallback to encrypted IDN comparison        |
| **Manual Link**   | User initiates `/auth/link` → UAE PASS login → callback links UUID to SP user    |
| **Corporate**     | Stub for org authorization — SP defines which individuals represent organizations |
| **Unlink**        | `DELETE /auth/unlink` → nulls UUID, invalidates all sessions, logs audit event   |

**Constraints:** One SP user ↔ one UAE PASS UUID (strictly one-to-one). SOP1 visitors link by UUID only — no Emirates ID.

### Phase 3 — Digital Signature

Users sign PDFs with legally binding UAE PASS digital signatures. Full state machine:

```
PENDING → INITIATED → AWAITING_USER → CALLBACK_RECEIVED → COMPLETING → SIGNED
                                                           ↓               ↓
                                                        FAILED          LTV_FAILED
                                                        CANCELED
                                                        FAILED_DOCUMENTS
EXPIRED (scheduler — 60 min timeout)
```

| Endpoint                      | Method | Purpose                                |
|-------------------------------|--------|----------------------------------------|
| `/signature/initiate`         | POST   | Upload PDF + sign params → signing URL |
| `/signature/callback`         | GET    | UAE PASS callback after user approves  |
| `/signature/status/{jobId}`   | GET    | Poll current signing state             |
| `/signature/download/{jobId}` | GET    | Download signed + LTV-enhanced PDF     |
| `/signature/verify`           | POST   | SOAP-based signature verification      |

**LTV (Long-Term Validation):** Mandatory per UAE PASS docs — applied via SOAP after every successful signing. Wrapped in Resilience4j circuit breaker.

### Phase 4 — eSeal (PAdES + CAdES)

Server-side organizational sealing. No user interaction — fully server-to-server via SOAP.

| Mode      | Profile                    | Input        | Output                        |
|-----------|----------------------------|--------------|-------------------------------|
| **PAdES** | PDF sealing                | PDF file     | Sealed PDF with embedded seal |
| **CAdES** | Binary document sealing    | Any file     | PKCS#7 detached signature     |

| Endpoint                | Method | Purpose                        |
|-------------------------|--------|--------------------------------|
| `/eseal/pdf`            | POST   | Seal a PDF (PAdES)             |
| `/eseal/document`       | POST   | Seal any document (CAdES)      |
| `/eseal/verify`         | POST   | Verify existing seal           |
| `/eseal/download/{id}`  | GET    | Download sealed document       |

### Phase 5 — Hash Signing

Signs documents by sending only the SHA-256 hash to the UAE PASS Docker SDK sidecar. Optimized for large documents and bulk operations.

**3-Step Flow:**
1. **Start** → `POST http://localhost:8081/start` → receives `txId`, `digest`
2. **Authorize** → User authenticates via UAE PASS OAuth (hsign-as endpoint)
3. **Sign** → `POST http://localhost:8081/sign` → SDK embeds signature into prepared PDF

Supports **bulk signing**: concatenated digests (`SHA-256(d1 + d2 + ... + dN)`) signed in a single user approval.

### Phase 6 — Face Biometric Verification

Adds biometric gates to sensitive operations via the `@FaceVerified` annotation.

| Endpoint                          | Method | Purpose                           |
|-----------------------------------|--------|-----------------------------------|
| `/face/initiate`                  | POST   | Start face verification request   |
| `/face/status/{verificationId}`   | GET    | Poll verification progress         |
| `/face/history`                   | GET    | Past verifications for user        |

**Security:** Strictly compares the UUID returned by face scan against the active session user's UUID to prevent impersonation. UUID mismatch triggers `SecurityIncidentService` logging.

### Phase 7 — Web Registration & PDPL Compliance

| Endpoint                         | Method | Purpose                                    |
|----------------------------------|--------|--------------------------------------------|
| `/auth/register`                 | GET    | Web registration for private org users      |
| `/users/me/data-export`         | GET    | PDPL Article 9 data export (JSON)           |
| `DELETE /users/me/data`          | DELETE | PDPL data deletion (requires confirmation)  |
| `/internal/assessment-checklist` | GET    | Self-assessment for UAE PASS Go-Live review  |

---

## Frontend Application

The Angular 21 frontend provides a **complete interactive simulation** of every UAE PASS feature — no backend required for demonstration purposes.

### Mock Services Layer

| Service                       | Simulates                                              |
|-------------------------------|--------------------------------------------------------|
| `mock-auth.service.ts`        | UAE PASS OIDC login, session, user profile              |
| `mock-linking.service.ts`     | Account link/unlink with localStorage persistence       |
| `mock-signature.service.ts`   | PDF signing state machine (6 states, 8s total cycle)    |
| `mock-eseal.service.ts`       | PAdES/CAdES sealing with SOAP delay simulation          |
| `mock-hashsign.service.ts`    | Hash signing + bulk signing (3-step SDK flow)            |
| `mock-face.service.ts`        | Face scan animation with 4-step verification            |
| `mock-compliance.service.ts`  | Data summary, JSON export, deletion with confirmation    |

All services use realistic delays, state machines, and persist data in `localStorage`.

### Pages & Components

| Page               | Route           | Features                                                              |
|--------------------|-----------------|-----------------------------------------------------------------------|
| **Login**          | `/`             | Emirates ID input → mock OIDC redirect animation → session creation    |
| **Dashboard**      | `/dashboard`    | Digital ID card, session info, 6 clickable service navigation tiles    |
| **Digital Signature** | `/signature` | Drag-and-drop upload → placement form → state machine → download      |
| **eSeal**          | `/eseal`        | PAdES/CAdES toggle → file upload → SOAP animation → seal result       |
| **Hash Signing**   | `/hashsign`     | Single/bulk tabs → 3-step SDK flow with digest display                |
| **Face Verify**    | `/face-verify`  | Purpose + username input → animated scanning → verified result         |
| **Account Linking**| `/linking`      | Link status display → link/unlink flows with confirmation modal        |
| **Data & Privacy** | `/compliance`   | Data summary → field tags → JSON export → danger zone deletion         |

### Routing

All feature pages are protected by `authGuard` — unauthenticated users are redirected to the login page.

```typescript
{ path: 'dashboard',   component: DashboardComponent,   canActivate: [authGuard] }
{ path: 'linking',     component: LinkingComponent,     canActivate: [authGuard] }
{ path: 'signature',   component: SignatureComponent,   canActivate: [authGuard] }
{ path: 'eseal',       component: EsealComponent,       canActivate: [authGuard] }
{ path: 'hashsign',    component: HashsignComponent,    canActivate: [authGuard] }
{ path: 'face-verify', component: FaceVerifyComponent,  canActivate: [authGuard] }
{ path: 'compliance',  component: ComplianceComponent,  canActivate: [authGuard] }
```

---

## Database Schema

7 tables managed via `schema.sql` (auto-executed on `staging` profile):

| Table                  | Purpose                               | Key Columns                                        |
|------------------------|---------------------------------------|----------------------------------------------------|
| `users`                | UAE PASS authenticated users          | `uaepass_uuid` (unique), `idn` (encrypted), `user_type` |
| `oauth_states`         | OAuth2 state parameter tracking       | `state` (PK), `flow_type`, `expires_at`, `used`    |
| `user_sessions`        | Active session management             | `session_token` (unique), `token_expires`, `ip_address` |
| `signing_jobs`         | Digital signature workflow tracking   | `signer_process_id`, `status`, `documents` (JSONB)  |
| `eseal_jobs`           | eSeal operations                      | `seal_type`, `status`, `request_id`                 |
| `face_verifications`   | Face biometric verification records   | `verified_uuid`, `uuid_match`, `purpose`            |
| `audit_log`            | Audit trail for all sensitive ops     | `action`, `entity_type`, `metadata` (JSONB)         |

Indexes on: `users.uaepass_uuid`, `signing_jobs.user_id`, `signing_jobs.status`, `face_verifications.user_id`, `audit_log.user_id`, `audit_log.created_at DESC`.

---

## API Reference

### Authentication
| Method | Endpoint               | Auth     | Description                                   |
|--------|------------------------|----------|-----------------------------------------------|
| GET    | `/auth/login`          | Public   | Redirects to UAE PASS authorization            |
| GET    | `/auth/callback`       | Public   | Handles OAuth2 callback, creates session       |
| POST   | `/auth/logout`         | Session  | Clears session, redirects to UAE PASS logout   |
| GET    | `/auth/me`             | Session  | Returns current user profile                   |

### Linking
| Method | Endpoint               | Auth     | Description                 |
|--------|------------------------|----------|-----------------------------|
| GET    | `/auth/link`           | Session  | Initiates manual linking     |
| GET    | `/auth/link/callback`  | Session  | Handles link callback        |
| DELETE | `/auth/unlink`         | Session  | Unlinks UAE PASS identity    |

### Digital Signature
| Method | Endpoint                        | Auth     | Description                      |
|--------|---------------------------------|----------|----------------------------------|
| POST   | `/signature/initiate`           | Session  | Initiates PDF signing            |
| GET    | `/signature/callback`           | Public   | UAE PASS signing callback         |
| GET    | `/signature/status/{jobId}`     | Session  | Polls signing status              |
| GET    | `/signature/download/{jobId}`   | Session  | Downloads signed PDF              |

### eSeal
| Method | Endpoint                | Auth       | Description              |
|--------|-------------------------|------------|--------------------------|
| POST   | `/eseal/pdf`            | Session    | Seal PDF (PAdES)          |
| POST   | `/eseal/document`       | Session    | Seal document (CAdES)     |
| POST   | `/eseal/verify`         | Session    | Verify existing seal      |
| GET    | `/eseal/download/{id}`  | Session    | Download sealed document  |

### Face Verification
| Method | Endpoint                             | Auth     | Description                 |
|--------|--------------------------------------|----------|-----------------------------|
| POST   | `/face/initiate`                     | Session  | Start face verification      |
| GET    | `/face/status/{verificationId}`      | Session  | Poll verification progress   |

### Compliance
| Method | Endpoint                           | Auth        | Description                  |
|--------|------------------------------------|-------------|------------------------------|
| GET    | `/users/me/data-export`            | Session     | PDPL data export (JSON)       |
| DELETE | `/users/me/data`                   | Session     | PDPL data deletion            |
| GET    | `/internal/assessment-checklist`   | Internal Key| Self-assessment checklist     |

---

## Security Controls

| Control                | Implementation                                                              |
|------------------------|-----------------------------------------------------------------------------|
| **Security Headers**   | HSTS, X-Content-Type-Options, X-Frame-Options: DENY, CSP, Cache-Control    |
| **Rate Limiting**      | Redis sliding window (`RateLimitFilter`) — login: 10/min, register: 3/5min |
| **Input Validation**   | Bean Validation (`@Valid`), PDF magic bytes check, filename sanitization     |
| **Session Hardening**  | 24h absolute expiry, IP mismatch warning, token rotation after sensitive ops|
| **Data Protection**    | AES-256-GCM for IDN encryption, Redis-only token refs, log masking         |
| **PDPL Compliance**    | `DELETE /users/me/data`, `GET /users/me/data-export`                        |
| **Correlation IDs**    | UUID per request in MDC, `X-Correlation-ID` response header                 |
| **Circuit Breakers**   | Resilience4j on all external calls (eSeal SOAP, LTV, SP token)             |

---

## UAE PASS Assessment Checklist

Self-assessment endpoint: `GET /internal/assessment-checklist` (requires `X-Internal-Key` header).

| # | Item                     | Implementation                                                  |
|---|--------------------------|----------------------------------------------------------------|
| 1 | Auth OIDC Flow           | `/auth/login` → `/auth/callback` → `/auth/me`                  |
| 2 | State Single-Use         | `StateService.consumeState()` — Redis GETDEL (atomic)           |
| 3 | LTV on Signatures        | `LtvService.applyLtv()` called post-signing                     |
| 4 | UUID Primary Key         | `uaepass_uuid` used for all linking — never email/mobile         |
| 5 | Face UUID Match          | `FaceVerificationService` compares UUIDs strictly                |
| 6 | httpOnly Sessions        | `cookie.setHttpOnly(true)`, `SameSite=Strict`                   |
| 7 | IDN Encrypted            | `CryptoUtil.encryptAES256()` before DB write                     |
| 8 | Tokens Not Logged        | `LogMaskingConverter` masks Bearer/access_token in logs           |
| 9 | Audit Log                | `AuditService.log()` on all sensitive operations                 |

---

## Testing

### Backend
```bash
cd demo
./mvnw test
```

### Frontend
```bash
cd frontend
npm run build    # Verify build succeeds (zero errors)
npm start        # Run dev server for manual verification
```

---

## CI/CD

GitHub Actions workflow: `demo/.github/workflows/ci.yml`

**Pipeline:** Checkout → Java 17 setup → `mvn test` → `mvn package` → `docker build`

**Required GitHub Secrets:**
- `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`
- `UPSTASH_REDIS_URL`
- `UAEPASS_CLIENT_ID`, `UAEPASS_CLIENT_SECRET`

---

## Profiles

| Profile     | Usage          | Key Behavior                                                    |
|-------------|----------------|-----------------------------------------------------------------|
| `default`   | Local dev      | Console logging, H2 in-memory DB, schema auto-generate          |
| `mock`      | Mock mode      | Mock SOAP + Hash SDK controllers active, no external deps needed|
| `staging`   | Staging server | JSON logging, `schema.sql` executed on startup                   |
| `prod`      | Production     | JSON logging (WARN+), schema init OFF, full security headers     |

```bash
# Activate a profile
SPRING_PROFILES_ACTIVE=staging ./mvnw spring-boot:run
```

---

## License

Proprietary — UAE PASS integration for authorized Service Providers only.
