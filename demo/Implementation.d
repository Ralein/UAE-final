# UAE PASS — Master Prompts Per Phase
### Stack: Spring Boot 3 (Java 17) · Angular 17 · Supabase (PostgreSQL) · Upstash (Redis)
### All endpoints verified against docs.uaepass.ae

---

> **How to use these prompts**
> Copy each prompt exactly into your AI coding tool (Cursor, Claude, Copilot etc.) at the start of that phase.
> Each prompt is fully self-contained — it carries the full context needed for that phase.
> Do NOT mix phases in one session. One prompt = one focused build session.

---

## PHASE 0 — Foundation
### 🎯 Goal: Spring Boot project wired to Supabase + Upstash, CI/CD running, nothing broken.

```
You are building the foundation of a production-grade UAE PASS Service Provider (SP) backend.

TECH STACK:
- Java 17, Spring Boot 3.2.x
- Maven build tool
- Spring Data JPA (Hibernate) for database access
- Spring Data Redis (Lettuce driver) for Redis
- Supabase as PostgreSQL host (free tier, max 60 connections)
- Upstash as Redis host (free tier, max 10,000 commands/day)
- Logback for structured JSON logging
- Spring Boot Actuator for health checks
- Docker + docker-compose for local dev
- GitHub Actions for CI/CD

TASK: Generate the complete project scaffold with the following:

1. pom.xml with these dependencies:
   - spring-boot-starter-web
   - spring-boot-starter-security
   - spring-boot-starter-data-jpa
   - spring-boot-starter-data-redis
   - spring-boot-starter-validation
   - spring-boot-starter-actuator
   - spring-boot-starter-oauth2-client
   - postgresql driver
   - logstash-logback-encoder (structured JSON logs)
   - lombok
   - resilience4j-spring-boot3 (circuit breakers for external calls)

2. application.yml with profiles: default (local), staging, prod
   - Supabase datasource config (JDBC URL from env var SUPABASE_DB_URL)
   - HikariCP pool: maximumPoolSize=10 (respect Supabase free tier 60-connection limit)
   - Upstash Redis config (spring.data.redis.url from env var UPSTASH_REDIS_URL)
   - Actuator: expose health and info endpoints only
   - Logging: JSON format, include correlation-id in every log line

3. CorrelationIdFilter.java
   - Servlet filter that generates UUID per request
   - Stores in MDC as "correlationId"
   - Adds X-Correlation-ID to response header

4. GlobalExceptionHandler.java
   - @RestControllerAdvice
   - Handle MethodArgumentNotValidException → 400 with field errors
   - Handle generic Exception → 500 with correlationId in response
   - NEVER expose stack traces in response body

5. StorageService.java (interface only):
   public interface StorageService {
     String upload(byte[] data, String key, String contentType);
     byte[] download(String key);
     void delete(String key);
   }
   Plus LocalStorageServiceImpl.java that saves to /tmp/uaepass-storage/ for dev/staging.

6. docker-compose.yml for local development:
   - app service (Spring Boot, port 8080)
   - No local DB or Redis needed (uses Supabase + Upstash env vars)
   - hash-signing-sdk service placeholder (image: uaepass-esign:local, port 8081) with a comment saying "replace with actual Docker image from UAE PASS onboarding team"

7. Dockerfile for the Spring Boot app (multi-stage: build → runtime on eclipse-temurin:17-jre-alpine)

8. .github/workflows/ci.yml:
   - Trigger: push to main
   - Steps: checkout → Java 17 setup → mvn test → mvn package -DskipTests → docker build
   - Store SUPABASE_DB_URL, UPSTASH_REDIS_URL, UAEPASS_CLIENT_ID, UAEPASS_CLIENT_SECRET as GitHub Secrets (show how to reference them in the workflow)

9. DatabaseSchemaInitializer.java or schema.sql (run on startup in staging only):
   Execute this exact SQL on Supabase:

   CREATE EXTENSION IF NOT EXISTS pgcrypto;

   CREATE TABLE IF NOT EXISTS users (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     uaepass_uuid VARCHAR(255) UNIQUE NOT NULL,
     spuuid VARCHAR(255),
     idn TEXT,
     email VARCHAR(255),
     mobile VARCHAR(30),
     full_name_en VARCHAR(500),
     full_name_ar VARCHAR(500),
     first_name_en VARCHAR(255),
     last_name_en VARCHAR(255),
     nationality_en CHAR(3),
     gender VARCHAR(10),
     user_type VARCHAR(10),
     id_type VARCHAR(20),
     acr TEXT,
     linked_at TIMESTAMPTZ DEFAULT NOW(),
     created_at TIMESTAMPTZ DEFAULT NOW(),
     updated_at TIMESTAMPTZ DEFAULT NOW()
   );

   CREATE TABLE IF NOT EXISTS oauth_states (
     state VARCHAR(256) PRIMARY KEY,
     flow_type VARCHAR(50),
     redirect_after TEXT,
     user_id UUID REFERENCES users(id),
     expires_at TIMESTAMPTZ,
     used BOOLEAN DEFAULT FALSE,
     created_at TIMESTAMPTZ DEFAULT NOW()
   );

   CREATE TABLE IF NOT EXISTS user_sessions (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     user_id UUID REFERENCES users(id) ON DELETE CASCADE,
     session_token VARCHAR(512) UNIQUE NOT NULL,
     uaepass_token_ref VARCHAR(256),
     token_expires TIMESTAMPTZ,
     ip_address INET,
     user_agent TEXT,
     created_at TIMESTAMPTZ DEFAULT NOW(),
     last_active TIMESTAMPTZ DEFAULT NOW()
   );

   CREATE TABLE IF NOT EXISTS signing_jobs (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     signer_process_id VARCHAR(255) UNIQUE,
     user_id UUID REFERENCES users(id),
     signing_type VARCHAR(20),
     status VARCHAR(30),
     document_count INT DEFAULT 1,
     sign_identity_id VARCHAR(255),
     documents JSONB,
     finish_callback_url TEXT,
     callback_status VARCHAR(20),
     ltv_applied BOOLEAN DEFAULT FALSE,
     error_message TEXT,
     created_at TIMESTAMPTZ DEFAULT NOW(),
     initiated_at TIMESTAMPTZ,
     completed_at TIMESTAMPTZ,
     expires_at TIMESTAMPTZ
   );

   CREATE TABLE IF NOT EXISTS eseal_jobs (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     requested_by UUID REFERENCES users(id),
     seal_type VARCHAR(10),
     status VARCHAR(20),
     input_key TEXT,
     output_key TEXT,
     request_id VARCHAR(255),
     error_message TEXT,
     created_at TIMESTAMPTZ DEFAULT NOW(),
     completed_at TIMESTAMPTZ
   );

   CREATE TABLE IF NOT EXISTS face_verifications (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     user_id UUID REFERENCES users(id),
     transaction_ref VARCHAR(255),
     purpose TEXT,
     status VARCHAR(20),
     username_used VARCHAR(20),
     verified_uuid VARCHAR(255),
     uuid_match BOOLEAN,
     created_at TIMESTAMPTZ DEFAULT NOW(),
     verified_at TIMESTAMPTZ
   );

   CREATE TABLE IF NOT EXISTS audit_log (
     id BIGSERIAL PRIMARY KEY,
     user_id UUID REFERENCES users(id),
     action VARCHAR(100),
     entity_type VARCHAR(50),
     entity_id VARCHAR(255),
     ip_address INET,
     metadata JSONB,
     created_at TIMESTAMPTZ DEFAULT NOW()
   );

   CREATE INDEX IF NOT EXISTS idx_users_uaepass_uuid ON users(uaepass_uuid);
   CREATE INDEX IF NOT EXISTS idx_signing_jobs_user ON signing_jobs(user_id);
   CREATE INDEX IF NOT EXISTS idx_signing_jobs_status ON signing_jobs(status);
   CREATE INDEX IF NOT EXISTS idx_face_verifications_user ON face_verifications(user_id);
   CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log(user_id);
   CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at DESC);

10. JPA Entity classes for all tables above (in package com.yoursp.uaepass.model.entity)
    - Use @Column, @Id, @GeneratedValue correctly
    - Map JSONB columns as String in Java (parse manually when needed)
    - DO NOT use Hibernate auto DDL — schema is managed manually

11. AuditService.java:
    - Simple service with method: log(UUID userId, String action, String entityType, String entityId, String ip, Map<String,Object> metadata)
    - Inserts into audit_log table
    - Called from every sensitive operation going forward

CONSTRAINTS:
- Keep security config permissive for now (permitAll) — real security added in Phase 2
- No business logic yet — just structure, wiring, and connectivity
- Everything must start and connect to Supabase + Upstash with env vars set
- Write a README.md with: how to set env vars, how to run locally with docker-compose, how to run tests
```

