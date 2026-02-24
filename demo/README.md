# UAE PASS Service Provider — Backend

Production-grade Spring Boot 3.2.x backend for UAE PASS integration (authentication, digital signing, e-sealing, face verification).

## Tech Stack

| Layer       | Technology                        |
|-------------|-----------------------------------|
| Runtime     | Java 17, Spring Boot 3.2.5        |
| Build       | Maven                             |
| Database    | Supabase (PostgreSQL)             |
| Cache       | Upstash (Redis, Lettuce driver)   |
| Logging     | Logback + Logstash JSON encoder   |
| CI/CD       | GitHub Actions                    |
| Container   | Docker, docker-compose            |

---

## Prerequisites

- **Java 17** (install via [SDKMAN](https://sdkman.io/) or [Temurin](https://adoptium.net/))
- **Docker** & **Docker Compose**
- **Supabase** project (free tier) — [supabase.com](https://supabase.com)
- **Upstash** Redis instance (free tier) — [upstash.com](https://upstash.com)

---

## Environment Variables

Create a `.env` file in the project root (or export in your shell):

```bash
# Supabase PostgreSQL
export SUPABASE_DB_URL=jdbc:postgresql://db.xxxxxxxxxxxx.supabase.co:5432/postgres
export SUPABASE_DB_USERNAME=postgres
export SUPABASE_DB_PASSWORD=your-supabase-password

# Upstash Redis
export UPSTASH_REDIS_URL=rediss://default:your-token@your-endpoint.upstash.io:6379

# UAE PASS OAuth2 (from SP onboarding)
export UAEPASS_CLIENT_ID=your-client-id
export UAEPASS_CLIENT_SECRET=your-client-secret
```

> **Never commit `.env` to version control.** It is already listed in `.gitignore`.

---

## Running Locally

### Option 1: Maven (direct)

```bash
# Set env vars first (source .env or export individually)
source .env

# Run the app
./mvnw spring-boot:run
```

The app will start on **http://localhost:8080**.

### Option 2: Docker Compose

```bash
# Build and run
docker-compose up --build

# Or in detached mode
docker-compose up -d --build
```

Services:
- **app**: Spring Boot on port `8080`
- **hash-signing-sdk**: Placeholder on port `8081` (replace image with actual SDK from UAE PASS team)

---

## Database Schema

The schema is defined in `src/main/resources/schema.sql` and is **automatically executed on startup** when running with the `staging` profile:

```bash
SPRING_PROFILES_ACTIVE=staging ./mvnw spring-boot:run
```

For production, schema is managed manually — `spring.sql.init.mode=never`.

### Tables

| Table                | Purpose                              |
|----------------------|--------------------------------------|
| `users`              | UAE PASS authenticated users         |
| `oauth_states`       | OAuth2 state parameter tracking      |
| `user_sessions`      | Active session management            |
| `signing_jobs`       | Digital signature workflow tracking   |
| `eseal_jobs`         | E-seal operations                    |
| `face_verifications` | Face biometric verification records  |
| `audit_log`          | Audit trail for all sensitive ops    |

---

## Running Tests

```bash
./mvnw test
```

---

## Health Check

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## Profiles

| Profile   | Usage           | Key Behavior                                    |
|-----------|-----------------|-------------------------------------------------|
| `default` | Local dev       | Console logging, schema init OFF                |
| `staging` | Staging server  | JSON logging, schema.sql executed on startup    |
| `prod`    | Production      | JSON logging (WARN+), schema init OFF           |

Activate a profile:
```bash
SPRING_PROFILES_ACTIVE=staging ./mvnw spring-boot:run
```

---

## Project Structure

```
src/main/java/com/yoursp/uaepass/
├── UaePassApplication.java          # Entry point
├── config/
│   └── SecurityConfig.java          # Security (permitAll for Phase 1)
├── exception/
│   └── GlobalExceptionHandler.java  # 400/500 error handling
├── filter/
│   └── CorrelationIdFilter.java     # Correlation ID per request
├── model/entity/
│   ├── User.java
│   ├── OAuthState.java
│   ├── UserSession.java
│   ├── SigningJob.java
│   ├── EsealJob.java
│   ├── FaceVerification.java
│   └── AuditLog.java
├── repository/
│   └── AuditLogRepository.java
└── service/
    ├── AuditService.java
    └── storage/
        ├── StorageService.java          # Interface
        └── LocalStorageServiceImpl.java # Local FS impl
```

---

## CI/CD

GitHub Actions workflow is at `.github/workflows/ci.yml`. It runs on push to `main`:

1. Checkout → Java 17 setup → `mvn test` → `mvn package` → `docker build`

Add these **GitHub Secrets** to your repository:
- `SUPABASE_DB_URL`
- `SUPABASE_DB_USERNAME`
- `SUPABASE_DB_PASSWORD`
- `UPSTASH_REDIS_URL`
- `UAEPASS_CLIENT_ID`
- `UAEPASS_CLIENT_SECRET`
