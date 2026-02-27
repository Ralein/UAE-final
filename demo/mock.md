# Mock Login Tracking

This document tracks all mock endpoints and bypasses created for local development without actual UAE PASS credentials.

**IMPORTANT: All changes listed here MUST be removed before deploying to production!**

## Mock Endpoints Added

1.  `GET /auth/dev-login`
    *   **Location:** `src/main/java/com/yoursp/uaepass/modules/auth/MockAuthController.java`
    *   **Purpose:** Bypasses UAE PASS authentication. Creates a fake user in the database and sets a valid session cookie.
    *   **Action Required for Prod:** Delete this file entirely. It is guarded by `@Profile("mock")`, but removing it is safest.

## Configuration Changes
1.  **SessionAuthFilter Whitelist:**
    *   **Location:** `src/main/java/com/yoursp/uaepass/modules/auth/SessionAuthFilter.java` (line ~55)
    *   **Purpose:** Added `/auth/dev-login` to the `SKIP_PATHS` whitelist so it doesn't try to look for a session cookie during login.
    *   **Action Required for Prod:** Remove `"/auth/dev-login"` from the `SKIP_PATHS` array.

2.  **SecurityConfig Whitelist:**
    *   **Location:** `src/main/java/com/yoursp/uaepass/config/SecurityConfig.java` (line ~69)
    *   **Purpose:** Added `/auth/dev-login` to the `requestMatchers` permitAll list to bypass Spring Security.
    *   **Action Required for Prod:** Remove `"/auth/dev-login"` from the `requestMatchers` list.