---

## PHASE 1 — UAE PASS Authentication (OIDC Full Flow)
### 🎯 Goal: Login, callback, session, logout, token validation — all working against staging.

```
You are implementing the complete UAE PASS Authentication module for a Spring Boot 3 backend.

CONTEXT:
- UAE PASS is an OpenID Connect (OIDC) Provider using standard OAuth2 Authorization Code flow
- Your backend is the OIDC Relying Party (confidential client) — client_secret NEVER goes to browser
- The Angular frontend has NO knowledge of tokens — it only talks to your backend via httpOnly session cookie
- All token exchange happens server-side only
- Access token TTL is fixed at 3600 seconds (no refresh token, no extension possible)
- Authorization code expires in 10 minutes — process it immediately

VERIFIED ENDPOINTS (from docs.uaepass.ae):
  Staging Authorization: https://stg-id.uaepass.ae/idshub/authorize
  Staging Token:         https://stg-id.uaepass.ae/idshub/token
  Staging UserInfo:      https://stg-id.uaepass.ae/idshub/userinfo
  Staging Logout:        https://stg-id.uaepass.ae/idshub/logout
  Token Introspect:      https://stg-id.uaepass.ae/idshub/introspect
  (Replace stg-id. with id. for production)

USER ATTRIBUTES returned by /userinfo (all fields you must map):
  uuid, sub, userType (SOP1/SOP2/SOP3), fullnameEN, fullnameAR,
  firstnameEN, lastnameEN, nationalityEN, gender, mobile, email,
  idn (Emirates ID — encrypt this), idType, acr, amr, spuuid

TASK: Build the following — all in package com.yoursp.uaepass.modules.auth:

1. SecurityConfig.java:
   - Configure Spring Security to allow /auth/**, /public/**, /actuator/health without auth
   - All other routes require authenticated session
   - Disable CSRF (using httpOnly cookie + SameSite=Strict instead)
   - No formLogin, no httpBasic
   - CORS: allow Angular dev origin (http://localhost:4200) and staging frontend URL from env var FRONTEND_URL

2. application-staging.yml additions:
   uaepass:
     client-id: ${UAEPASS_CLIENT_ID}
     client-secret: ${UAEPASS_CLIENT_SECRET}
     base-url: https://stg-id.uaepass.ae
     redirect-uri: ${APP_BASE_URL}/auth/callback
     scope: "openid urn:uae:digitalid:profile:general"
     acr-values: "urn:safelayer:tws:policies:authentication:level:low"
     ui-locales: "en"

3. StateService.java (Upstash Redis):
   - generateState(String flowType, String redirectAfter, UUID userId): 
       → stores in Redis: SETEX "oauth_state:{uuid}" 300 {json payload}
       → json payload: {flowType, redirectAfter, userId, createdAt}
       → returns the state string (UUID)
   - consumeState(String state): 
       → GET then DEL atomically using Lua script (single-use, atomic)
       → returns StatePayload or throws InvalidStateException if missing/expired
   - IMPORTANT: use Redis pipeline for atomic GET+DEL to prevent race conditions

4. AuthController.java with these endpoints:
   
   GET /auth/login?redirectAfter={url}:
   - Generate state via StateService (flowType="AUTH")
   - Build authorization URL:
     {uaepass.base-url}/idshub/authorize
       ?response_type=code
       &client_id={client-id}
       &redirect_uri={redirect-uri}
       &scope={scope}
       &state={state}
       &acr_values={acr-values}
       &ui_locales={ui-locales}
   - Return 302 redirect to that URL
   
   GET /auth/callback?code={code}&state={state}&error={error}:
   - If error param present: redirect to frontend with error message
   - Consume state from Redis (throws 401 if invalid/expired/reused)
   - Exchange code for tokens: POST to /idshub/token
       body: grant_type=authorization_code&code={code}&redirect_uri={redirect_uri}
       header: Authorization: Basic base64(client_id:client_secret)
       Content-Type: application/x-www-form-urlencoded
   - Validate id_token JWT (signature against UAE PASS JWKS, iss, aud, exp claims)
   - Fetch user info: GET /idshub/userinfo with Authorization: Bearer {access_token}
   - Call UserSyncService.syncUser(userInfoMap) → returns User entity
   - Store access_token encrypted in Redis: SETEX "uaepass_token:{userId}" 3500 {encrypted}
   - Create session: generate 64-byte random session token → insert into user_sessions table
   - Set cookie: HttpOnly=true, Secure=true, SameSite=Strict, Path=/, MaxAge=3600
     Cookie name: UAEPASS_SESSION
   - Redirect to statePayload.redirectAfter or /dashboard
   
   POST /auth/logout:
   - Read UAEPASS_SESSION cookie
   - Delete from user_sessions table
   - Delete uaepass_token:{userId} from Redis
   - Clear cookie (set MaxAge=0)
   - Redirect to: {uaepass.base-url}/idshub/logout?post_logout_redirect_uri={frontend-url}
   
   GET /auth/me:
   - Requires active session (enforced by SessionAuthFilter)
   - Return UserProfileDto (NEVER return raw UAE PASS token):
     {id, fullNameEn, fullNameAr, email, mobile, userType, nationality, linkedAt}
   
   POST /auth/validate-token (internal use, require SP service auth header):
   - POST to /idshub/introspect: token={access_token}
   - Auth: Basic client_id:client_secret
   - Returns: {active, clientId, sub, exp, userType}

5. UserSyncService.java:
   - syncUser(Map<String, Object> userInfo):
     → Extract uaepass_uuid (from "uuid" field)
     → Find existing user by uaepass_uuid in DB
     → If not found: check by idn (for SOP2/SOP3 only — field "idn") for auto-linking
     → If still not found: create new user
     → Encrypt idn before storing: use pgcrypto pgp_sym_encrypt via native query
       (key from env var IDN_ENCRYPTION_KEY)
     → Update: fullnameEN, fullnameAR, email, mobile, userType, acr on every login
     → Log to audit_log: action="LOGIN", entity_type="USER", entity_id={uuid}
     → Return saved User entity
   - IMPORTANT: userType SOP1 = visitor (fewer attributes), SOP2 = resident, SOP3 = citizen

6. SessionAuthFilter.java (OncePerRequestFilter):
   - Read UAEPASS_SESSION cookie
   - Look up session_token in user_sessions table
   - Check session not expired (token_expires > NOW())
   - Update last_active = NOW()
   - Set SecurityContextHolder with authenticated UsernamePasswordAuthenticationToken
   - Set request attribute "currentUser" = User entity
   - If no valid session: return 401 JSON (don't redirect — Angular handles redirect)

7. UserLinkingService.java (stub — full implementation in Phase 2):
   - autoLink(String uaepassUuid, String idn, String userType): boolean
   - linkManually(UUID spUserId, String uaepassUuid): void
   - unlinkAccount(UUID spUserId): void

8. CryptoUtil.java:
   - encryptAES256(String plaintext, String key): String (returns Base64 encoded ciphertext)
   - decryptAES256(String ciphertext, String key): String
   - Use AES/GCM/NoPadding
   - Used to encrypt access tokens stored in Redis

CONSTRAINTS:
- redirect_uri sent in /auth/callback token request MUST be byte-for-byte identical to the one registered with UAE PASS and sent in /auth/login — any difference causes "invalid_grant" error
- NEVER log: access tokens, Emirates ID (idn), mobile numbers
- State param: atomic single-use via Redis Lua script — no second use ever
- Token introspection: verify client_id in response matches YOUR client_id
- Test with UAE PASS staging accounts: SOP1, SOP2, SOP3 user types
- Write JUnit tests for: StateService (generate/consume/reuse), UserSyncService (all user types), CryptoUtil
```

