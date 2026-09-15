# Test Coverage by Module

Complete Detail — Unit, Contract, Integration, Component, and End-to-End

**Candidate:** Qisti Irfan
**Date:** 15 September 2026

---

This is the single consolidated reference for everything tested across the Claims Management application during this assessment. Per-file test counts are exact — taken directly from mvn test, npm test, and npx playwright test output, not estimated.

---

## 1. claims-service — Detailed Test Coverage

Spring Boot service owning claim and user data and all core business rules. No tests existed in this module before this assessment (confirmed by checking src/test/java directly — it did not exist).

### 1.1 Domain Logic

| Test File | Tests | What It Covers |
|-----------|-------|---------------|
| ClaimStatusTest.java | 35 | Full 5x5 transition matrix (every combination of the 5 ClaimStatus values) as a single parameterized test, plus self-transition and terminal-state checks |
| ClaimTest.java | 26 | Claim entity constructor validation: future-date rejection, description length (10-1000 chars, including whitespace-trim boundary), location length (5-200 chars), amount boundaries (rejects <=0, accepts exactly the max, rejects one cent over), null-argument handling, and the changedBy bug characterized as a passing test asserting current behavior |

### 1.2 Application / Use Cases

| Test File | Tests | What It Covers |
|-----------|-------|---------------|
| CreateClaimUseCaseTest.java | 4 | Unknown-user rejection, valid claim creation with SUBMITTED status, all provided fields persisted correctly, auto-generated claim ID |
| UpdateClaimStatusUseCaseTest.java | 6 | RBAC (unknown acting user rejected, non-admin actor rejected), claim-not-found, illegal status transition rejected, legal transition applied and persisted, and a dedicated test pinning down the changedBy misattribution bug precisely |

### 1.3 Contract Tests (Kafka)

| Test File | Tests | What It Covers |
|-----------|-------|---------------|
| ClaimEventProducerContractTest.java | 2 | Forces kafka.producer.schema-validation.enabled to true and publishes real ClaimSubmitted/ClaimStatusChanged events through the actual KafkaDomainEventPublisher. Both tests FAIL validation — documenting a real contract violation: the producer's nested envelope ({ eventType, eventId, payload: {...} }) doesn't match the flat committed schema (eventId, claimId, userId, incidentDate, claimAmount, occurredAt at top level). |

### 1.4 Why These Numbers Matter

The 35 transition tests represent the complete state machine specification — 25 transition combinations plus 10 boundary checks. Because this table is the actual specification of the admin workflow and is duplicated by hand in the frontend, spot-checking would leave silent gaps.

The 26 validation tests represent every rule enforced by the Claim constructor — the single point through which all claims enter the system. Boundary-value analysis (exactly 10 chars, exactly 1000 chars, amount exactly 1,000,000, one cent over) is where bugs typically live, so each edge was tested explicitly.

The 10 use case tests cover the two orchestration paths — creation and status update. Each test targets a specific behavior: RBAC enforcement, entity lookup failure, event publishing, and the audit-trail bug.

The 2 contract tests document a real schema violation — the producer's output doesn't match its own committed JSON schema.

**claims-service total:** 73/73 passing (71 unit + 2 contract).

---

## 2. bff-service — Detailed Test Coverage

Spring Boot middleware service. No tests existed in this module before this assessment (confirmed by checking src/test/java directly).

### 2.1 Contract Tests (Kafka)

| Test File | Tests | What It Covers |
|-----------|-------|---------------|
| ClaimEventConsumerContractTest.java | 4 | Two tests confirm ClaimEventKafkaConsumer parses the actual publisher envelope shape (nested payload). Two tests target the CDC path — one confirms the consumer reads the real Postgres column name (claim_id), one documents a schema-vs-code drift: the committed DebeziumClaimsMessage.json documents the primary key field as "id", but the consumer code reads "claim_id" (the real Postgres column name). |

### 2.2 Why These Numbers Matter

The 4 consumer contract tests verify the consumer correctly handles both messaging paths: the direct-publish path (envelope from KafkaDomainEventPublisher) and the CDC path (Debezium envelope from Postgres write-ahead log). The two paths publish the same logical event but with different shapes — the tests prove the consumer handles both. One of them actively documents a bug: the schema file says the primary key field is "id", but the code reads "claim_id". The code works in practice because real Debezium output uses the column name, not the schema's documented name — but a developer reading only the schema would be misled.

**bff-service total:** 4/4 passing (contract tests only).

---

## 3. demo-app-ui — Detailed Test Coverage

Next.js/React frontend. Vitest, Testing Library, jest-dom, and user-event were already present in package.json devDependencies, and the test/test:watch/test:coverage npm scripts already existed — but no vitest.config.ts had ever been written and zero component test files existed.

### 3.1 Component Tests (New)

