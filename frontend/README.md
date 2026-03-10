# 🇦🇪 UAE PASS SP — Angular Frontend

> The client-side application for the UAE PASS Service Provider integration.  
> Built with **Angular 21** (Zoneless) · **TypeScript 5.9** · **Vitest**

---

## Tech Stack

| Layer            | Technology                        | Version   |
|------------------|-----------------------------------|-----------|
| Framework        | Angular (Standalone Components)   | 21.1.0    |
| Language         | TypeScript (strict mode)          | ~5.9.2    |
| HTTP Client      | `@angular/common/http`            | 21.1.0    |
| Routing          | `@angular/router`                 | 21.1.0    |
| Reactive         | RxJS                              | ~7.8.0    |
| Change Detection | Zoneless (`provideZonelessChangeDetection`) | —  |
| Unit Tests       | Vitest + jsdom                    | 4.0.x     |
| Build Tool       | `@angular/build` (esbuild-based)  | 21.1.4    |
| Package Manager  | npm                               | 10.9.2    |

---

## Project Structure

```
frontend/
├── angular.json              # Angular workspace config
├── package.json              # Dependencies & scripts
├── tsconfig.json             # Root TypeScript config (strict)
├── tsconfig.app.json         # App-specific TS config
├── tsconfig.spec.json        # Test-specific TS config
├── public/                   # Static assets (favicon, etc.)
└── src/
    ├── main.ts               # Bootstrap entry point
    ├── styles.css             # Global styles
    └── app/
        ├── app.ts            # Root AppComponent
        ├── app.html          # Root template
        ├── app.css           # Root styles
        ├── app.config.ts     # Application providers config
        ├── app.routes.ts     # Route definitions
        ├── core/             # Shared singletons (guards, services)
        │   ├── guards/
        │   │   └── auth.guard.ts
        │   └── services/
        │       ├── auth.ts
        │       ├── auth.interceptor.ts
        │       └── mock-auth.service.ts
        └── pages/            # Feature pages (routed components)
            ├── login/
            │   ├── login.ts
            │   ├── login.html
            │   └── login.css
            └── dashboard/
                ├── dashboard.ts
                ├── dashboard.html
                └── dashboard.css
```

---

## Routing

| Path           | Component            | Guard        | Description                              |
|----------------|----------------------|--------------|------------------------------------------|
| `/`            | `LoginComponent`     | —            | Login page; redirects to UAE PASS OAuth   |
| `/dashboard`   | `DashboardComponent` | `authGuard`  | Protected user dashboard after login      |
| `/**`          | —                    | —            | Wildcard redirect → `/` (login)           |

---

## Core Modules

### Guards

| Guard         | File                 | Type            | Behaviour                                                         |
|---------------|----------------------|-----------------|-------------------------------------------------------------------|
| `authGuard`   | `auth.guard.ts`      | `CanActivateFn` | Checks `MockAuthService.isLoggedIn()`. Redirects to `/` if false. |

### Services

| Service              | File                      | Scope        | Purpose                                                              |
|----------------------|---------------------------|--------------|----------------------------------------------------------------------|
| `Auth`               | `auth.ts`                 | `root`       | Placeholder service for production UAE PASS token-based auth.        |
| `MockAuthService`    | `mock-auth.service.ts`    | `root`       | Simulates the full UAE PASS login flow for local dev/testing.        |
| `authInterceptor`    | `auth.interceptor.ts`     | global (fn)  | HTTP interceptor — sets `withCredentials: true` on every request.    |

### `MockAuthService` — Functions

| Method                     | Returns                           | Description                                                        |
|----------------------------|-----------------------------------|--------------------------------------------------------------------|
| `login(emiratesId)`        | `Promise<MockUserProfile>`        | Simulates 1.5s UAE PASS login, stores session in `localStorage`.   |
| `getProfile()`             | `MockUserProfile \| null`          | Reads the current user profile from the stored session.            |
| `getSessionInfo()`         | `{token, loginTime, expiresAt} \| null` | Returns session metadata (mock token, login time, expiry).   |
| `logout()`                 | `void`                            | Clears session from `localStorage`, sets `isLoggedIn` to `false`. |
| `generateVerificationCode()` | `string`                        | Returns a random 4-digit code (for mock OTP/verification UI).     |
| `isLoggedIn` (signal)      | `Signal<boolean>`                 | Reactive read-only signal reflecting current auth state.           |

