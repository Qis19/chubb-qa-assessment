\# Chubb QA Take-Home Assessment



QA test suite for the Chubb claims management application — \*\*87 automated tests\*\* across unit, integration, and component layers.



\## Executive Summary



\*\*Goal:\*\* Extend test coverage for the Chubb claims management application.



\*\*What was delivered:\*\*

\- \*\*87 automated tests\*\* across all 3 required categories:

&#x20; - 71 unit tests (JUnit) — domain logic, state machine, use cases

&#x20; - 13 integration tests (Playwright) — API endpoints + browser E2E flows

&#x20; - 3 component tests (Vitest + Testing Library) — UI component behaviour



\- \*\*1 real bug found\*\* — `changedBy` audit trail attributes status changes to the wrong user (by reading the code)

\- \*\*Test strategy documented\*\* in `TEST\_STRATEGY.md`

\- \*\*AI collaboration journal\*\* in `AI\_JOURNAL.md`



\*\*Approach:\*\*

1\. Read the source code first to identify high-risk areas

2\. Wrote tests targeting those areas (prioritised, not exhaustive)

3\. Documented findings, decisions, and bugs honestly



\---



\## Test Suite Overview



|| Type | Tool | Count | Location |
|------|------|-------|----------|
| Unit tests | JUnit 5 + Mockito | 71 | demo-app/code/claims-service/src/test/java/ |
| Contract tests (Kafka) | JUnit 5 + Embedded Kafka | 6 | demo-app/code/*/src/test/java/.../contract/ |
| Integration — API | Playwright | 9 | demo-app/tests/api/ |
| Integration — E2E | Playwright | 4 | demo-app/tests/e2e/ |
| Component tests | Vitest + Testing Library | 3 | demo-app/code/demo-app-ui/src/features/admin/components/__tests__/ |
| **Total** | | **93** | |



\---



\## Prerequisites



\- Podman Desktop 5.x+ (or Docker Desktop 24+)

\- Java 21 (Eclipse Temurin)

\- Maven 3.8+

\- Node.js 20+

\- Python 3.x (for podman-compose)



\---



\## How to Run the App



\### 1. Start the Podman machine

```cmd

podman machine start

### 2. Start infrastructure services
cd demo-app
podman compose -f docker-compose.infra.yml up -d
Wait 60 seconds.

### 3. Start application services
podman compose -f docker-compose.apps.yml up -d
Wait 60 seconds.

### 4. Verify everything is up
podman ps --filter "name=demo-"

Expected: 9 containers, all Up or Up (healthy).

**Service URLs:**
- UI: http://localhost:3001
- BFF Swagger: http://localhost:8090/swagger-ui.html
- Claims Swagger: http://localhost:8080/swagger-ui.html
- Keycloak Admin: http://localhost:8180
- Kafka UI: http://localhost:9093

**Test users:**
- Claimant: claimant@demo.com / Claimant123!
- Admin: admin@demo.com / Admin123!

---

## How to Run the Tests

### Unit tests (JUnit)

cd demo-app/code/claims-service
mvn test -Dcheckstyle.skip=true
Expected: 71 tests passing.

### Integration tests (Playwright)

Requires the app to be running (see above).

cd demo-app
npx playwright test --reporter=list

Expected: 13 tests passing (9 API + 4 E2E).

### Component tests (Vitest)
cd demo-app/code/demo-app-ui
npm test

Expected: 3 tests passing.

---

## Test Structure
demo-app/
tests/ # Playwright integration tests
api/
health.spec.ts # Service health checks
auth.spec.ts # Login + auth boundary
claims.spec.ts # Claims API auth checks
e2e/
login.spec.ts # Browser login flow
claim-creation.spec.ts # Claim navigation flow
code/
claims-service/
src/test/java/ # JUnit unit tests
com/example/demo/
domain/claim/
ClaimStatusTest.java
ClaimTest.java
application/usecases/
UpdateClaimStatusUseCaseTest.java
CreateClaimUseCaseTest.java
demo-app-ui/
src/features/admin/components/tests/
admin-claims-table.test.tsx # Vitest component tests

text

---

## Documentation

| File | Content |
|------|---------|
| README.md | This file — overview + run instructions |
| TEST_STRATEGY.md | Risk assessment, what was tested and why, bugs found |
| AI_JOURNAL.md | AI collaboration log — accepted, challenged, overrode |
| WALKTHROUGH_NOTES.md | Talking points for the panel walkthrough |

---

## Key Findings

### Bug: changedBy audit trail is wrong

**Where:** Claim.java, inside updateStatus()

**What:** The ClaimStatusChanged event's changedBy field is always set to the claim owner's ID (this.userId), never the acting admin's ID. updateStatus() doesn't even accept an admin ID parameter.

**Impact:** Audit logs cannot identify which admin actioned a claim. Violates the documented API contract (claim-events-api.yml says changedBy = "the admin user who changed the status").

**Fix (not implemented):** Change updateStatus(ClaimStatus) to updateStatus(ClaimStatus, UUID changedBy) and pass command.adminUserId() from the use case.

**Test proving it:** UpdateClaimStatusUseCaseTest.changedByIsClaimOwnerNotAdmin_bug()

### Observation: Login response shape

**Finding:** The login endpoint returns user info (email, role, userId) as JSON and sets the auth token as an HTTP-only cookie — not a JSON token field.

**Impact:** Not a bug — this is the more secure pattern (prevents XSS token theft). My initial test expected a token field; corrected after seeing the actual response.

### Infrastructure: Kafka Zookeeper state

**Finding:** Kafka failed to start after a machine restart with NodeExistsException. Zookeeper retained a stale Kafka registration from the previous run.

**Fix:** podman volume prune -f to remove orphaned anonymous volumes, then restart infra.

---

## What Was Not Done

- **Kafka contract tests** — verify producer/consumer schemas match the committed spec
- **BFF service unit tests** — the BFF layer is covered indirectly via Playwright API tests
- **Integration tests with Testcontainers** — would test against real containers in CI
- **Full claim submission E2E flow** — partial coverage via navigation tests
- **Load testing** — out of scope for this assessment

Each was excluded to focus on the highest-risk domain logic within the time available.

---

## Author

Qisti Irfan — Chubb QA Engineer Take-Home Assessment, September 2026