---

## PHASE 2 — User Linking
### 🎯 Goal: All three linking scenarios work correctly. UUID is always the primary key.

```
You are implementing the UAE PASS User Linking module for a Spring Boot 3 backend.

CONTEXT (verified from docs.uaepass.ae):
- NEVER use email or mobile as the primary link key — users can change them in UAE PASS
- ALWAYS use uaepass_uuid (returned as "uuid" in userinfo) as the immutable primary key
- For SOP2/SOP3 users: can also use idn (Emirates ID) for initial auto-detection
- For SOP1 users (visitors): only uuid is reliable — no Emirates ID
- Three scenarios: Auto Linking, Manual Linking, Corporate Account linking
- Unlink must be logged in audit_log

EXISTING CODE: Phase 1 is complete. UserLinkingService.java stub exists.

TASK: Implement the full UserLinkingModule in package com.yoursp.uaepass.modules.linking:

1. UserLinkingController.java:
   
   GET /auth/link:
   - Requires active SP session (user already logged into SP with email/password)
   - Generate state with flowType="MANUAL_LINK", userId=currentUser.id
   - Build UAE PASS authorization URL (same as /auth/login)
   - Redirect to UAE PASS

   GET /auth/link/callback?code={code}&state={state}:
   - Consume state from Redis — extract userId and verify flowType="MANUAL_LINK"
   - Exchange code → access token → userinfo (same flow as auth callback)
   - Check: does this uaepass_uuid already belong to another SP user? If yes → 409 Conflict
   - Check: does current SP user already have a different uaepass_uuid linked? If yes → 409 Conflict  
   - Set users.uaepass_uuid = returned uuid for userId from state
   - Log to audit_log: action="LINK", metadata={uaepassUuid, linkedBy="MANUAL"}
   - Return success to Angular

   DELETE /auth/unlink:
   - Requires active session
   - Null out uaepass_uuid on user record
   - Log to audit_log: action="UNLINK", metadata={previousUuid}
   - Invalidate all sessions for this user
   - Return 204

2. AutoLinkingService.java (called from UserSyncService in Phase 1):
   
   tryAutoLink(String incomingUuid, String incomingIdn, String userType):
   - Step 1: SELECT user WHERE uaepass_uuid = incomingUuid → if found, already linked, update attributes
   - Step 2 (SOP2/SOP3 only): if incomingIdn not blank:
       Decrypt all stored idn values and compare (or use DB-level pgcrypto decrypt in WHERE clause)
       If match found → set uaepass_uuid = incomingUuid on that user
       Log: action="AUTO_LINK", metadata={matchedBy="IDN"}
   - Step 3: if nothing found → return null (new user creation handled by UserSyncService)
   - Never auto-link SOP1 users by anything other than uuid

3. ManualLinkingService.java:
   - linkBySession(UUID spUserId, String uaepassUuid): void
     → Validates no conflicts (one SP user ↔ one uaepass_uuid, one-to-one)
     → Updates users table
     → Logs audit event
   - unlinkUser(UUID spUserId): void
     → Sets uaepass_uuid = null
     → Invalidates all sessions in user_sessions table
     → Logs audit event

4. CorporateAccountService.java (stub for now):
   - isAuthorizedRepresentative(UUID userId, String organizationId): boolean
   - linkCorporateAccount(UUID userId, String organizationId): void
   - Comment: "Corporate/organization authorization is managed by the SP post-authentication.
     UAE PASS only provides individual identity. Your SP must define which individuals 
     are authorized to represent an organization."

5. UserLinkingDto classes:
   - LinkStatusResponse: {linked: boolean, linkedAt: String, userType: String}
   - LinkConflictResponse: {error: "ALREADY_LINKED_TO_OTHER_USER" | "USER_ALREADY_HAS_LINK"}

6. Tests:
   - AutoLinkingServiceTest: test SOP1 (uuid only), SOP2 (uuid + idn fallback), SOP3
   - Conflict detection tests: duplicate uuid, user already linked
   - Unlink + session invalidation test

CONSTRAINTS:
- One-to-one mapping only: one SP user can be linked to exactly one UAE PASS uuid
- One uaepass_uuid can only be linked to one SP user
- Never cascade delete users when unlinking — just null out the uuid field
- Audit every link and unlink operation without exception
```

---

## PHASE 3 — Digital Signature (Single + Multiple Documents)
### 🎯 Goal: Users can sign PDFs. LTV applied after every signing. Job state machine tracks everything.