### `MockUserProfile` Interface

| Field            | Type     | Example                         |
|------------------|----------|----------------------------------|
| `firstname`      | `string` | `Ahmed`                          |
| `lastnameEn`     | `string` | `Al Mansoori`                    |
| `fullnameEn`     | `string` | `Ahmed Khalid Al Mansoori`       |
| `email`          | `string` | `ahmed.mansoori@email.ae`        |
| `mobile`         | `string` | `971501234567`                   |
| `nationalityEn`  | `string` | `United Arab Emirates`           |
| `nationalityAr`  | `string` | `الإمارات العربية المتحدة`       |
| `idn`            | `string` | `784-1990-1234567-1`             |
| `userType`       | `string` | `SOP1` / `SOP2` / `SOP3`        |
| `uaepassUuid`    | `string` | `uaepass-a1b2c3d4-...`          |
| `gender`         | `string` | `Male`                           |
| `dob`            | `string` | `1990-03-15`                     |

---

## Pages

### Login Page (`/`)
- Provides the initial entrance to the application.
- Collects Emirates ID input and triggers `MockAuthService.login()`.
- On success, navigates the user to `/dashboard`.
- In production mode, this page initiates a redirect to the backend `GET /auth/login` endpoint which then redirects the browser to the UAE PASS OIDC authorization URL.

### Dashboard Page (`/dashboard`)
- **Protected** by `authGuard` — only accessible with an active session.
- Displays the authenticated user's profile (name, email, nationality, user type).
- Provides access to actions like document signing, face verification, and logout.
- On logout, clears the session and redirects back to `/`.

---

## Authentication Workflow

```
┌─────────────┐      ┌───────────────┐      ┌──────────────────┐      ┌────────────┐
│ Login Page   │─────▶│ Backend       │─────▶│ UAE PASS          │─────▶│ User's     │
│ (Angular)    │ GET  │ /auth/login   │ 302  │ /idshub/authorize │      │ UAE PASS   │
│              │      │               │      │                  │      │ App        │
└─────────────┘      └───────────────┘      └──────────────────┘      └─────┬──────┘
                                                                            │ User
                                                                            │ authenticates
                                                                            ▼
┌─────────────┐      ┌───────────────┐      ┌──────────────────┐      ┌────────────┐
│ Dashboard    │◀─────│ Backend       │◀─────│ UAE PASS          │◀─────│ Callback   │
│ (Angular)    │ 302  │ /auth/callback│ POST │ /idshub/token     │      │ ?code=...  │
│              │ +    │ Creates       │      │ Returns tokens    │      │ &state=... │
│              │ 🍪   │ httpOnly      │      │                  │      │            │
│              │      │ session cookie│      └──────────────────┘      └────────────┘
└─────────────┘      └───────────────┘

🍪 = UAEPASS_SESSION httpOnly cookie (Secure, SameSite=Strict)
```

### Key Security Notes
- The Angular frontend **never** sees or stores access tokens.
- All token exchange happens server-side via the Spring Boot backend.
- The `authInterceptor` ensures `withCredentials: true` is set on every HTTP call, which tells the browser to include the `httpOnly` session cookie automatically.

---

## Communication with the Backend

| Frontend Action                   | Backend Endpoint                        | Method | Auth        |
|-----------------------------------|-----------------------------------------|--------|-------------|
| Start UAE PASS login              | `/auth/login?redirectAfter=/dashboard`  | `GET`  | None        |
| OAuth callback handler            | `/auth/callback?code=...&state=...`     | `GET`  | None        |
| Get current user profile           | `/auth/me`                              | `GET`  | Session 🍪  |
| Logout                            | `/auth/logout`                          | `POST` | Session 🍪  |
| Link UAE PASS identity             | `/auth/link`                            | `GET`  | Session 🍪  |
| Unlink UAE PASS identity           | `/auth/unlink`                          | `DELETE`| Session 🍪 |
| Initiate document signing          | `/signature/initiate`                   | `POST` | Session 🍪  |
| Poll signing status                | `/signature/status/{jobId}`             | `GET`  | Session 🍪  |
| Download signed PDF                | `/signature/download/{jobId}`           | `GET`  | Session 🍪  |
| eSeal a PDF                       | `/eseal/pdf`                            | `POST` | Session 🍪  |
| Initiate hash signing              | `/hashsign/initiate`                    | `POST` | Session 🍪  |
| Start face verification            | `/face/verify/initiate`                 | `POST` | Session 🍪  |
| Register new UAE PASS user (Private Org) | `/auth/register`                  | `GET`  | None        |
| Delete my data (PDPL)             | `/users/me/data`                        | `DELETE`| Session 🍪 |
| Export my data (PDPL)             | `/users/me/data-export`                 | `GET`  | Session 🍪  |

