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

### 2026-09-28 16:30 — Frontend (blueprint items 20–22)
- Files: `frontend/**` (Vite/React/TS/Tailwind; employee, manager, HR features; i18n en/hi/ta), `backend/src/test/.../LocalDevApplication.java`
- Build: `tsc --noEmit` clean; `vite build` OK
- Verified in browser against the bundled SPA + embedded PostgreSQL: employee apply (preview: Diwali excluded, conflict warning) → flagged → manager approve with acknowledgement → PENDING_HR → HR final approve → APPROVED; escalation page (Chitra auto-escalated to Dev); audit trail.
- Commit: `feat: role-based React frontend …`

### 2026-09-28 16:45 — AI module (items 24–27) + Sarvam
- Sarvam key provided by the user → stored only in gitignored `.env`. API shapes verified against docs.sarvam.ai AND live calls (STT saaras:v3, translate mayura:v1, TTS bulbul:v3, chat sarvam-105b with `json_object`; `sarvam-30b` is deprecated).
- Files: `assistant/**` (ports, Sarvam adapter + circuit breakers, LLM + rule extractors, deterministic date resolver, command builder, orchestrator, answers, controller), `telemetry/AssistantTelemetry`, read-only `BalanceService.snapshots/peek`, prompt `prompts/intent-system-prompt.txt`
- Tests added: `ArchitectureTest` (5), `AssistantIntegrationTest` (13), `AssistantLlmPathTest` (8), `DateExpressionResolverTest` (21), `RuleBasedIntentExtractorTest` (8), `SarvamClientTest` (3, local mock server), `SarvamLiveSmokeTest` (3, live, key-gated)
- Result: 184/184 passed. Fixed during testing: reason/“because of” regexes; a model-inferred end date now yields to the stated duration.
- Live end-to-end through the app: Hindi apply → READY proposal with resolved dates; Tamil balance → translated answer; "next week" → clarification; `/transcribe` returned the exact sentence from real audio; `/speak` returned WAV; assistant proposal confirmed in the UI → request #1008.

### 2026-09-28 17:00 — Deployment configuration (item 23)
- Files: `Dockerfile`, `.dockerignore`, `railway.json`, `render.yaml`, `.github/workflows/ci.yml`, `deploy/vercel-deploy.sh`, `backend/.env.example`, `README.md`, `FINAL_AUDIT.md`
- Verified: production fat jar (`prod,demo`) against a fresh PostgreSQL database → migrations, seeding, SPA deep links, security headers, locked actuator.
- Vercel token provided and validated (account reachable). **Not deployed**: Vercel cannot host the Spring Boot backend/PostgreSQL; a Railway or Render credential is needed for the backend (frontend-only on Vercel would be non-functional).
- Environment limitation: Docker not installed → image not built locally (CI job builds it).