```
You are implementing the UAE PASS Digital Signature module for a Spring Boot 3 backend.

VERIFIED ENDPOINTS (from docs.uaepass.ae):
  SP Token (client_credentials):
    POST https://stg-id.uaepass.ae/trustedx-authserver/oauth/main-as/token
    Body: grant_type=client_credentials
    Auth: Basic base64(client_id:client_secret)
    Scope: urn:safelayer:eidas:sign:process:document (and others assigned to SP)

  Create Signer Process:
    POST https://stg-id.uaepass.ae/trustedx-resources/esignsp/v2/signer_processes
    Auth: Bearer {SP_access_token}
    Content-Type: multipart/form-data
    Parts: "process" (JSON), "document" (PDF binary, optional for multiple-doc flow)

  Sign Document (redirect user):
    GET https://stg-id.uaepass.ae/trustedx-resources/esignsp/v2/ui?signerProcessId={id}
    → User approves on UAE PASS app → callback to finish_callback_url

  Signing Callback (SP receives):
    GET {your_finish_callback_url}?status={finished|failed|failed_documents|canceled}&signer_process_id={id}

  Get Signed Document:
    GET {document[0].url}/content
    Auth: Bearer {SP_access_token}
    → Returns binary PDF

  Delete Document (cleanup):
    DELETE https://stg-id.uaepass.ae/trustedx-resources/esignsp/v2/documents/{docId}
    Auth: Bearer {SP_access_token}

  LTV Configuration (MANDATORY — SOAP call, endpoint from onboarding toolkit):
    SOAP WS-Security UsernameToken (same client_id/client_secret)
    Input: signed PDF bytes
    Output: LTV-enhanced PDF bytes

SIGNING JOB STATES:
  PENDING → INITIATED → AWAITING_USER → CALLBACK_RECEIVED → COMPLETING → SIGNED
                                                           ↓               ↓
                                                        FAILED          LTV_FAILED
                                                        CANCELED
                                                        FAILED_DOCUMENTS
  EXPIRED (set by scheduler if no callback within 60 minutes)

TASK: Build the following in package com.yoursp.uaepass.modules.signature:

1. SpTokenService.java:
   - getSpAccessToken(): String
   - Calls POST /trustedx-authserver/oauth/main-as/token with client_credentials grant
   - Caches result in Upstash Redis: SETEX "sp_sign_token" {expires_in - 60} {token}
   - Returns cached token if not expired
   - Uses WebClient (non-blocking) for the HTTP call

2. SignatureController.java:
   
   POST /signature/initiate (requires active session):
   Body: {fileName: string, fileBase64: string, signatureFieldName: string,
          pageNumber: int, x: int, y: int, width: int, height: int,
          showSignatureImage: boolean}
   - Validates user is authenticated (SOP2 or SOP3 only — SOP1 visitors cannot sign legally binding docs)
   - Decodes base64 PDF → validate it's a real PDF (check %PDF header)
   - Calls SingleDocSignService.initiateSigning(userId, pdfBytes, signParams)
   - Returns: {jobId, signingUrl} where signingUrl = tasks.pending[0].url from UAE PASS

   GET /signature/callback?status={status}&signer_process_id={id}:
   - This is the finish_callback_url registered with UAE PASS
   - status values: finished, failed, failed_documents, canceled
   - Update signing_jobs status to CALLBACK_RECEIVED
   - Enqueue async processing: @Async CompletableFuture call to SignatureCompletionService
   - Return 200 immediately (do NOT block — UAE PASS needs fast response)
   - Redirect user browser to frontend result page

   GET /signature/status/{jobId} (requires active session):
   - Returns current signing job status and download URL if SIGNED
   - Angular polls this every 3 seconds while status is AWAITING_USER or COMPLETING

   GET /signature/download/{jobId} (requires active session):
   - Validates jobId belongs to current user
   - Returns signed PDF from StorageService
   - Content-Type: application/pdf

3. SingleDocSignService.java:
   
   initiateSigning(UUID userId, byte[] pdfBytes, SignParams params): SigningJobDto
   Steps:
   a) Get SP access token via SpTokenService
   b) Build multipart request body:
      - "process" part (JSON):
        {
          "process_type": "urn:safelayer:eidas:processes:document:sign:esigp",
          "labels": [["digitalid", "server", "qualified"]],
          "signer": {
            "signature_policy_id": "urn:safelayer:eidas:policies:sign:document:pdf",
            "parameters": {
              "type": "pades-baseline",
              "signature_field": {
                "name": params.signatureFieldName,
                "location": {
                  "page": params.pageNumber,
                  "llx": params.x, "lly": params.y,
                  "urx": params.x + params.width, "ury": params.y + params.height
                }
              },
              "appearance": {} // empty = hide signature image; populate if showSignatureImage=true
            }
          },
          "ui_locales": ["en_US"],
          "finish_callback_url": "{APP_BASE_URL}/signature/callback",
          "timestamp": {"provider_id": "urn:uae:tws:generation:policy:digitalid"}
        }
      - "document" part: PDF binary with Content-Type application/pdf
   c) POST to /v2/signer_processes → expect 201 Created
   d) Extract: id (signer_process_id), tasks.pending[0].url (signing URL), documents[0].url (doc URL)
   e) Store PDF in StorageService with key "unsigned/{jobId}.pdf"
   f) Insert signing_jobs record: status=INITIATED, signer_process_id, documents JSON, expires_at=NOW()+3600s
   g) Log audit: action="SIGN_INITIATED"
   h) Return {jobId, signingUrl}

4. SignatureCompletionService.java (@Async — runs in background after callback):
   
   completeSign(String signerProcessId, String callbackStatus):
   a) Update job status: CALLBACK_RECEIVED
   b) If callbackStatus != "finished" → update status=FAILED/CANCELED/FAILED_DOCUMENTS, log, return
   c) Get SP access token
   d) GET {documents[0].url}/content → receive signed PDF binary
   e) Store in StorageService: key "signed/{jobId}.pdf"
   f) Update status: COMPLETING
   g) Call LtvService.applyLtv(signedPdfBytes, jobId)
   h) Store LTV-enhanced PDF: key "signed-ltv/{jobId}.pdf"
   i) Update status: SIGNED, ltv_applied=true, completed_at=NOW()
   j) DELETE document from UAE PASS: DELETE /v2/documents/{docId}
   k) Log audit: action="SIGN_COMPLETED"

5. LtvService.java (MANDATORY per docs):
   applyLtv(byte[] signedPdf, UUID jobId): byte[]
   - Makes SOAP call to LTV web service (endpoint URL from env var LTV_SOAP_ENDPOINT)
   - WS-Security UsernameToken header with client_id/client_secret
   - Input: signed PDF in Base64 in SOAP body
   - Output: LTV-enhanced PDF in Base64 from SOAP response
   - If LTV SOAP endpoint not yet available (staging setup): log WARNING and return original PDF
   - Set signing_jobs.ltv_applied=true only if LTV actually succeeded
   - Wrap with Resilience4j circuit breaker (open after 3 failures, half-open after 30s)

6. MultipleDocSignService.java:
   initiateMultiDocSigning(UUID userId, List<MultiDocRequest> docs): SigningJobDto
   - Same flow as single doc but:
     - Upload each document as separate PDF parts
     - "documents" array in process JSON references all uploaded doc IDs
     - documents array in signing_jobs JSONB tracks all {name, inputKey, docId, outputUrl, signedKey}
   
   completeMultiDocSign(String signerProcessId, String callbackStatus):
   - If status="failed_documents": download whichever docs have signed URLs, mark others as failed
   - Download each signed document individually from their respective /content URLs
   - Apply LTV to each signed document
   - Update job with per-document status

7. SigningJobScheduler.java (@Scheduled):
   - Every 5 minutes: UPDATE signing_jobs SET status='EXPIRED' 
     WHERE status IN ('INITIATED','AWAITING_USER') AND expires_at < NOW()
   - Log all expired jobs

8. SignatureVerificationService.java:
   verifySignature(byte[] signedPdf): VerificationResult
   - SOAP call to UAE PASS Verification API (endpoint from env var SIGNATURE_VERIFY_SOAP_ENDPOINT)
   - WS-Security UsernameToken header
   - VerifyRequest Profile="urn:safelayer:tws:dss:1.0:profiles:pdf:1.0:verify"
   - Parse VerifyResponse: ResultMajor (Success/Failure), ResultMinor, SignerIdentity, SigningTime
   - Return VerificationResult {valid, signerName, signingTime, certificateInfo}
   POST /signature/verify (internal endpoint, require SP auth header):
   - Accepts PDF upload → returns VerificationResult

CONSTRAINTS:
- NEVER process the callback inline — always async (@Async)
- finish_callback_url must be publicly reachable (not localhost) — use ngrok for local testing
- If signing the same document twice: include unique "name" field in process_type request body
- SOP1 visitors: block signing initiation with clear error message
- All UAE PASS API calls: log request/response (mask any tokens in logs to first 8 chars)
- Resilience4j circuit breaker on all external calls (SP token, create process, get document)
- Test the full state machine transitions with mocked UAE PASS responses
```

---

## PHASE 4 — eSeal Module (PAdES + CAdES)
### 🎯 Goal: Server-side organizational sealing for PDF and non-PDF documents.

