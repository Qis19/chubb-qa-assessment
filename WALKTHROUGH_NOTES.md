# Walkthrough Notes — Chubb QA Assessment

Quick reference for the panel walkthrough. Not polished — my talking points.

## 1. Approach

**What I did:**
1. Read the source code first — focused on domain logic and integration points
2. Identified highest-risk areas (state machine, validation, auth boundary)
3. Wrote tests in priority order
4. Documented decisions, bugs, and AI collaboration

**Why this order:** The assessment says to assess before testing. I followed that — reading first made tests target real risks, not surface-level checks.

---

## 2. Test Suite at a Glance

| File | Tests | Type | Why |
|------|-------|------|-----|
| ClaimStatusTest | 35 | Unit | Full 5x5 state machine matrix |
| ClaimTest | 26 | Unit | All validation rules + boundary values |
| UpdateClaimStatusUseCaseTest | 6 | Unit | RBAC, transitions, changedBy bug |
| CreateClaimUseCaseTest | 4 | Unit | User validation + claim creation |
| health.spec.ts | 3 | API | Services alive |
| auth.spec.ts | 3 | API | Login + auth boundary |
| claims.spec.ts | 3 | API | Claims endpoints require auth |
| login.spec.ts | 2 | E2E | Real browser login |
| claim-creation.spec.ts | 2 | E2E | Browser navigation |
| admin-claims-table.test.tsx | 3 | Component | UI component behavior |
| **Total** | **87** | | |

---

## 3. Key Decisions (and Why)

### Why test the state machine so heavily?

The claim lifecycle is the core of the app. A bug there corrupts every claim. The same transition rules are duplicated by hand in the frontend — if either side drifts, silent bugs appear. Testing all 25 combinations plus self-transitions is the only safe way to cover it.

### Why only 3 component tests?

The assessment says quality over quantity. The component tests cover three distinct behaviors — rendering headers, showing rows, handling empty state. More tests would be diminishing returns given my time budget.

### Why not Kafka contract tests?

They're valuable but not in the minimum requirements. I prioritized the 3 required categories (unit, integration, component). Contract tests would be my next priority with more time.

### Why not test the BFF service directly?

The BFF is covered indirectly via Playwright API tests. A dedicated BFF unit test suite would be my next focus with more time.

---

## 4. Bug Found

### The changedBy bug

**What:** When an admin changes a claim's status, the event that gets published says the CLAIM OWNER changed it, not the admin. This means audit logs can't identify which admin actioned a claim.

**Where:** In Claim.java — updateStatus() uses this.userId instead of the admin's ID.

**How I found it:** I read Claim.java, noticed this.userId was being used, then read UpdateClaimStatusUseCase.java and confirmed the admin ID was available (command.adminUserId()) but never passed down.

**Why it matters:** The API contract (claim-events-api.yml) documents changedBy as "the admin user who changed the status." This is a direct contract violation.

**Test:** UpdateClaimStatusUseCaseTest.changedByIsClaimOwnerNotAdmin_bug() — asserts the current (incorrect) behavior, so it fails when someone fixes it.

---

## 5. AI Collaboration

**Accepted:**
- Test structure for parameterized tests
- Mockito patterns for use case testing
- Diagnosis of Kafka NodeExistsException

**Challenged:**
- Mock data types (AI's first mock used strings for dates — I fixed)
- CI-specific config (removed since we run locally)

**Overrode:**
- AI suggested 5-8 component tests — I chose 3
- AI suggested more E2E tests — I chose documentation instead

**See AI_JOURNAL.md for full log.**

---

## 6. What I'd Do With More Time

**Priority order:**

1. Kafka contract tests — verify producer/consumer schemas match the committed spec. High value, would catch the schema violations that exist in the code.
2. BFF service unit tests — dedicated tests for the auth and WebSocket layers.
3. Integration tests with Testcontainers — real Postgres + Kafka in CI without manual Docker setup.
4. More component tests — cover claim wizard, filters, and other UI.
5. Load testing — with K6 or similar.

---

## 7. Numbers to Remember

- 93 tests total
- 71 unit (JUnit)
- 6 contract (JUnit + Embedded Kafka)
- 13 integration (Playwright — 9 API + 4 E2E)
- 3 component (Vitest)
- 2 bugs found (changedBy + Kafka schema violation)
- 20+ git commits
- All 3 required test categories + contract tests covered

---

## 8. If Asked Hard Questions

**"Why only 87 tests and not more?"**
Quality over quantity. The assessment explicitly says a focused set across a few test types is more valuable than broad shallow coverage. I prioritized the highest-risk areas.

**"Why no contract tests?"**
Not in the minimum requirements. I focused on the 3 required categories. Contract tests would be my next investment with more time.

**"Why no BFF tests?"**
The BFF is covered indirectly via Playwright API tests against it. Direct unit tests would be nice but weren't the highest priority.

**"How do you know your tests are correct?"**
I ran them all. They pass. I also read the source code each test targets — so I understand what each test is proving, not just that it passes.

**"What did AI do that you had to override?"**
AI suggested more tests than I needed. I kept it focused. AI also suggested keeping CI-specific config — I removed it since we run locally.