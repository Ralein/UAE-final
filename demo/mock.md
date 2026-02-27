# Mock Login Tracking

This document tracks all mock endpoints and bypasses created for local development without actual UAE PASS credentials.

**IMPORTANT: All changes listed here MUST be removed before deploying to production!**

---

## Backend Mock Endpoints

1.  `GET /auth/dev-login`
    *   **Location:** `src/main/java/com/yoursp/uaepass/modules/auth/MockAuthController.java`
    *   **Purpose:** Bypasses UAE PASS authentication. Creates a fake user in the database and sets a valid session cookie.
    *   **Action Required for Prod:** Delete this file entirely. It is guarded by `@Profile("mock")`, but removing it is safest.

## Backend Configuration Changes
1.  **SessionAuthFilter Whitelist:**
    *   **Location:** `src/main/java/com/yoursp/uaepass/modules/auth/SessionAuthFilter.java` (line ~55)
    *   **Purpose:** Added `/auth/dev-login` to the `SKIP_PATHS` whitelist so it doesn't try to look for a session cookie during login.
    *   **Action Required for Prod:** Remove `"/auth/dev-login"` from the `SKIP_PATHS` array.

2.  **SecurityConfig Whitelist:**
    *   **Location:** `src/main/java/com/yoursp/uaepass/config/SecurityConfig.java` (line ~69)
    *   **Purpose:** Added `/auth/dev-login` to the `requestMatchers` permitAll list to bypass Spring Security.
    *   **Action Required for Prod:** Remove `"/auth/dev-login"` from the `requestMatchers` list.

---

## Frontend Mock Files

> These files simulate the entire UAE PASS authentication flow in the Angular frontend so no backend is required.

### New Files (DELETE for production)

| File | Purpose |
|------|---------|
| `frontend/src/app/core/services/mock-auth.service.ts` | Simulates login, session storage (localStorage), profile data, and verification codes. **This is the core mock.** |
| `frontend/src/app/core/guards/auth.guard.ts` | Route guard — redirects unauthenticated users to login. Keep this but wire it to the real auth service. |

### Modified Files (REVERT for production)

| File | What to change |
|------|----------------|
| `frontend/src/app/pages/login/login.ts` | Replace `MockAuthService` with real backend calls to `/auth/login`. Remove simulated delay/states. |
| `frontend/src/app/pages/login/login.html` | Remove multi-step simulation (verifying/approving states). Use real UAE PASS redirect flow instead. |
| `frontend/src/app/pages/dashboard/dashboard.ts` | Replace `MockAuthService.getProfile()` with `HttpClient.get('/auth/me')`. |
| `frontend/src/app/app.routes.ts` | Update guard to use real auth check. |
| `frontend/src/app/app.config.ts` | Ensure `HttpClient` is configured with proper interceptor for real cookies. |

### Mock Data Used

The `MockAuthService` creates this fake user profile:

```json
{
  "firstname": "Ahmed",
  "lastnameEn": "Al Mansoori",
  "fullnameEn": "Ahmed Khalid Al Mansoori",
  "email": "ahmed.mansoori@email.ae",
  "mobile": "971501234567",
  "nationalityEn": "United Arab Emirates",
  "idn": "<user-entered Emirates ID>",
  "userType": "SOP1",
  "uaepassUuid": "uaepass-a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "gender": "Male",
  "dob": "1990-03-15"
}
```

Session data is stored in `localStorage` under the key `uaepass_mock_session`.
