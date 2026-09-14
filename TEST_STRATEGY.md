# Test Strategy — Chubb QA Assessment

## 1. Application Overview

Three-tier insurance claims management app:
- **UI**: Next.js 16 + React (port 3001)
- **BFF Service**: Spring Boot (port 8090)
- **Claims Service**: Spring Boot (port 8080)
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

**Tests to write:**
1. Valid transitions → expect 200
2. Invalid transitions → expect 400
3. Backward transition (`UNDER_REVIEW → SUBMITTED`) → document behavior
4. Terminal state (`CLOSED → anything`) → expect 400
5. Concurrent updates → check for data corruption

## 3. What I Chose to Test
(To be filled)

## 4. What I Deliberately Left Out
(To be filled)

## 5. Bugs / Issues Found
(To be filled)

## 6. What I'd Do With More Time
(To be filled)