```
You are implementing the UAE PASS eSeal module for a Spring Boot 3 backend.

CONTEXT (verified from docs.uaepass.ae):
- eSeal is a single synchronous SOAP-based web service call (not REST)
- PAdES = for PDF documents (Profile: urn:safelayer:tws:dss:1.0:profiles:pades:1.0:sign)
- CAdES = for non-PDF/binary documents (Profile: urn:safelayer:tws:dss:1.0:profiles:cmspkcs7sig:1.0:sign)
- Authentication: WS-Security UsernameToken with client_id as Username, client_secret as Password
- Response contains: Base64-encoded sealed document in dss:Base64Data element
- Maximum one eSeal per entity per document; a document can carry up to 2 eSeals from 2 different entities
- PAdES: MimeType="application/pdf" in response
- CAdES: returns PKCS#7 CMS detached signature (SignatureType: urn:etsi:ts:101733)
- eSeal SOAP endpoint URL provided by UAE PASS onboarding team (store in env var ESEAL_SOAP_ENDPOINT)
- KeySelector/Name: CN={eSeal Certificate CN}, O={org}, L={city}, C=AE (from your eSeal certificate)
- No user interaction required — fully server-to-server

VERIFIED SOAP STRUCTURES (from docs.uaepass.ae):

PAdES Request:
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <soapenv:Header>
    <wsse:Security soapenv:actor="http://schemas.xmlsoap.org/soap/actor/next"
                   soapenv:mustUnderstand="1"
                   xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
      <wsse:UsernameToken>
        <wsse:Username>{client_id}</wsse:Username>
        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">{client_secret}</wsse:Password>
      </wsse:UsernameToken>
    </wsse:Security>
  </soapenv:Header>
  <soapenv:Body>
    <SignRequest xmlns="http://www.docs.oasis-open.org/dss/2004/06/oasis-dss-1.0-core-schema-wd-27.xsd"
                 Profile="urn:safelayer:tws:dss:1.0:profiles:pades:1.0:sign"
                 RequestID="{random 20-char hex string}">
      <OptionalInputs>
        <KeySelector>
          <ns1:KeySelector xmlns:ns1="http://www.safelayer.com/TWS">
            <ns1:Name Format="urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName">{ESEAL_CERT_SUBJECT_NAME}</ns1:Name>
            <ns1:KeyUsage>nonRepudiation</ns1:KeyUsage>
          </ns1:KeySelector>
        </KeySelector>
      </OptionalInputs>
      <InputDocuments>
        <Document>
          <Base64Data MimeType="application/pdf">{base64 encoded PDF}</Base64Data>
        </Document>
      </InputDocuments>
    </SignRequest>
  </soapenv:Body>
</soapenv:Envelope>

CAdES Request: same structure but:
- Profile="urn:safelayer:tws:dss:1.0:profiles:cmspkcs7sig:1.0:sign"
- Add inside OptionalInputs: <SignatureType xsi:type="xsd:anyURI">urn:etsi:ts:101733</SignatureType>
- Add: <EnvelopingSignature/>
- Base64Data: no MimeType attribute

PAdES Response extracts from: /SignResponse/OptionalOutputs/DocumentWithSignature/XMLData/Base64Data (the sealed PDF)
CAdES Response extracts from: /SignResponse/SignatureObject/Base64Signature (the PKCS#7 signature)

eSeal Verification Request:
- Profile="urn:safelayer:tws:dss:1.0:profiles:cmspkcs7sig:1.0:verify" (for CAdES)
- Profile="urn:safelayer:tws:dss:1.0:profiles:pdf:1.0:verify" (for PAdES)
- Body: VerifyRequest with Base64Data of sealed document

TASK: Build the following in package com.yoursp.uaepass.modules.eseal:

1. ESealSoapClient.java:
   - Low-level SOAP client using Spring-WS or raw HttpClient + XML string building
   - Method: String executeSoapRequest(String soapEndpoint, String soapBody, String soapAction)
   - Builds full SOAP envelope with WS-Security UsernameToken header
   - Sends HTTP POST with Content-Type: text/xml; charset=utf-8
   - Returns raw XML response string
   - Logs: correlationId, requestId, response ResultMajor (never log base64 content)
   - Timeout: 30 seconds connect, 60 seconds read
   - Wrapped in Resilience4j circuit breaker "eseal-circuit-breaker" (open after 3 failures)

2. PadesESealService.java:
   sealPdf(byte[] pdfBytes, String requestedByUserId): ESealResult
   a) Generate random RequestID (20 hex chars)
   b) Base64-encode pdfBytes
   c) Build PAdES SOAP request XML (template above, substituting base64 content + RequestID)
   d) Call ESealSoapClient.executeSoapRequest(ESEAL_SOAP_ENDPOINT, body, "sign")
   e) Parse XML response:
      - Check dss:ResultMajor contains "Success"
      - Extract Base64Data from DocumentWithSignature/XMLData/Base64Data
      - Decode from base64 → sealed PDF bytes
   f) Insert eseal_jobs record: seal_type=PADES, status=SEALED
   g) Store sealed PDF in StorageService: key "eseal/{jobId}.pdf"
   h) Log audit: action="ESEAL_PDF"
   i) Return ESealResult {jobId, sealedPdfBytes, requestId}

3. CadesESealService.java:
   sealDocument(byte[] documentBytes, String requestedByUserId): ESealResult
   - Same pattern as PAdES but:
     - CAdES profile + SignatureType + EnvelopingSignature in request
     - Response: extract dss:Base64Signature (this is the PKCS#7 signature, not the document itself)
     - Store both original document + signature file: "eseal/{jobId}.bin" + "eseal/{jobId}.p7s"
     - Record in eseal_jobs: seal_type=CADES

4. ESealVerificationService.java:
   verifyPadesESeal(byte[] sealedPdf): ESealVerifyResult
   - SOAP VerifyRequest with Profile="urn:safelayer:tws:dss:1.0:profiles:pdf:1.0:verify"
   - Parse: ResultMajor, ResultMinor (ValidSignature_OnAllDocuments = success), SignerIdentity, SigningTime
   
   verifyCadesESeal(byte[] sealedDoc, byte[] signature): ESealVerifyResult
   - Profile="urn:safelayer:tws:dss:1.0:profiles:cmspkcs7sig:1.0:verify"

5. ESealController.java:
   
   POST /eseal/pdf (requires active session + admin/service role):
   - Accept multipart PDF upload
   - Call PadesESealService.sealPdf()
   - Return: {jobId, downloadUrl}
   
   POST /eseal/document (requires active session + admin/service role):
   - Accept multipart file upload (any type)
   - Call CadesESealService.sealDocument()
   - Return: {jobId, documentDownloadUrl, signatureDownloadUrl}
   
   POST /eseal/verify:
   - Accept multipart sealed document
   - Auto-detect: if PDF use PAdES verify, else use CAdES verify
   - Return: {valid, signerName, signingTime, certificateInfo}
   
   GET /eseal/download/{jobId}:
   - Return sealed document bytes from StorageService

6. ESealErrorCodeMapper.java:
   - Map UAE PASS eSeal ResultMinor codes to human-readable messages:
     "urn:oasis:names:tc:dss:1.0:resultminor:invalid:IncorrectSignature" → "Invalid eSeal signature"
     "urn:oasis:names:tc:dss:1.0:resultminor:InvalidSignatureTimestamp" → "Invalid timestamp"
     (handle all common ResultMinor values)

7. application-staging.yml additions:
   uaepass.eseal:
     soap-endpoint: ${ESEAL_SOAP_ENDPOINT}
     cert-subject-name: ${ESEAL_CERT_SUBJECT_NAME}

CONSTRAINTS:
- Never log base64 document content — only log jobId, requestId, resultMajor
- Circuit breaker is mandatory — eSeal SOAP can be slow/unavailable
- File size validation: reject PDFs > 10MB (free tier storage constraint)
- Resilience4j: on circuit open, throw ESealUnavailableException with friendly message
- Write integration test with mock SOAP server (WireMock) for both PAdES and CAdES flows
```

