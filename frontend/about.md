# UAE PASS Integration App - Detailed Explanation

## Overview
This application is a full-stack project consisting of a Java Spring Boot 3 backend and an Angular 21 frontend. It provides a comprehensive integration with the UAE PASS system, acting as a Service Provider (SP). It enables authentication, user linking, digital signatures, e-seals, hash signing, and facial biometric confirmation.

## Architecture & Tech Stack
- **Backend:** Java 17, Spring Boot 3.2.x, Maven, Spring Data JPA, and Spring Data Redis.
- **Frontend:** Angular 21, TypeScript, RxJS.
- **Database:** Supabase (PostgreSQL) for relational data persistence.
- **Cache:** Upstash (Redis) for fast session and state management.
- **Deployment & CI:** Docker for local running, GitHub actions for CI.

---

## Backend Structure (`demo/src/main/java/com/yoursp/uaepass/`)

The backend is organized with standard Spring Boot conventions and domain-driven design principles centered around the UX phases of UAE PASS.

### Core Folders
- `config/`: Configuration classes (e.g., `SecurityConfig` for defining protected routes and CORS).
- `exception/`: Global exception handling, e.g., `GlobalExceptionHandler` returning consistent API error formatting.
- `filter/`: Request filters such as `CorrelationIdFilter` for tracing requests across logs.
- `model/entity/`: JPA data models mapping to database tables (`User`, `OAuthState`, `UserSession`, `SigningJob`, `EsealJob`, `FaceVerification`, `AuditLog`).
- `repository/`: Spring Data JPA repositories handling direct database operations.
- `service/`: General foundational services (like `AuditService` for action tracking and `StorageService` for files).

### Modules (`modules/`)
The backend's business logic is internally structured according to specific UAE PASS integration phases:

1. **`auth/` (Authentication - Phase 1):** Handles the OIDC Auth Code flow, login callbacks, session creation, and UAE PASS token validation. Contains `AuthController`, `StateService`, `UserSyncService`, and `SessionAuthFilter`.
2. **`linking/` (User Linking - Phase 2):** Connects SP accounts to UAE PASS UUIDs. Contains auto-linking and manual linking logic, ensuring the `uaepass_uuid` acts as the primary immutable identity key.
3. **`signature/` (Digital Signature - Phase 3):** Allows users to digitally sign PDFs. Manages document upload, interaction with UAE PASS signer processes, Long-Term Validation (LTV) configuration, and callback handling.
4. **`eseal/` (eSeal Module - Phase 4):** Provides organizational server-side sealing of documents. Supports PAdES for PDFs and CAdES for non-PDFs via SOAP Web Service calls.
5. **`hashsigning/` (Hash Signing - Phase 5):** Signs documents by sending only their SHA-256 hash to a local UAE PASS Docker SDK, embedding the returned signature into the original document locally.
6. **`face/` (Facial Biometrics - Phase 6):** Adds biometric verification gates to sensitive operations. Sends requests to UAE PASS for face scans and strictly validates the returned UUID against the active session to prevent impersonation.
7. **`webreg/` (Web Registration - Phase 7):** Allows Private Organizations to register new UAE PASS users directly through the SP application.
8. **`compliance/` (Security Hardening):** Implements endpoints and logic for data deletion, export, and assessment checklists required by UAE PASS for Go-Live compliance.

---

## Frontend Structure (`frontend/src/app/`)

The frontend is a lightweight Angular application that communicates with the Spring Boot API.

### `core/`
- **`guards/`**: Contains Angular route guards to protect components that require authentication (e.g., ensuring an active session before accessing the dashboard).
- **`services/`**: Angular services fetching data from the backend. This includes `mock-auth.service.ts` for handling auth flows and managing session state locally for testing/development.

### `pages/`
- **`login/`**: The starting point structure. Contains the UI prompting users to authenticate or register using UAE PASS, which triggers redirects to the backend OAuth2 authorize endpoints.
- **`dashboard/`**: The main authenticated view for users to access their profile, initiate document signing, or use face verification capabilities once the session cookie is established.

---

## UAE PASS Implementation Phases

The project was constructed following the official UAE PASS onboarding guidelines in distinct logical sprint phases:

- **PHASE 0 — Foundation:** Base Spring Boot project setup, connected to Supabase PostgreSQL and Upstash Redis. Foundation tools like Logging, Exception Handling, Health checks, Docker, and CI/CD pipelines created.
- **PHASE 1 — UAE PASS Authentication:** Full OpenID Connect (OIDC) flow (login, callback, session generation, state management via Redis) running against the UAE PASS staging environment.
- **PHASE 2 — User Linking:** Managing auto-linking via Emirates ID (for SOP2/SOP3 residents and citizens) and manual linking of identities strictly to the `uaepass_uuid`.
- **PHASE 3 — Digital Signature:** Single & batch PDF signing logic involving SP token fetching, Signer process UI redirects, callbacks, signed document downloads, and applying mandatory LTV (Long-Term Validation).
- **PHASE 4 — eSeal Module:** Synchronous SOAP integrations for organizational cryptographic sealing of documents (PAdES and CAdES profiles). No user interaction required.
- **PHASE 5 — Hash Signing:** Specialized signing handled via a local UAE PASS Docker SDK sidecar (`start`, `sign`), sending only encrypted hashes across the wire, speeding up the signing of massive documents and bulk batches. 
- **PHASE 6 — Facial Biometric Confirmation:** Implementation of the `@FaceVerified` security guard to enforce a live face-scan via the UAE PASS app before sensitive actions (like deleting data or unlinking accounts) can be performed.
- **PHASE 7 — Web Registration & Hardening:** Implementation of newly-configured registration flows exclusively for private organizations, along with rigorous security hardening (Rate Limits, PDPL compliance, Security Headers, and Log Masking) to pass the UAE PASS Go-Live Assessment.
