# TODO

Legend: `[x]` implemented **and** verified · `[~]` implemented, verification limited (see note) · `[ ]` not done

## Core product (P0)
- [x] 1. Project skeleton (Spring Boot 3.5, Java 21 target)
- [x] 2. PostgreSQL + Flyway (V1 schema, V2 reference data) — verified on PostgreSQL 16.15
- [x] 3. Users / roles / teams (+ HR head, skip-level, HR-proxy routing)
- [x] 4. Authentication (BCrypt + HS256 JWT, role reloaded from DB, demo login)
- [x] 5. Leave types + holidays
- [x] 6. Leave balances (lazy, race-free creation)
- [x] 7. Leave request domain + validation rules §9.5
- [x] 8. Balance reservation (pending reserves; CHECK backstop)
- [x] 9. Approval steps (approval_action timeline, one decision per stage)
- [x] 10. State machine (15 transitions; 49-pair test)
- [x] 11. Manager approval
- [x] 12. HR approval (+ four-eyes)
- [x] 13. Rejection (comment required, releases pending)
- [x] 14. Cancellation (pending / approved-before-start)
- [x] 15. Audit (same-TX, append-only trigger, denials in REQUIRES_NEW)
- [x] 16. Conflict detection (flag + acknowledgement, never rejects)
- [x] 17. Escalation scheduler (SKIP LOCKED, exactly-once, restart-safe)
- [x] 18. Idempotency (clientRequestId unique; decision/cancel replay)
- [x] 19. Concurrency tests
- [x] 20. Employee UI
- [x] 21. Manager UI
- [x] 22. HR UI
- [~] 23. Deployment config done (Dockerfile, railway.json, render.yaml, CI, Vercel script); prod jar verified locally; **public deploy blocked on backend-host credentials**

## AI (P1)
- [x] 24. AI provider abstraction (ports; sarvam | none; test fakes)
- [x] 25. Sarvam adapter — verified against docs + live API
- [~] 26. Voice — STT/TTS verified live through the API; browser mic capture not exercised with a physical mic here
- [x] 27. Chatbot (role-aware intents, templated answers, proposals only)
- [~] 28. Multilingual — assistant replies translated live (hi/ta verified); UI hi/ta partially translated

## Verification
- [x] ArchUnit: assistant cannot depend on workflow write side / repositories / JDBC
- [x] Frontend typecheck + build
- [x] Local end-to-end run exercised in a browser (employee → manager → HR, assistant proposal)
- [ ] Public HTTPS deployment (needs Railway or Render access)
- [ ] Docker image build (no Docker locally; CI builds it)