---

## Build Configurations

| Configuration | Optimization | Source Maps | Output Hashing | Use Case              |
|---------------|:------------:|:-----------:|:--------------:|-----------------------|
| `development` | ❌            | ✅           | ❌              | Local `ng serve`       |
| `production`  | ✅            | ❌           | ✅ (all)        | Deployed build output  |

### Bundle Budgets (Production)

| Type                | Warning | Error |
|---------------------|---------|-------|
| Initial bundle      | 500 kB  | 1 MB  |
| Any component style | 4 kB    | 8 kB  |

---

## TypeScript Configuration

| Option                              | Value    | Reason                              |
|-------------------------------------|----------|-------------------------------------|
| `strict`                            | `true`   | Full type safety                    |
| `noImplicitOverride`                | `true`   | Explicit override keyword            |
| `noImplicitReturns`                 | `true`   | All code paths must return           |
| `noFallthroughCasesInSwitch`        | `true`   | Prevent accidental fall-throughs     |
| `isolatedModules`                   | `true`   | Compatible with esbuild              |
| `target`                            | `ES2022` | Modern browser baseline              |
| `strictTemplates`                   | `true`   | Angular template type checking       |
| `strictInjectionParameters`         | `true`   | DI type safety                       |

---

## Getting Started

### Prerequisites

- **Node.js** 20+ (LTS)
- **npm** 10+
- **Angular CLI** 21.x (`npm i -g @angular/cli`)

### Install & Run

```bash
# Install dependencies
npm install

# Start development server (http://localhost:4200)
ng serve

# Or
npm start
```

### Build for Production

```bash
ng build
# Output → dist/frontend/
```

### Run Unit Tests

```bash
ng test
# Powered by Vitest + jsdom
```

---

## UAE PASS Integration Phases (Frontend Perspective)

| Phase | Name                        | Frontend Work                                                       | Status |
|-------|-----------------------------|---------------------------------------------------------------------|--------|
| 0     | Foundation                  | Angular project scaffold, routing, global config                    | ✅     |
| 1     | Authentication (OIDC)       | Login page, auth guard, session cookie handling, dashboard           | ✅     |
| 2     | User Linking                | Link/Unlink UI on dashboard (calls `/auth/link`, `/auth/unlink`)    | ✅     |
| 3     | Digital Signature           | PDF upload form, signing status polling, signed PDF download         | ✅     |
| 4     | eSeal                       | Admin upload UI for e-sealing documents                              | ✅     |
| 5     | Hash Signing                | Similar to Phase 3 but targets the `/hashsign/*` endpoints           | ✅     |
| 6     | Face Biometric              | Face verify prompt modal before sensitive actions                    | ✅     |
| 7     | Web Reg + Hardening         | Registration page (Private Orgs), PDPL data deletion/export UI      | ✅     |

---

## Environment Configuration

The frontend proxies all API calls to the Spring Boot backend. No environment variables hold secrets — all authentication is cookie-based.

| Setting              | Dev Default                | Production                        |
|----------------------|----------------------------|-----------------------------------|
| Backend API          | `http://localhost:8080`     | Set via environment/proxy          |
| Dev Server Port      | `4200`                     | N/A                               |
| Session Cookie       | Set by backend (`httpOnly`) | Same — browser handles it          |

---

## Related Documentation

| Document                           | Location                              | Description                          |
|------------------------------------|---------------------------------------|--------------------------------------|
| Backend README                     | `demo/README.md`                      | Full backend setup, DB schema, CI/CD |
| Implementation Phases (Prompts)    | `demo/Implementation.md`              | All 8 phases with detailed prompts   |
| About (Project Overview)           | `frontend/about.md`                   | High-level project explanation       |
| Mock Mode Guide                    | `demo/mock.md`                        | Running backend in mock mode         |
| Environment Variables Reference    | `demo/.env.example`                   | All required env vars                |
