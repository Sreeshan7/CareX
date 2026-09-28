# Final Audit against implementation.md

Audit date: 2026-09-28 · Commit: see `git log` · Test run: **184 run, 184 passed, 0 failed, 0 skipped** (`mvn test` with `SARVAM_API_KEY` set; without the key the 3 live Sarvam tests are skipped → 181 run).

Legend: ✅ implemented and verified · ⚠️ implemented with a stated limitation · ❌ not done

## 1. Mandatory challenge requirements

| # | Requirement | Status | Where | Tested | Demo steps |
|---|---|---|---|---|---|
| M1 | Java / Spring Boot | ✅ | `backend/` (Spring Boot 3.5.16, Java 21 target) | whole suite | — |
| M2 | Leave management | ✅ | `leave/*`, `workflow/LeaveWorkflowService`, `web/LeaveRequestController` | `WorkflowIntegrationTest` (23) | Arjun → Apply leave → submit |
| M3 | Manager → HR → Final approval | ✅ | `workflow/policy/LeaveStateMachine`, `AccessPolicy` | `LeaveStateMachineTest` (54), `WorkflowIntegrationTest.happyPath…` | Meera approves → PENDING_HR → Hema final-approves → APPROVED |
| M4 | Explicit approval state machine | ✅ | `LeaveStateMachine` (15 transitions, 7 states), `approval_action` timeline | all 49 status×event pairs; `tableHasExactlyFifteenTransitions` | Request detail → stage stepper + timeline |
| M5 | Automatic escalation after timeout | ✅ | `workflow/EscalationJob` (`FOR UPDATE SKIP LOCKED`), `escalation` table | `EscalationTest` (7), `ConcurrencyTest.approvalAndEscalationRace…` | Chitra's seeded request escalates to Dev within ~20 s of startup; HR → Escalations → "Run escalation check now" |
| M6 | Team-wide conflict detection | ✅ | `conflict/ConflictService`, `ConflictResult` | `ConflictResultTest` (4), `WorkflowIntegrationTest.conflictIsFlagged…` | Meera's queue: Bala's request ⚠ Flagged; detail heat strip |
| M7 | Excessive leave FLAGGED, not rejected | ✅ | flag never changes status; approval needs `acknowledgeConflict` | `conflictIsFlaggedNotRejectedAndApprovalNeedsAcknowledgement` | Approve Bala → checkbox required → approved with ack in timeline |
| M8 | Pro-rating for mid-year joiners | ✅ | `leave/balance/ProRatingCalculator` (month-based, 15th cutoff, round half-up to 0.5) | `ProRatingCalculatorTest` (13, every blueprint row) | Log in as Farhan → balance cards show "Joined …; 5/12 months × 18 = 7.50 → 7.5" |
| M9 | Role-based frontend (Employee/Manager/HR) | ✅ | `frontend/src/features/{employee,manager,hr}`, route guards, server `allowedActions` | typecheck + build; browser walkthrough (see §6) | Quick-login as each role |

## 2. Non-negotiable invariants

