# Test Strategy — Chubb QA Assessment

## 1. Application Overview

Three-tier insurance claims management app:
- **UI**: Next.js 16 + React (port 3001)
- **BFF Service**: Spring Boot (port 8090)
- **Claims Service**: Spring Boot (port 8080, it's free port)
- **Infrastructure**: Kafka, Keycloak, PostgreSQL, Redis

Domain: Insurance claims — creation, review, approval/rejection lifecycle.

## 2. Highest-Risk Areas

### 2.1 Claim Status State Machine (`ClaimStatus.java`)

The claim lifecycle is enforced by `ClaimStatus.canTransitionTo()`:

**Valid transitions:**
| From | To |
|------|-----|
| SUBMITTED | UNDER_REVIEW, REJECTED |
| UNDER_REVIEW | APPROVED, REJECTED, SUBMITTED |
| APPROVED | CLOSED |
| REJECTED | CLOSED |
| CLOSED | (none — terminal) |

**Observations / risks:**
- ⚠️ `UNDER_REVIEW → SUBMITTED` is a **backward transition** — unusual for claim workflows. Could allow duplicate review. Needs testing to confirm behavior.
- ⚠️ No `SUBMITTED → APPROVED` — forces review first (likely intentional).
- ✅ `CLOSED` is terminal (correct).
- 🚨 **KEY RISK:** Does the use case layer actually enforce `canTransitionTo()`? If not, any status can be forced via API.
- 🚨 **Race condition risk:** Concurrent status updates — is there optimistic locking?

### 2.2 Claim Domain (`Claim.java`)

**Validation rules (constructor):**
- All fields required (non-null)
- `incidentDate` not in future
- `description`: 10–1000 chars (trimmed)
- `incidentLocation`: 5–200 chars (trimmed)
- `claimAmount`: > 0 and <= 1,000,000

**State transitions:**
- `updateStatus(newStatus)` enforces `ClaimStatus.canTransitionTo()`
- Emits `ClaimStatusChanged` domain event

**Risks / bugs identified:**

🔴 **Bug: `changedBy` is wrong in audit trail**
- Line 112–118: `ClaimStatusChanged` event uses `this.userId` (claim owner) for `changedBy`
- Should be the acting admin's ID
- `updateStatus()` doesn't even receive the admin's ID
- Impact: audit trail cannot identify which admin actioned a claim
- Test: assert current behavior (documenting bug), then fix

🟠 **`reconstitute()` bypasses validation**
- Intentional for persistence, but means invalid DB data loads silently
- Worth documenting in strategy

🟠 **Timezone risk in `LocalDate.now()`**
- Server timezone vs user timezone could reject valid "today" incidents
- Boundary test: incidentDate = today

🟡 **No `equals()` / `hashCode()`**
- Object identity only

🟡 **Minor: two `Instant.now()` calls in constructor**
- `createdAt` and event `occurredAt` may differ

**Tests to write:** 25–28 tests covering validation, transitions, events, and the `changedBy` bug.

**Tests to write:**
1. Valid transitions → expect 200
2. Invalid transitions → expect 400
3. Backward transition (`UNDER_REVIEW → SUBMITTED`) → document behavior
4. Terminal state (`CLOSED → anything`) → expect 400
5. Concurrent updates → check for data corruption

### 2.3 UpdateClaimStatusUseCase

**Responsibilities:**
- Look up admin user → enforce ADMIN role
- Look up claim → not-found handling
- Call `claim.updateStatus(newStatus)` → enforces transition rules
- Save updated claim
- Publish events (CDC or direct, based on config flag)
- Increment metrics counter

**Risks / bugs identified:**

🔴 **CONFIRMED BUG: `changedBy` is never passed to `Claim.updateStatus()`**
- Line 48: `claim.updateStatus(command.newStatus())` — no admin ID
- Use case HAS `command.adminUserId()` (line 36, 65) — available but not passed
- Result: `ClaimStatusChanged.changedBy` = claim owner, not acting admin
- Contract violation: `claim-events-api.yml` documents `changedBy` as "The admin user who changed the status"
- Impact: audit trail cannot identify which admin actioned a claim
- Test: assert current (incorrect) behavior; fix later

🟠 **Observation: `cdcEnabled` defaults to `true`**
- CDC path is primary; direct-publish path only used if explicitly disabled
- Setup guide runs direct-publish? (Confirm actual app config)
- Risk: events silently missing if CDC not running

🟠 **Observation: Events published within transaction**
- `claim.getDomainEvents().forEach(eventPublisher::publishEvent)` runs inside `@Transactional`
- If transaction rolls back, event may have already published
- Consider `@TransactionalEventListener(phase = AFTER_COMMIT)` — worth noting

🟡 **No null checks on `command`**
- Null command or null adminUserId → NPE (would be 500, not 400)

**Tests to write:** 10 tests (RBAC, not-found, valid/invalid transitions, changedBy bug, CDC toggle, metrics).

### 2.4 CreateClaimUseCase

**Responsibilities:**
- Verify user exists
- Delegate creation + validation to `Claim` domain constructor
- Save to repository
- Publish events (CDC or direct)
- Increment metrics

**Risks / observations:**

🟠 **Null field inputs cause `NullPointerException` (500)**
- If `command.incidentDate()` etc. is null → `Claim` constructor's `Objects.requireNonNull` throws NPE
- Expected: 400 Bad Request, not 500
- Worth a test

🟠 **CDC toggle: events silently dropped if CDC enabled but Debezium down**
- `app.events.cdc-enabled` defaults to `true`
- Setup guide runs... (confirm actual config)
- Risk: claims created but no event published

🟠 **Events published within transaction**
- Same as UpdateClaimStatusUseCase
- Consider AFTER_COMMIT listener

🟡 **No duplicate claim detection**
- Same user can submit identical claims twice

🟡 **No explicit validation before Claim construction**
- Relies on constructor's validation + NPEs
- Could provide cleaner error messages

**Tests to write:** 10 tests (user not found, valid creation, event publishing, CDC toggle, null handling, metrics).

### 2.5 Controllers and Security (Claims Service)

#### ClaimController.java

**Endpoints:** POST /api/claims, GET /api/claims/{id}, GET /api/claims

**Findings:**
- 🟠 No `@PreAuthorize` — relies on use case-level userId filtering for cross-user protection
- 🟡 `BigDecimal.valueOf(request.getClaimAmount())` — double→BigDecimal conversion, precision risk
- 🟡 Malformed UUID in JWT subject → 500

#### AdminClaimsController.java

**Endpoints:** GET /api/admin/claims, PATCH /api/admin/claims/{id}/status

**Findings:**
- ✅ `@PreAuthorize("hasRole('ADMIN')")` on both endpoints
- 🔴 **CONFIRMS changedBy bug**: `adminUserId` IS passed to use case (line 84), but use case doesn't forward to `claim.updateStatus()`
- 🟡 N+1 user lookups in `toAdminClaimResponse()`
- 🟠 `ClaimStatus.valueOf()` on invalid status value → 500 (should be 400)

#### SecurityConfig.java (Claims Service)

**Findings:**
- ✅ CSRF disabled, stateless sessions (correct for JWT API)
- ✅ Keycloak realm_access roles mapped to Spring authorities
- 🚨 `anyRequest().permitAll()` — anything outside `/api/**` and `/actuator/**` is public
- 🟠 Debug-level JWT logging could leak tokens in logs
- 🟡 No role hierarchy (ADMIN doesn't inherit CLAIMANT)
- 🟡 No CORS config (verify BFF handles it)

**Tests to write:**
1. Unauthenticated request to `/api/claims` → 401
2. Non-admin request to `/api/admin/claims` → 403
3. Admin request to `/api/admin/claims` → 200
4. Invalid status value → 400 (not 500)
5. Invalid UUID in path → 400
6. GET /error without auth (if security allows) → check behavior
7. Cross-user claim access → 403/404


## 3. What I Chose to Test

### 3.1 ClaimStatusTest — 35 tests
Full 5x5 transition matrix (valid, invalid, self-transitions, terminal state).

### 3.2 ClaimTest — 26 tests
All constructor validation rules with boundary-value analysis:
- Null handling for all fields
- Future date rejection
- Description length (10-1000 chars)
- Location length (5-200 chars)
- Amount boundaries (0.01, 0, 1M, 1M.01)
- changedBy bug characterization

### 3.3 UpdateClaimStatusUseCaseTest - 6 tests

**Why:** The use case is the actual security boundary - RBAC is enforced here,
not in the controller. It's also where the changedBy bug originates: it has
the admin ID from the command, but never passes it to claim.updateStatus().

**Tests cover:**
- Unknown admin user -> UnauthorizedException
- Non-admin user -> UnauthorizedException (RBAC boundary)
- Claim not found -> ClaimNotFoundException
- Invalid transition (SUBMITTED -> APPROVED) -> InvalidStatusTransitionException
- Valid transition (SUBMITTED -> UNDER_REVIEW) -> status updated, save called
- changedBy bug -> captured via eventPublisher mock (characterization test)

**Key learning:** The use case clears domain events after publishing when
cdcEnabled = false. Tests must capture events via the eventPublisher mock,
not from the returned Claim entity. This shaped how the changedBy test was written.

### 3.4 CreateClaimUseCaseTest - 4 tests

**Explanation:** When someone creates a new claim, the system checks
the user exists, then creates the claim with all business rules applied.
I tested the rejection path, the happy path, field integrity, and auto-generated IDs.

**Tests cover:**
- Unknown user -> UserNotFoundException
- Valid command -> claim created with SUBMITTED status
- All provided fields persisted correctly
- Claim ID auto-generated

**Key learning:** The use case delegates all validation to the Claim domain
constructor - so this use case test focuses on orchestration, not validation rules.

### 3.5 Playwright API tests - health.spec.ts (3 tests)

**What it does:** Checks the 3 services are alive (claims, BFF, UI).

**Why:** Foundation — if the app isn't running, no other test can work.

**Tests:**
- Claims Service health endpoint returns UP
- BFF Service health endpoint returns UP
- UI homepage loads

**Runtime:** ~1.6 seconds

### 3.6 Playwright API tests - auth.spec.ts (3 tests)

**What it does:** Tests the login system and auth boundary.

**Tests:**
- Valid credentials return user info (email, role, userId)
- Invalid credentials rejected (400/401/403)
- Unauthenticated request to protected endpoint rejected (401/403)

**Finding:** The login endpoint returns user info as JSON and sets the auth
token as an HTTP-only cookie (rather than returning a JSON token). My initial
test expected a `token` field — this was corrected after seeing the actual
response. The cookie approach is more secure (prevents XSS token theft).

**Runtime:** ~1.1 seconds

### 3.7 Playwright API tests - claims.spec.ts (3 tests)

**What it does:** Verifies that claim endpoints are protected - all require
authentication.

**Tests:**
- GET /api/claims without auth -> 401
- POST /api/claims without auth -> 401
- GET /api/claims/{id} without auth -> 401

**Why:** Proves the security boundary works — no claim data is accessible
without a valid login.

**Runtime:** ~0.5 seconds

### 3.8 Playwright E2E tests - login.spec.ts (2 tests)

Opens a real browser and tests the actual login flow.

**Tests:**
- Homepage loads and shows login form
- Invalid credentials stay on login page (not redirected)

**Why:** The API tests verify the backend. This E2E test verifies the real
browser experience — form renders, buttons work, flow behaves correctly.

**Runtime:** ~4.6 seconds

### 3.9 Playwright E2E tests - claim-creation.spec.ts (2 tests)

**What it does:** Tests the claim submission flow through a real browser.

**Tests:**
- Claimant can log in and reach claims page
- Claimant can navigate to create-claim page

**Why:** Proves the entire login + navigation flow works end-to-end.

**Runtime:** ~5.3 seconds

### 3.10 Vitest component tests - admin-claims-table.test.tsx (3 tests)

**What it does:** Renders the AdminClaimsTable component in isolation and
verifies its behavior with fake data.

**Tests:**
- Renders all 6 column headers (Claim ID, User ID, Incident Date, Amount, Status, Actions)
- Shows one row per claim with IDs truncated to 8 characters
- Handles empty state — headers visible, no rows

**Why:** Component tests are fast (milliseconds), don't need a browser, and
catch UI-level bugs early. This complements the E2E tests which test the
whole app through a real browser.

**Tooling:** Vitest + @testing-library/react + jsdom

**Runtime:** ~241ms

## 4. What I Deliberately Left Out
(To be filled)

## 5. Bugs / Issues Found
(To be filled)

## 6. What I'd Do With More Time
(To be filled)