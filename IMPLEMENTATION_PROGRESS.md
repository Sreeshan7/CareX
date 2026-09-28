# Implementation Progress

Authoritative design: [implementation.md](implementation.md). This log records what was built, how it was verified, and what remains.

## Environment facts (recorded 2026-09-28)

| Tool | Status | Consequence |
|---|---|---|
| JDK | 24.0.2 (compiles with `--release 21`) | OK |
| Maven | not installed → Apache Maven 3.9.11 downloaded to the session scratchpad | Build with any Maven ≥ 3.9; Docker build uses `maven:3.9-eclipse-temurin-21` |
| Docker | **not installed** | Testcontainers impossible → integration tests use **zonky embedded PostgreSQL 16.15** (real Postgres binaries, no Docker). Docker image not built locally. |
| PostgreSQL | not installed | Embedded Postgres used for tests and local dev |
| Node | 22.20 | OK |
| Railway CLI / credentials | **not available** | Deployment config prepared; deploy is a manual step (see README) |
| Sarvam docs / key | **not provided** | Provider interfaces + configurable Sarvam adapter + fake/none providers; endpoint paths are env-configurable |

## Log

### 2026-09-28 15:25 — Project skeleton + schema
- Files: `.gitignore`, `backend/pom.xml`, `backend/src/main/resources/db/migration/V1__schema.sql`, `V2__reference_data.sql`, `application*.yml`, `LeaveApplication.java`
- Build: `mvn test -Dtest=SchemaSmokeTest` → **PASS** (Flyway V1+V2 applied on PostgreSQL 16.15 incl. `btree_gist` exclusion constraint)
- Deviation: Spring Boot **3.5.16** instead of 3.3.x (3.3 is EOL and JDK 24 needs the newer Hibernate/ByteBuddy). Schema adds one column `leave_request.manager_routed_to_hr` to make the top-level-user HR-proxy routing (§6.1) explicit.

### 2026-09-28 16:05 — Core product (blueprint items 1–19)
- Files: `common/*` (errors, clock, correlation id, channel), `config/*` (security, rate limiter, SPA serving), `auth/*` (JWT, login, demo-login), `org/*`, `leave/type|calendar|balance|request|query/*`, `workflow/*` (state machine, access policy, allowed actions, workflow service, escalation job, history), `conflict/*`, `escalation/*`, `audit/*`, `notification/*`, `dashboard/*`, `web/*` controllers, `demo/*` seeder + reset
- Build: `mvn -B test` → **BUILD SUCCESS**
- Tests run: 123 — **123 passed, 0 failed**
  - `LeaveStateMachineTest` (54: all 7×7 status×event pairs + invariants)
  - `ProRatingCalculatorTest` (13: every row of §9.3)
  - `WorkingDayCalculatorTest` (3), `ConflictResultTest` (4), `SchemaSmokeTest` (1)
  - `WorkflowIntegrationTest` (23): happy path, HR-at-manager-stage 409, manager-at-HR-stage 403, self-approval, four-eyes, top-level HR proxy, reject/cancel balance release, cancel-after-start, overlap across types, validation rules, holiday exclusion, idempotent submit, conflict flag + ack, audit per transition, audit append-only trigger, balance CHECK backstop
  - `ConcurrencyTest` (6): 10 parallel submits → exactly 2 succeed; 8 duplicate submits → 1 row; 2 simultaneous approvals → 1 decision; approval vs escalation ×15 → always consistent; cancel vs HR approve ×10; parallel overlapping types → 1 wins
  - `EscalationTest` (7): timeout → skip-level; original manager still approves; HR-stage escalation never auto-approves; HR-pool fallback with four-eyes; 4 concurrent job runs → exactly 1 escalation; restart recovery via fresh job instance; cancelled never escalates
  - `SecurityApiTest` (12): login ok/generic failure + audit, 401 missing/tampered/expired JWT, role routes, IDOR read/write → 404, mass assignment → 400, HTTP idempotent replay, ProblemDetail shape + correlation id, actuator lockdown, security headers, employee sees no teammate names, demo-login off by default
- Remaining: frontend, AI module, deployment files, docs
