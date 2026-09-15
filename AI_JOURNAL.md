# AI Working Journal — Chubb QA Assessment

Log of AI-assisted decisions: accepted, challenged, overrode.

---

## Entry 1 — Environment Setup

- **Prompt:** How to set up Podman on Windows for this assessment?
- **AI Suggestion:** Install Podman Desktop, enable WSL2, `podman machine init` + `start`.
- **Decision:** Accepted
- **Reasoning:** Matched the setup guide; worked after enabling Virtual Machine Platform.
- **Challenge:** AI did not flag that WSL2 and Virtual Machine Platform needed to be enabled manually. I found this from reading Podman's error messages and resolved it myself.

---

## Entry 2 — Test Framework

- **Prompt:** TypeScript or Java for Playwright?
- **AI Suggestion:** TypeScript — faster setup, better AI generation, matches frontend.
- **Decision:** Accepted
- **Reasoning:** 5-min setup vs 20-min for Java; docs are TS-first.

---

## Entry 3 — Repo Root

- **Prompt:** Where should the Git repo root be?
- **AI Suggestion:** `tech-assess-qa` level so docs live beside the app.
- **Decision:** Accepted
- **Reasoning:** Cleaner submission structure.

---

## Entry 4 — Test Prioritisation Strategy

- **Prompt:** Which tests should I write first with limited time?
- **AI Suggestion:** Prioritise the claim state machine and validation rules.
- **Decision:** Accepted
- **Reasoning:** The state machine is the single source of truth for the admin workflow and is duplicated by hand in the frontend. A bug here corrupts every claim. The Claim constructor is the only place amount/date/length validation is enforced. These are the two highest-risk areas identified by reading the code.

---

## Entry 5 — Component Test Scope

- **Prompt:** How many component tests should I write for AdminClaimsTable?
- **AI Suggestion:** 5–8 tests covering rendering, interactions, clipboard, keyboard.
- **Decision:** Overrode — wrote 3 tests only.
- **Reasoning:** The assessment says "quality over quantity." Three tests cover the component's core behaviors: headers, rows, and empty state. More tests would be diminishing returns given the time budget. AI's suggestion was thorough but not necessary for the goal.

---

## Entry 6 — Mock Data Type Mismatch

- **Prompt:** Help write component test mock data.
- **AI Suggestion:** Used short date strings like `'2026-01-15'` for `incidentDate`.
- **Decision:** Challenged and fixed.
- **Reasoning:** The component's `formatDate` helper expects a `Date` object, not a string. Running the test produced a `RangeError: Invalid time value`. I read `formatting.ts` to understand the actual signature and fixed the mock data to use `new Date('2026-01-15')`.

---

## Entry 7 — Infrastructure Debugging (Kafka)

- **Prompt:** Kafka container keeps failing with `NodeExistsException`.
- **AI Suggestion:** Run `podman volume prune -f` to remove orphaned anonymous volumes, then restart.
- **Decision:** Accepted after 3 failed attempts with simpler fixes.
- **Reasoning:** Simple restart and container removal didn't work. The volume prune finally fixed it. The AI's diagnosis was correct — Zookeeper had stale Kafka registration state in an anonymous volume after the machine was force-stopped.

---

## Entry 8 — Prioritisation: Docs vs More Tests

- **Prompt:** Should I add more Playwright tests or finish documentation?
- **AI Suggestion:** Add more tests.
- **Decision:** Overrode — chose documentation.
- **Reasoning:** The assessment lists documentation as a required deliverable. I already had strong integration coverage (13 Playwright tests). Complete docs deliver more value than 2 more E2E tests.

---

## Entry 9 — Kafka Contract Tests (Assessor Feedback)

- **Context:** After initial review, the assessor said Kafka contract tests were needed and recommended adding them.
- **Prompt:** How to write Kafka contract tests for the producer and consumer?
- **AI Suggestion:** Two test files — one in claims-service for the producer, one in bff-service for the consumer. Use Spring's Embedded Kafka so no external broker is required.
- **Decision:** Accepted the approach, found the actual bug myself.
- **Reasoning:** While reading `KafkaDomainEventPublisher.java` and the committed JSON schemas, I noticed the envelope structure didn't match. The producer nests fields under `payload`, but the schemas describe a flat structure. The producer and consumer agree with each other — so the app works — but neither matches the documented contract. My producer contract tests fail validation, proving this.
- **Challenged:** AI's first file layout put the consumer test in the wrong service folder (`claims-service` instead of `bff-service`). I caught this when Maven couldn't find the test. Moved it to the right service.
- **Also debugged:** A corrupted generated file in `target/generated-sources/` caused compile errors. Fixed with `mvn clean`.
- **Outcome:** 6 contract tests passing. 2 real bugs documented.
- **Reflection:** Assessor feedback is genuinely valuable — I wouldn't have prioritized contract tests on my own, but they exposed a real defect.

---

## Entry 10 — Simple Explanation Request

- **Prompt:** Explain the tests in simple terms so I can convey to the panel.
- **AI Suggestion:** Rewrite technical explanations as plain-English versions.
- **Decision:** Accepted — this became part of walkthrough prep.
- **Reasoning:** The panel evaluates whether I can defend my decisions. Being able to explain tests simply is a real skill. This AI contribution was genuinely valuable.

---

## Overall Reflection

**Where AI was most valuable:**
- Initial environment setup and code structure
- Parameterized test patterns
- Diagnosing infrastructure issues (Kafka NodeExists)
- Drafting contract test structure

**Where I had to push back or verify:**
- AI didn't flag WSL2 needed enabling — I found it via errors
- AI's mock data had wrong types — I fixed them
- AI suggested more tests than needed — I kept it focused
- AI suggested more E2E over docs — I chose docs
- AI initially mislocated the consumer test file — I moved it

**What I learned:**
Every AI suggestion needed verification. The most valuable contributions were structural (test patterns, diagnosis) rather than content. The few times I accepted blindly, I paid for it with debug time. AI is a force multiplier — but only when the human stays in the loop on every decision.