| Invariant | Status | Enforced by | Test |
|---|---|---|---|
| AI cannot bypass authorization | ✅ | assistant calls the same read services with `CurrentUser`; `IntentPolicy` | `approveRequestIsRoleAndVisibilityChecked`, `queriesUseAuthorizedDataOnly`, `modelCannotEscalateRoleOrInventIntents` |
| AI cannot bypass balance validation | ✅ | proposals execute only via `POST /leave-requests` | `confirmedProposalGoesThroughTheNormalEndpointWithAllRules` (422 INSUFFICIENT_BALANCE) |
| AI cannot bypass conflict detection | ✅ | same endpoint → `ConflictService` at submit/approve | proposal preview shows flag; decision cards require ack |
| AI cannot bypass the state machine | ✅ | no assistant → workflow dependency | `ArchitectureTest.assistantCannotReachTheWorkflowWriteSide` |
| AI cannot directly modify PostgreSQL | ✅ | ArchUnit: no repositories/JdbcTemplate/EntityManager/DataSource in `..assistant..` | `ArchitectureTest` (5), `assistantNeverWritesBusinessDataForAnyIntent` |
| Manager cannot bypass HR | ✅ | no transition from manager stage to APPROVED; stage explicit in API | `noEventMovesAManagerStageStateToApproved`, `managerCannotActAtHrStageAndCannotBypassHr` |
| HR cannot approve before HR stage | ✅ | state machine | `hrCannotApproveAtManagerStage`, `pendingManagerPlusHrApproveMustFail`, HTTP 409 test |
| Employees cannot approve own requests | ✅ | `AccessPolicy` (actor ≠ owner) | `employeeCannotApproveOwnRequest`, `hrEmployeeCannotApproveOwnRequestAtHrStage` |
| Same person cannot approve both stages | ✅ | four-eyes check | `samePersonCannotApproveBothStages`, `topLevelUserIsRoutedToHrProxy…`, `noSkipLevelFallsBackToHrPoolWithFourEyes` |
| Concurrent approvals cannot corrupt state | ✅ | row lock + `uq_action_one_decision_per_stage` | `twoSimultaneousApprovalsYieldExactlyOneDecision` |
| Concurrent submissions cannot overdraw | ✅ | balance row lock + `ck_balance_not_overdrawn` | `simultaneousBalanceConsumingRequestsCannotOverdraw` (10 parallel → exactly 2) |
| Escalation survives restart | ✅ | deadlines in DB; fresh job instance recovers | `escalationSurvivesRestartBecauseStateIsInTheDatabase` |
| Escalation cannot execute twice | ✅ | SKIP LOCKED + status re-check + `uq_escalation_request_stage` | `schedulerRunningConcurrentlyEscalatesExactlyOnce` |
| Rejected/cancelled release pending balance | ✅ | `BalanceService.releasePending/restoreUsed` | `managerRejectRequires…`, `hrRejectReleasesBalance`, `cancelPendingReleases…` |
| HR approval converts pending → used | ✅ | `commitPending` | `happyPathManagerThenHrMovesPendingToUsed` |
| Excess team leave → flag, never auto-reject | ✅ | `ConflictService` has no status writes | `conflictIsFlaggedNotRejected…` |
| Every consequential transition audited | ✅ | same-TX `AuditService`; denials in REQUIRES_NEW; append-only trigger | `everyConsequentialTransitionIsAudited`, `auditLogIsAppendOnly` |
| Core works when Sarvam unavailable | ✅ | `none` providers, rule-based extractor, circuit breakers | `degradedModeIsReportedAndCoreStillWorks`, `outageFallsBackToRules`, `SarvamClientTest.failuresBecome…` |

## 3. Blueprint items (§25 P0/P1)

| Item | Status | Notes |
|---|---|---|
| Flyway schema with DB invariants (CHECK, EXCLUSION, UNIQUEs, audit trigger) | ✅ | `V1__schema.sql`; verified on PostgreSQL 16.15 |
| JWT auth, demo quick-login, RBAC route + object layers | ✅ | `SecurityApiTest` (12) |
| Idempotency (clientRequestId, decision/cancel replay) | ✅ | `duplicateSubmit…`, `duplicateConcurrentSubmissions…`, HTTP replay test |
| Preview endpoint (read-only) | ✅ | used by Apply form and assistant card |
| Notifications (in-app, 30 s polling) | ✅ | bell in top bar |
| Manager team calendar, conflicts page | ✅ | |
| HR overview charts, audit viewer, balance-integrity, demo reset | ✅ | integrity check asserted in concurrency tests |
| Assistant text chat with clarification (§18.5 cases) | ✅ | `AssistantIntegrationTest` (13), `AssistantLlmPathTest` (8) |
| Voice (MediaRecorder → Sarvam STT → editable transcript) | ⚠️ | STT verified with real audio through `/assistant/transcribe`; browser mic capture not exercised with a physical microphone in this environment |
| Multilingual replies via Sarvam translation | ✅ | verified live (Hindi, Tamil) |
| TTS playback | ✅ | `/assistant/speak` verified live (WAV) |
| UI i18n en/hi/ta | ⚠️ | navigation, statuses, core screens translated; some secondary strings fall back to English |
| Rate limiting, circuit breaker, ArchUnit | ✅ | |
| Public HTTPS deployment | ❌ | backend host credentials not available (see §7) |

## 4. API list (`/api/v1`)

Auth: `POST /auth/login`, `POST /auth/demo-login`, `GET /auth/demo-accounts`, `GET /auth/me` ·
Reference: `GET /leave-types`, `GET /holidays?year=` ·
Employee: `GET /me/balances`, `GET /me/leave-requests`, `POST /leave-requests/preview`, `POST /leave-requests`, `GET /leave-requests/{id}`, `POST /leave-requests/{id}/decisions`, `POST /leave-requests/{id}/cancel` ·
Manager: `GET /manager/dashboard`, `/manager/approvals?scope=`, `/manager/team`, `/manager/conflicts`, `/manager/escalations` ·
HR: `GET /hr/approvals`, `/hr/escalations?open=`, `POST /hr/escalations/run`, `GET /hr/leave-requests`, `/hr/conflicts`, `/hr/team-calendar`, `/hr/overview`, `/hr/balance-integrity`, `/hr/teams`, `/hr/audit`, `POST /hr/demo/reset` ·
Notifications: `GET /notifications`, `/notifications/unread-count`, `POST /notifications/{id}/read`, `/notifications/read-all` ·
Assistant: `GET /assistant/status`, `POST /assistant/message`, `POST /assistant/transcribe`, `POST /assistant/speak` ·
Ops: `GET /actuator/health` (public), Swagger at `/swagger-ui.html`.