---

## PHASE 5 — Hash Signing
### 🎯 Goal: Sign documents by sending only the hash. Docker SDK integrated as sidecar.

```
You are implementing the UAE PASS Hash Signing module for a Spring Boot 3 backend.

CONTEXT (verified from docs.uaepass.ae):
- UAE PASS signs the SHA-256 hash of document and returns it in PKCS#1 format
- SP embeds the signed hash back into the document in PKCS#7 format
- The Hash Signing Docker image (ZIP) is provided by UAE PASS onboarding team — it must be running as a sidecar
- Hash Signing uses a SEPARATE OAuth endpoint: https://stg-id.uaepass.ae/trustedx-authserver/oauth/hsign-as
- Single doc: digests_summary = SHA-256 hash of the prepared document
- Bulk doc: digests_summary = SHA-256(digest1_bytes + digest2_bytes + ... + digestN_bytes) concatenated
- signProp parameter format: "1:[x, y, width, height]" (page:coordinates)
- Error 412 Precondition Failed = txId already used — always generate a fresh txId per attempt
- The SDK runs locally and is called via HTTP on port 8081

HASH SIGNING FLOW (3 steps per docs):
  Step 1 — Start the Process (call Docker SDK):
    POST http://localhost:8081/start
    → Returns: txId, sign_identity_id, digest (SHA-256 of prepared PDF with ByteRange reservation)

  Step 2 — Initiate Signing Process (build auth URL + redirect user):
    Authorization URL: https://stg-id.uaepass.ae/trustedx-authserver/oauth/hsign-as
    Params: response_type=code, client_id, redirect_uri, state,
            scope=urn:uae:digitalid:backend_api:hash_signing urn:safelayer:eidas:sign:identity:use:server,
            digests_summary={SHA256_hash}, digests_summary_algorithm=SHA256,
            sign_identity_id={from step 1},
            signProp={page:coordinates}

  Step 3 — Sign PDF Document (after callback):
    a) Exchange authorization_code → access_token (via /idshub/token)
    b) POST http://localhost:8081/sign with:
       - X-SIGN-ACCESSTOKEN: {access_token}
       - txId, sign_identity_id
    → Returns: PKCS#1 signed hash bytes
    c) Docker SDK embeds signature into reserved ByteRange → returns final PKCS#7 signed PDF

TASK: Build the following in package com.yoursp.uaepass.modules.hashsigning:

1. HashSignSdkClient.java:
   - HTTP client calling the UAE PASS Docker SDK sidecar on http://localhost:8081 (or env var HASH_SDK_URL)
   - startProcess(byte[] pdfBytes, String signProp): HashStartResult
       POST /start → returns {txId, sign_identity_id, digest (hex SHA-256)}
   - signDocument(String txId, String signIdentityId, String accessToken): byte[]
       POST /sign with headers X-SIGN-ACCESSTOKEN + body {txId, sign_identity_id}
       → returns final signed PDF bytes
   - Handle 412: throw HashSigningTxIdReusedException (caller must generate new txId)
   - Handle SDK unavailable: throw HashSignSdkUnavailableException

2. HashSigningController.java:

   POST /hashsign/initiate (requires active session):
   Body: {fileName, fileBase64, pageNumber, x, y, width, height}
   - Validates SOP2 or SOP3 only
   - Call SingleHashSignService.initiate()
   - Returns: {jobId, signingUrl}

   GET /hashsign/callback?code={code}&state={state}:
   - Consume state from Redis (flowType="HASH_SIGN")
   - Extract jobId from state
   - Exchange code → access token
   - Call SingleHashSignService.complete(jobId, accessToken)
   - Redirect to frontend with result

   GET /hashsign/status/{jobId}: same pattern as digital signature status

3. SingleHashSignService.java:

   initiate(UUID userId, byte[] pdfBytes, SignParams params): HashSignJobDto
   a) Generate unique txId (UUID)
   b) Call HashSignSdkClient.startProcess(pdfBytes, signProp) → get digest + sign_identity_id
   c) Store: save PDF in storage "hashsign/unsigned/{jobId}.pdf"
   d) Insert signing_jobs: signing_type=HASH, sign_identity_id, status=INITIATED
   e) Generate state: flowType="HASH_SIGN", jobId stored in state metadata
   f) Build hash-signing auth URL:
      https://stg-id.uaepass.ae/trustedx-authserver/oauth/hsign-as
        ?response_type=code
        &client_id={client_id}
        &redirect_uri={APP_BASE_URL}/hashsign/callback
        &state={state}
        &scope=urn:uae:digitalid:backend_api:hash_signing urn:safelayer:eidas:sign:identity:use:server
        &digests_summary={digest_from_sdk}
        &digests_summary_algorithm=SHA256
        &sign_identity_id={sign_identity_id}
        &signProp={pageNumber}:[{x},{y},{width},{height}]
   g) Update job: status=AWAITING_USER
   h) Return {jobId, signingUrl}

   complete(UUID jobId, String accessToken):
   a) Update status: CALLBACK_RECEIVED
   b) Get job details (txId, sign_identity_id, unsigned PDF storage key)
   c) Call HashSignSdkClient.signDocument(txId, sign_identity_id, accessToken)
   d) Store signed PDF: "hashsign/signed/{jobId}.pdf"
   e) Apply LTV via LtvService (mandatory)
   f) Store LTV PDF: "hashsign/signed-ltv/{jobId}.pdf"
   g) Update status: SIGNED, ltv_applied=true
   h) Log audit: action="HASH_SIGN_COMPLETED"

4. BulkHashSignService.java:

   initiateBulk(UUID userId, List<BulkDoc> docs): HashSignJobDto
   a) For each doc: call SDK startProcess → get individual digest
   b) Compute digests_summary = SHA-256(concat(digest1_bytes, digest2_bytes, ...))
      - Convert each hex digest string to bytes, then concatenate, then SHA-256 the whole thing
   c) Build single auth URL with combined digests_summary (single user approval for all docs)
   d) signProp: "1:[x1,y1,w1,h1]|2:[x2,y2,w2,h2]" (one per document, pipe-separated)
   e) Insert signing_jobs with document_count=N, documents JSONB array

   completeBulk(UUID jobId, String accessToken):
   - Get all docs from signing_jobs.documents JSONB
   - For each doc: call signDocument with its txId → get signed PDF
   - Apply LTV to each signed PDF
   - Update each doc entry in JSONB with signedKey

5. docker-compose.yml update:
   - hash-signing-sdk service: add actual health check
   - Spring Boot app: add depends_on with health condition for hash-signing-sdk
   - Add env var: HASH_SDK_URL=http://hash-signing-sdk:8081

CONSTRAINTS:
- txId must be unique per signing attempt — never reuse
- If 412 received: create new signing_job record with new txId, do NOT retry same txId
- Bulk: single user approval covers ALL documents in the batch — make this clear in API response
- LTV is mandatory on hash-signed documents too (same as standard digital signature)
- Test digests_summary calculation for bulk: verify SHA-256 of concatenated byte arrays
```

