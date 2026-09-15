# Claims Management Application — QA Assessment

Take-Home Assessment Report

**Candidate:** Qisti Irfan
**Date:** 15 September 2026
**Repository:** https://github.com/Qis19/chubb-qa-assessment

---

## Document Guide

| Section | What It Covers |
|---------|---------------|
| 1. Executive Summary | What I did and the results |
| 2. Environment Setup | How I got the app running |
| 3. No Pre-Existing Tests | Confirmed the codebase had no tests |
| 4. Risk Assessment | Where I looked for bugs and why |
| 5. Tests I Wrote | 93 tests across 4 layers |
| 6. Bugs Found | 2 real bugs |
| 7. AI Collaboration | How I used AI |
| 8. Full Test Baseline | All tests passing |
| 9. What I Didn't Do | Honest gaps |
| 10. What's in the Submission | File list |

---

## 1. Executive Summary

This report covers a QA review of the Chubb Claims Management app — a three-tier system with a Next.js/React frontend, two Spring Boot services (Claims and BFF), Kafka for messaging, PostgreSQL, Redis, and Keycloak for auth.

The assessment had two parts: read the code first and figure out where the risk is, then write tests for those areas. Finding real bugs was a key part.

**What I did:**

I got the full app running locally — nine containers via Podman on Windows. I logged in as both admin and claimant, used the claim submission wizard, and used the admin status workflow. I confirmed the codebase had no pre-existing tests anywhere, which matches the brief.

Then I read the source code across all three modules and identified the highest-risk areas. Based on that, I wrote 93 tests across four layers:

- **71 unit tests** (JUnit + Mockito) — claim domain logic, state machine, use cases
- **6 contract tests** (JUnit + Embedded Kafka) — Kafka producer and consumer contract verification
- **13 integration tests** (Playwright) — 9 API tests and 4 browser E2E tests
- **3 component tests** (Vitest + Testing Library) — AdminClaimsTable

I found two real bugs:

1. An audit trail bug — status changes are attributed to the claim owner instead of the acting admin
2. A Kafka schema violation — the producer's actual envelope doesn't match the committed JSON schemas

Both bugs have dedicated tests that document the current behavior.

---

## 2. Environment Setup

I set up the full app locally before doing any code review or writing tests.

First I installed the tools I needed: Podman Desktop + WSL2, Java 21, Maven, Node.js, and Python. I started the Podman machine, built the Java JARs, and then started the six infrastructure services (Postgres, Redis, Zookeeper, Kafka, Keycloak, Kafka UI). Then the three app services (claims-service, bff-service, ui). Nine containers total.

I verified everything was up by checking `podman ps`, hitting the health endpoints, and logging into the UI as both user roles. I exercised the claim submission wizard and admin workflow to confirm it all worked.

Two issues came up during setup. First, WSL2 and the Virtual Machine Platform weren't enabled by default — I found this by reading Podman's error messages. Second, Kafka failed to start after a restart because of leftover Zookeeper state. The fix was `podman volume prune -f` to clear orphaned volumes, then restart.

By the end of setup, the app was running fully.

---

## 3. No Pre-Existing Tests

I checked the codebase directly instead of assuming.

Neither `src/test/java` folder existed in the backend services. In the UI, Vitest and Testing Library were in `package.json`, but there was no `vitest.config.ts` and no component test files.

This matches the brief. The codebase is a genuine blank slate.

The app code itself is well organized. The claims-service uses hexagonal architecture with clear separation between domain, application, and adapter layers. Validation is centralized in the `Claim` entity. Event publishing happens after commit. The risk is concentrated in a few integration points rather than spread everywhere.

---

## 4. Risk Assessment

I read the actual source code across all three modules before writing any test.

**Claim status transitions and validation — highest risk.**

`Claim.updateStatus()` and `ClaimStatus.canTransitionTo()` are the single source of truth for the entire admin workflow. The same rules are also copied by hand in the frontend. If they drift apart, silent bugs appear. The `Claim` constructor is the only place where amount, date, and length rules are checked — every claim goes through it. A bug here affects every claim.

**Authentication boundary — high risk.**

Admin endpoints require `ROLE_ADMIN`. The login flow through bff-service validates credentials with Keycloak and sets an HTTP-only session cookie. All `/api/**` endpoints require auth. A bug here means unauthorized access.

**Kafka event contract — high risk.**

The app publishes events to Kafka and consumes them in bff-service. The project ships JSON schemas describing what each event should look like. If the producer's output doesn't match the schema, consumers can break silently. This was specifically flagged by the assessor.

**Use case orchestration — moderate risk.**

`CreateClaimUseCase` and `UpdateClaimStatusUseCase` handle the flow from HTTP request to persistence to event publishing. They check RBAC, look up entities, call the domain, save, and publish. A bug here corrupts data silently.

**Audit trail — real bug found.**

While reading `Claim.updateStatus()`, I noticed the `ClaimStatusChanged` event's `changedBy` field is set to `this.userId` — the claim owner. It should be the admin. `UpdateClaimStatusUseCase` has the admin's ID (`command.adminUserId()`) but never passes it down. The project's own schema documents `changedBy` as "The admin user who changed the status." So this violates the contract.

**Kafka producer contract — real bug found.**

`KafkaDomainEventPublisher` builds a nested envelope: `{ eventType, eventId, payload: { ... } }`. But the committed schemas describe a flat structure: `{ eventId, claimId, userId, ... }` at the top level. The producer and consumer agree with each other, but neither matches the documented schema.

---

## 5. Tests I Wrote

93 tests across four layers.

**Unit tests — 71 tests (JUnit + Mockito).**