## 5. Database summary

`team`, `app_user`, `leave_type`, `holiday`, `leave_balance` (CHECK not-overdrawn, unique user/type/year), `leave_request` (EXCLUDE overlapping active ranges per employee via btree_gist, unique client_request_id, same-year CHECK, snapshots of team/approver/working days), `approval_action` (unique decision per stage), `conflict_flag`, `escalation` (unique per request/stage), `notification`, `audit_log` (append-only trigger), `assistant_interaction` (telemetry, no transcripts). Deviation: added `leave_request.manager_routed_to_hr` to make the top-level HR-proxy routing explicit.

## 6. Verification performed

- Backend: `mvn test` → 184/184 passed (incl. 3 live Sarvam calls: translation, TTS→STT round trip, Hindi LLM extraction).
- Frontend: `tsc --noEmit` clean; `vite build` succeeds.
- Browser (bundled SPA on embedded PostgreSQL): Arjun applied (preview showed Diwali excluded + conflict warning) → request flagged → Meera approved with acknowledgement → PENDING_HR → Hema final-approved → APPROVED; audit trail and escalation pages checked; the assistant (Sarvam) produced a proposal for Farhan that was confirmed into request #1008 (PENDING_MANAGER).
- Production artifact: `app.jar` with `prod,demo` profile against a fresh database → Flyway migrated, demo seeded, Sarvam enabled, SPA deep links served, security headers present, actuator locked.
- Not verified: Docker image build (Docker not installed here; CI job builds it), public HTTPS deployment.

## 7. Known limitations

1. **Not publicly deployed.** Only a Vercel token was provided. Vercel cannot host the Spring Boot backend (long-running JVM + scheduler) or PostgreSQL. A Railway (or Render) token/account is required; all config is ready (`Dockerfile`, `railway.json`, `render.yaml`).
2. Docker image not built locally (no Docker on this machine).
3. UI translations for Hindi/Tamil are partial; assistant replies are fully translated by Sarvam.
4. Offline (no-Sarvam) assistant understands English plus a few Hindi/Tamil keywords only.
5. Rate limits are in-memory per instance (fine for one instance).
6. Whole-day leave only; no carry-forward, admin CRUD, or email (P2 by design).
7. No automated frontend tests (typecheck + build + manual browser verification).
8. Demo quick-login is public when `APP_DEMO_ENABLED=true` (demo data only; disable for real use).
9. Frontend bundle is ~890 KB (Recharts); acceptable, could be code-split.

## 8. Environment variables

See [backend/.env.example](backend/.env.example). Required in production: `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`. Demo: `APP_DEMO_ENABLED`, `DEMO_USER_PASSWORD`. AI: `SARVAM_API_KEY` (with `AI_PROVIDER=auto`). All Sarvam endpoints/models default to the values verified on 2026-09-28 and can be overridden (`SARVAM_BASE_URL`, `SARVAM_*_PATH`, `SARVAM_*_MODEL`, `SARVAM_TTS_SPEAKER`).

## 9. Judge demo flow (≈6 minutes)

1. **Login page** → point out the one-click role accounts. Log in as **Farhan** → balance cards show pro-rating ("Joined 20 Jul → 5/12 × 18 = 7.5").
2. Open **Assistant** → switch language to हिन्दी → mic (or type) *"मुझे अगले सोमवार से दो दिन की आकस्मिक छुट्टी चाहिए"* → editable transcript → send → structured interpretation card (Mon 5 Oct – Tue 6 Oct, balance after, conflict warning) → **Confirm & submit** → request enters PENDING_MANAGER (🎙/chat icon in timeline).
3. Show clarification: *"take leave next week"* → assistant asks which days (never guesses).
4. Log in as **Meera** → dashboard: escalated (Chitra, red) and flagged (Bala ⚠) items → open Bala → heat strip with names → Approve → must tick the conflict acknowledgement → PENDING_HR. Point out there is no "final approve" button for a manager.
5. Log in as **Hema** → HR approvals → Final approve → APPROVED (pending → used).
6. **Escalations** page: Chitra escalated to Dev Raman, "late by …"; press *Run escalation check now*.
7. **Audit trail**: every transition, acknowledgement, denial, login, SYSTEM escalation, channel CHAT/VOICE.
8. Resilience story: unset `SARVAM_API_KEY` → assistant shows the degraded banner, rule-based answers still work, every screen works (proved by `degradedModeIsReportedAndCoreStillWorks`).