---

## PHASE 6 — Facial Biometric Confirmation
### 🎯 Goal: Face verification gate protecting sensitive operations. UUID match is the security check.

```
You are implementing the UAE PASS Facial Biometric Transactions Confirmation module for a Spring Boot 3 backend.

CONTEXT (verified from docs.uaepass.ae):
- Uses the SAME /idshub/authorize endpoint as regular authentication
- Must pass "username" param = user's EID, MOBILE, or EMAIL
  - SOP2 (residents): can use EID, MOBILE, or EMAIL
  - SOP1 (verified visitors): can use MOBILE or EMAIL only (no Emirates ID)
  - SOP3 (citizens): can use EID, MOBILE, or EMAIL
- User receives UAE PASS push notification → performs face scan → callback received
- CRITICAL SECURITY: uuid returned in face-verify userinfo MUST match the current session user's uaepass_uuid
- If UUID mismatch: this means a different person's face was scanned — treat as security incident
- Face verification is valid for a configurable window (recommend 15 minutes)
- Purpose of this module: add face verification as second factor before sensitive operations

VERIFIED FLOW:
  1. SP calls GET /face/verify/initiate → returns UAE PASS authorization URL
  2. User is redirected → UAE PASS sends push notification to their phone
  3. User completes face scan on UAE PASS app
  4. UAE PASS redirects to SP: GET /face/verify/callback?code={code}&state={state}
  5. SP exchanges code → access token → calls /idshub/userinfo
  6. SP compares: returned uuid == session user's uaepass_uuid → VERIFIED or SECURITY_INCIDENT

TASK: Build the following in package com.yoursp.uaepass.modules.face:

1. FaceVerifyController.java:

   POST /face/verify/initiate (requires active session):
   Body: {purpose: string, transactionRef: string, usernameType: "EID"|"MOBILE"|"EMAIL"}
   - Validate active session
   - Get current user from session
   - Resolve username value based on usernameType:
     - EID: decrypt user.idn from DB (if userType=SOP1 → reject with 400, cannot use EID)
     - MOBILE: user.mobile
     - EMAIL: user.email
   - Generate state: flowType="FACE_VERIFY", userId, purpose, transactionRef
   - Build authorization URL:
     https://stg-id.uaepass.ae/idshub/authorize
       ?response_type=code
       &client_id={client_id}
       &redirect_uri={APP_BASE_URL}/face/verify/callback
       &scope=openid urn:uae:digitalid:profile:general
       &state={state}
       &acr_values={face-specific acr_values from env var FACE_ACR_VALUES}
       &username={resolved username value}
       &ui_locales=en
   - Insert face_verifications record: status=PENDING, purpose, transactionRef
   - Return: {verificationId, authorizationUrl}

   GET /face/verify/callback?code={code}&state={state}&error={error}:
   - If error present: update verification status=FAILED, redirect to frontend error page
   - Consume state (flowType must be "FACE_VERIFY" — reject any other)
   - Extract userId and verificationId from state
   - Exchange code → access token → GET /idshub/userinfo
   - CRITICAL CHECK: compare response.uuid == users.uaepass_uuid for userId
     - If MATCH: update face_verifications: status=VERIFIED, uuid_match=true, verified_at=NOW()
       Log audit: action="FACE_VERIFY_SUCCESS", metadata={purpose, transactionRef}
     - If MISMATCH: update face_verifications: status=FAILED, uuid_match=false
       Log SECURITY_INCIDENT to audit_log with metadata={expected_uuid, received_uuid, ip, purpose}
       Return 403 to user — do NOT tell them WHY (security)
   - Redirect to frontend with result

   GET /face/verify/status/{verificationId} (requires active session):
   - Returns current status for polling from Angular

2. FaceVerifiedGuard.java (Spring Security filter / custom annotation):
   
   @FaceVerified annotation:
   - Mark controller methods that require recent face verification
   
   FaceVerifiedFilter.java (OncePerRequestFilter):
   - When route is annotated @FaceVerified:
     - Check face_verifications table for current userId:
       SELECT * FROM face_verifications
       WHERE user_id = :userId AND status = 'VERIFIED' AND uuid_match = true
       AND verified_at > NOW() - INTERVAL '15 minutes'
       ORDER BY verified_at DESC LIMIT 1
     - If found: allow request
     - If not found: return 403 {error: "FACE_VERIFICATION_REQUIRED", verifyUrl: "/face/verify/initiate"}
   
   Apply @FaceVerified to these example endpoints:
   - POST /signature/initiate (high-value signing)
   - POST /eseal/pdf (organizational sealing)
   - DELETE /auth/unlink (unlinking identity)

3. FaceVerifyDto classes:
   - FaceVerifyInitiateRequest: {purpose, transactionRef, usernameType}
   - FaceVerifyInitiateResponse: {verificationId, authorizationUrl, expiresInSeconds: 300}
   - FaceVerifyStatusResponse: {verificationId, status, purpose, verifiedAt}

4. application-staging.yml additions:
   uaepass.face:
     acr-values: ${FACE_ACR_VALUES}   # provided by UAE PASS onboarding team
     verification-window-minutes: 15

5. SecurityIncidentService.java:
   - logSecurityIncident(String type, UUID userId, Map<String, Object> details)
   - Inserts into audit_log with action="SECURITY_INCIDENT"
   - Sends alert notification (stub — log to console in staging, hook to email/Slack in prod)

CONSTRAINTS:
- UUID match check is non-negotiable — no face verification is valid without it
- Never reveal "UUID mismatch" in the error response — generic 403 only
- For SOP1 users trying to use EID as username: return clear 400 error before initiating
- State must carry verificationId so callback can update the correct record
- Face verification expires after 15 minutes — enforce this in both DB query and application logic
- Write test: UUID match success, UUID mismatch (security incident), expired verification, SOP1 EID rejection
```

---

## PHASE 7 — Web Registration + Security Hardening + Go-Live Prep
### 🎯 Goal: Web registration done. All security controls applied. Ready for UAE PASS Assessment.