| Test File | Tests | What It Covers |
|-----------|-------|---------------|
| admin-claims-table.test.tsx | 3 | Renders all 6 column headers; renders one row per claim with truncated IDs (first 8 chars); handles the empty state — headers visible, no data rows |

### 3.2 Setup Required

Two fixes were needed to get the component test suite running, both self-caught by running the suite and reading the error output:

- vitest.config.ts required explicit include patterns — Vitest's default file discovery also matched *.spec.ts, which collided with the Playwright specs. The include pattern was scoped to prevent this.
- Mock data type mismatch — the initial mock data used short date strings but the component's formatDate helper expects a Date object, causing a RangeError. Fixed by passing new Date() objects.

### 3.3 Why These Numbers Matter

Component tests are fast (milliseconds), require no browser, and catch UI-level bugs early. The 3 tests cover three distinct behaviors — header rendering, data row rendering, and empty state — which together verify the component's core contract with its callers.

**demo-app-ui total:** 3/3 passing (component tests only).

---

## 4. Integration Tests — Playwright

Playwright is used for integration testing at two levels: API tests that call the backend directly, and end-to-end tests that drive a real browser.

### 4.1 API Tests

| Test File | Tests | What It Covers |
|-----------|-------|---------------|
| tests/api/health.spec.ts | 3 | Claims Service and BFF Service /actuator/health return UP; UI homepage loads |
| tests/api/auth.spec.ts | 3 | Valid credentials return user info; invalid credentials rejected; unauthenticated request to protected endpoint rejected |
| tests/api/claims.spec.ts | 3 | GET/POST /api/claims and GET /api/claims/{id} without auth all return 401 |

### 4.2 End-to-End Tests

| Test File | Tests | What It Covers |
|-----------|-------|---------------|
| tests/e2e/login.spec.ts | 2 | Homepage loads and shows login form; invalid credentials stay on login page |
| tests/e2e/claim-creation.spec.ts | 2 | Claimant can log in and reach claims page; can navigate to create-claim page |

### 4.3 Why These Numbers Matter

The 9 API tests verify service availability, login success and failure, and the authentication boundary on all claims endpoints. They run without a browser and complete in under 3 seconds.

The 4 E2E tests open a real Chromium browser and exercise the login and claim navigation flows exactly as a user would. They prove the whole stack works together.

One observation from the auth tests: the login endpoint returns user info as JSON and sets the auth token as an HTTP-only cookie rather than returning a JSON token field. My initial test expected a token field and failed; I corrected the assertion to match the actual response. This is the more secure pattern.

**Playwright total:** 13/13 passing (9 API + 4 E2E).

---

## 5. Recommended Next Coverage (Not Yet Implemented)

| Scenario | Priority | Layer | Why |
|----------|----------|-------|-----|
| BFF-service unit tests | Medium | Unit | Covered indirectly via Playwright API tests and consumer contract tests. Direct unit tests for the WebSocket fan-out and Keycloak token handling would strengthen this layer. |
| Claim ownership check | Medium | Unit | GetClaimUseCase enforces ownership with no dedicated test today. |
| Full claim submission E2E | Medium | E2E | Current tests verify login and navigation. A full wizard-filling test would close the loop. |
| Testcontainers integration | Medium | Integration | Would spin up Postgres + Kafka in CI without manual Docker setup. |
| Load testing (K6) | Low | Load | Out of scope for minimum requirements. |

---

## 6. Notes on Accuracy

- Per-file test counts were taken directly from mvn test, npm test, and npx playwright test output — not estimated.
- Module-level pass/fail totals are the actual numbers reported when each suite was run against the live stack.
- One revision was made during this assessment: the initial mock data for the component test used string dates, causing a RangeError. Fixed by using Date objects.
- The Vitest configuration required an explicit include pattern to prevent it from matching Playwright .spec.ts files during test discovery.
- The contract test setup went through path fixes (files initially landed in src/main instead of src/test) — resolved by moving to the correct folder.

---

## 7. Overall Summary — All Modules

| Module | Test Files | Tests | Status |
|--------|-----------|-------|--------|
| claims-service (domain) | 2 | 61 | 61/61 passing |
| claims-service (use cases) | 2 | 10 | 10/10 passing |
| claims-service (contract) | 1 | 2 | 2/2 passing |
| bff-service (contract) | 1 | 4 | 4/4 passing |
| demo-app-ui (component) | 1 | 3 | 3/3 passing |
| Integration (API) | 3 | 9 | 9/9 passing |
| Integration (E2E) | 2 | 4 | 4/4 passing |
| **Grand total** | **12 files** | **93** | **93/93 passing** |

**Test categories covered:**
- ✅ Unit tests — 71 tests (JUnit 5 + Mockito)
- ✅ Contract tests — 6 tests (JUnit 5 + Embedded Kafka)
- ✅ Integration tests — 13 tests (Playwright API + E2E)
- ✅ Component tests — 3 tests (Vitest + Testing Library)

Every module in the project now has at least one form of automated test coverage.

---