`ClaimStatusTest` — 35 tests. Full 5x5 transition matrix, all 25 combinations plus self-transitions and terminal state. This table is the actual workflow spec, so spot-checking would leave gaps.

`ClaimTest` — 26 tests. Every validation rule in the constructor, with boundary values: exactly 10 chars, exactly 1000, whitespace-only, amount 0, amount 1,000,000, amount 1,000,000.01.

`CreateClaimUseCaseTest` — 4 tests. User validation, claim creation, field integrity, auto-generated ID.

`UpdateClaimStatusUseCaseTest` — 6 tests. RBAC, claim not found, valid and invalid transitions, and the `changedBy` bug as a characterization test.

**Contract tests — 6 tests (JUnit + Embedded Kafka).**

Producer tests (claims-service) — 2 tests. Force schema validation on and publish real events. Both fail — proving the schema violation.

Consumer tests (bff-service) — 4 tests. Feed real envelope payloads and verify parsing. Two cover the direct-publish path; two cover CDC, including a schema-vs-code drift where the schema says `id` but the code reads `claim_id`.

**Integration tests — 13 tests (Playwright).**

API tests — 9 tests. Service health, login success/failure, and auth boundary on claims endpoints.

E2E tests — 4 tests. Real browser login and navigation to claim pages.

**Component tests — 3 tests (Vitest + Testing Library).**

`admin-claims-table.test.tsx` — 3 tests. Renders headers, rows with truncated IDs, and empty state.

---

## 6. Bugs Found

### Bug 1: Audit trail attributes status changes to the wrong user

In `Claim.updateStatus()`, the `ClaimStatusChanged` event's `changedBy` field is set to `this.userId` — the claim owner. The method never receives the acting admin's ID.

`UpdateClaimStatusUseCase` has it (`command.adminUserId()`) but doesn't pass it. Every status change is attributed to the claimant, no matter which admin did it.

The schema in `claim-events-api.yml` says `changedBy` should be "The admin user who changed the status." So this violates the contract. Audit logs can't tell who actioned a claim.

I found this by reading `Claim.java`, then confirming it in `UpdateClaimStatusUseCase.java`. There's a characterization test that asserts the current incorrect behavior.

### Bug 2: Kafka producer envelope violates its own schema

`KafkaDomainEventPublisher` builds a nested envelope: `{ eventType, eventId, payload: { claimId, userId, ... } }`. But the committed schemas describe flat structures with `eventId`, `claimId`, `userId`, `incidentDate`, `claimAmount`, `occurredAt` at the top level.

Fields like `incidentDate`, `claimAmount`, and `occurredAt` are missing entirely. Other fields are wrapped under `payload`, which the schema doesn't expect.

The bug ships silently because schema validation is disabled in the deployed config. If it were enabled, every claim submission would throw an exception.

My producer contract tests document this — they force validation on, publish an event, and assert that validation fails.

---

## 7. AI Collaboration

I used AI (Claude by Anthropic) throughout for environment troubleshooting, code reading, test authoring, and docs.

Where it helped:
- Test structure for parameterized tests
- Diagnosing the Kafka `NodeExistsException`
- Drafting the contract test structure after the assessor asked for Kafka

Where I verified or pushed back:
- WSL2 setup — AI didn't flag it needed enabling; I found it from error messages
- Mock data types in component tests — AI's first attempt used strings for dates; I fixed it
- Component test count — AI suggested 5–8; I kept it to 3
- Priority — AI suggested more E2E tests; I chose documentation first, then contract tests when the assessor asked

Full details in `AI_JOURNAL.md`.

---

## 8. Full Test Baseline

| Module | Result | Notes |
|--------|--------|-------|
| claims-service (unit) | 71/71 passing | Domain, state machine, use cases |
| claims-service (contract) | 2/2 passing | Kafka producer schema validation |
| bff-service (contract) | 4/4 passing | Kafka consumer envelope parsing |
| Integration (API) | 9/9 passing | Playwright API tests |
| Integration (E2E) | 4/4 passing | Playwright browser tests |
| demo-app-ui (component) | 3/3 passing | Vitest + Testing Library |
| **Grand total** | **93/93 passing** | All 3 required categories + contract tests |

---

## 9. What I Didn't Do

**BFF-service unit tests.** The bff-service is covered through Playwright API tests and the consumer contract tests. Direct unit tests of the WebSocket fan-out and Keycloak token handling would be nice to have.

**Testcontainers integration.** Would spin up Postgres and Kafka in CI without manual Docker. The embedded Kafka in the contract tests achieves similar coverage for the messaging layer.

**Full claim submission E2E.** The E2E tests cover login and navigation. A full test that fills the multi-step claim wizard would close the loop.

**Load testing.** Out of scope for the minimum requirements.

---

## 10. What's in the Submission

**Repository:** https://github.com/Qis19/chubb-qa-assessment

**Test files:**
- 4 JUnit unit test files (claims-service)
- 1 JUnit producer contract test (claims-service)
- 1 JUnit consumer contract test (bff-service)
- 3 Playwright API tests (demo-app/tests/api/)
- 2 Playwright E2E tests (demo-app/tests/e2e/)
- 1 Vitest component test (demo-app-ui)
- Config files: `vitest.config.ts`, `vitest.setup.ts`

**Documentation:**
- `README.md` — Overview and run instructions
- `TEST_STRATEGY.md` — Risk assessment and coverage decisions
- `AI_JOURNAL.md` — AI collaboration log
- `WALKTHROUGH_NOTES.md` — Walkthrough prep
- `ASSESSMENT_REPORT.md` — This report
- `TEST_COVERAGE_BY_MODULE.md` — Per-module breakdown

Git history shows the incremental work — each test committed with its docs.

---