```
You are finalizing the UAE PASS integration for production readiness. Two parts: Web Registration + Security Hardening.

PART A — WEB REGISTRATION (Private Organizations only, per docs.uaepass.ae)

CONTEXT:
- Web Registration allows net-new users to create a UAE PASS account from within your SP app
- Available for Private Organizations only (not Government entities)
- Same 3-step flow as auth: Access Code → Access Token → User Information
- After registration: user's brand-new UAE PASS uuid is immediately linked to a new SP account

TASK (package com.yoursp.uaepass.modules.webreg):

1. WebRegController.java:
   
   GET /auth/register:
   - Gate this endpoint: only accessible if SP is configured as Private Organization
     (check env var SP_TYPE=PRIVATE — reject with 403 if SP_TYPE=GOVERNMENT)
   - Generate state with flowType="WEB_REG"
   - Build registration authorization URL (same authorize endpoint but with registration-specific scope)
   - Redirect to UAE PASS registration flow

   GET /auth/register/callback?code={code}&state={state}:
   - Consume state (flowType must be "WEB_REG")
   - Exchange code → access token → userinfo
   - This is a brand-new UAE PASS user — their uuid will not exist in your DB
   - Create new user record (UserSyncService.syncUser handles this)
   - Mark user as registered via web registration in audit_log
   - Issue SP session cookie (same as normal login callback)
   - Redirect to onboarding/welcome page

---

PART B — SECURITY HARDENING

TASK: Apply the following security controls to the existing codebase:

1. Input Validation (apply to all existing controllers):
   - Add @Valid + Bean Validation annotations to all request body DTOs
   - File upload endpoints: validate file is real PDF (%PDF magic bytes check)
   - Filename sanitization: strip path separators, null bytes, special chars
   - Max file size: 10MB (configure in Spring: spring.servlet.multipart.max-file-size=10MB)

2. Security Headers (SecurityConfig.java):
   Add to all responses via Spring Security headers configuration:
   - X-Content-Type-Options: nosniff
   - X-Frame-Options: DENY
   - Strict-Transport-Security: max-age=31536000; includeSubDomains (HTTPS only)
   - Content-Security-Policy: default-src 'self'; script-src 'self'; frame-ancestors 'none'
   - Cache-Control: no-store (for API responses — prevents browser caching of sensitive data)

3. Rate Limiting (RateLimitFilter.java using Upstash Redis):
   Implement sliding window rate limiter:
   - Key pattern: "ratelimit:{endpoint_key}:{identifier}"
   - /auth/login: 10 requests per IP per minute
   - /auth/callback: 5 requests per IP per minute  
   - /auth/register: 3 requests per IP per 5 minutes
   - /signature/initiate: 20 requests per userId per hour
   - /eseal/*: 50 requests per userId per hour
   - Algorithm: Redis ZADD + ZREMRANGEBYSCORE (sliding window, ~3 Redis commands per check)
   - On rate limit exceeded: return 429 with Retry-After header
   - Budget impact: ~500 extra Redis commands/day — within Upstash free tier

4. Log Masking (LogMaskingConverter.java for Logback):
   - Never log: access_token, client_secret, idn (Emirates ID), full mobile numbers
   - Mask tokens: show first 8 chars + "..." (e.g., "a1b2c3d4...")
   - Mask mobile: show last 4 digits only (e.g., "***1234")
   - Mask Emirates ID: show "EID:[REDACTED]" in all logs

5. Session Security improvements:
   - Add user_sessions.ip_address check: warn (don't block) if session used from different IP
   - Session token rotation: generate new session token after sensitive operations (signing, face verify)
   - Absolute session expiry: delete sessions older than 24 hours regardless of activity

6. Sensitive Data at Rest (Supabase):
   - Verify idn is stored encrypted (pgcrypto pgp_sym_encrypt) — audit all places where idn is written
   - Add IDN_ENCRYPTION_KEY rotation plan (comment in code: how to rotate without downtime)
   - Ensure user_sessions.uaepass_token_ref is only a Redis key reference, never the actual token

7. PDPL Compliance Endpoints:
   
   DELETE /users/me/data (requires active session + face verification @FaceVerified):
   - Delete: user_sessions, face_verifications, signing_jobs records for this user
   - Anonymize: users record (null out personal fields, keep id for audit trail integrity)
   - Preserve: audit_log entries (required for legal/compliance — cannot delete)
   - Log: action="USER_DATA_DELETION_REQUEST"
   - Return: confirmation with list of what was deleted
   
   GET /users/me/data-export (requires active session):
   - Return JSON export of all data held for the current user
   - Include: profile fields, signing history (without PDF content), face verify history
   - Do NOT include: raw signed PDFs, decrypted Emirates ID in response

8. Pre-Assessment Checklist endpoint (internal only, requires X-Internal-Key header):
   GET /internal/assessment-checklist:
   Returns JSON with pass/fail for each UAE PASS assessment requirement:
   - auth_oidc_flow: does /auth/login → /auth/callback → /auth/me work end-to-end?
   - state_single_use: is state consumed atomically?
   - ltv_applied: are all signing_jobs records showing ltv_applied=true?
   - uuid_primary_key: are all users linked by uaepass_uuid?
   - face_uuid_match: is face verify UUID comparison implemented?
   - sessions_httponly: are session cookies httpOnly?
   - idn_encrypted: spot-check that idn column values start with pgcrypto prefix?
   - tokens_not_logged: (manual check — flag as "MANUAL_VERIFICATION_REQUIRED")
   - audit_log_populated: is audit_log table receiving entries?
   This endpoint is your self-assessment tool before UAE PASS review.

9. README.md update: add section "UAE PASS Assessment Checklist" documenting all items UAE PASS will review

CONSTRAINTS:
- Rate limiter must never block legitimate users — use generous limits for staging
- Data deletion must be irreversible — add "are you sure?" confirmation step in API (require body: {"confirm": "DELETE_MY_DATA"})
- Security headers: test with https://securityheaders.com after deployment
- Web registration is Private Org only — enforce this strictly, not just as a comment
- Write integration test that walks through the full pre-assessment checklist automatically
```

---

## APPENDIX — Environment Variables Reference

```
# UAE PASS Credentials
UAEPASS_CLIENT_ID=sandbox_stage
UAEPASS_CLIENT_SECRET=sandbox_stage
UAEPASS_BASE_URL=https://stg-id.uaepass.ae

# App URLs
APP_BASE_URL=https://your-railway-app.up.railway.app
FRONTEND_URL=https://your-angular-app.netlify.app

# Database (Supabase)
SUPABASE_DB_URL=jdbc:postgresql://db.xxxx.supabase.co:5432/postgres?sslmode=require
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=your_supabase_password

# Redis (Upstash)
UPSTASH_REDIS_URL=redis://:your_password@your-upstash-endpoint.upstash.io:6379

# Security
IDN_ENCRYPTION_KEY=your_32_byte_hex_key_for_aes256
SESSION_COOKIE_DOMAIN=your-railway-app.up.railway.app

# eSeal (from UAE PASS onboarding)
ESEAL_SOAP_ENDPOINT=https://eseal-stg.uaepass.ae/...    # exact URL from onboarding team
ESEAL_CERT_SUBJECT_NAME=CN=YourOrg eSeal, O=Your Org, L=Dubai, C=AE

# Hash Signing
HASH_SDK_URL=http://hash-signing-sdk:8081   # Docker sidecar

# LTV
LTV_SOAP_ENDPOINT=https://ltv-stg.uaepass.ae/...    # exact URL from onboarding toolkit

# Signature Verification
SIGNATURE_VERIFY_SOAP_ENDPOINT=https://verify-stg.uaepass.ae/...

# Face Verification
FACE_ACR_VALUES=urn:safelayer:tws:policies:authentication:level:face   # exact value from UAE PASS team

# SP Type
SP_TYPE=BOTH   # GOVERNMENT, PRIVATE, or BOTH

# Internal
INTERNAL_API_KEY=your_random_32_char_key
```

---

## APPENDIX — Phase Order & Dependencies

```
Phase 0 (Foundation) 
  └── Phase 1 (Auth) — depends on Phase 0
       └── Phase 2 (Linking) — depends on Phase 1 UserSyncService
            └── Phase 3 (Digital Signature) — depends on Phase 1 session + SpTokenService
                 └── Phase 4 (eSeal) — independent of Phase 3, but same infrastructure
                      └── Phase 5 (Hash Signing) — needs Docker SDK from UAE PASS team
                           └── Phase 6 (Face Biometric) — depends on Phase 1 sessions + Phase 2 UUID linking
                                └── Phase 7 (Web Reg + Hardening) — wraps up everything
```

**Critical path items to request from UAE PASS team upfront (Day 1):**
1. Staging `client_id` and `client_secret`
2. eSeal SOAP endpoint URL + certificate
3. Hash Signing Docker image ZIP
4. LTV SOAP endpoint URL
5. Signature Verification SOAP endpoint URL
6. Face biometric `acr_values` string
7. Signing scopes to be assigned to your SP client in TrustedX