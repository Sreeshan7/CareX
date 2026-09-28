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
- [ ] 20. Employee UI
- [ ] 21. Manager UI
- [ ] 22. HR UI
- [ ] 23. Deployment (Dockerfile, railway.json, README steps)

## AI (P1)
- [ ] 24. AI provider abstraction (STT/TTS/Translate/LLM ports, fake + none)
- [ ] 25. Sarvam adapter (configurable; endpoints unverified until docs arrive)
- [ ] 26. Voice (MediaRecorder → transcribe → editable transcript)
- [ ] 27. Chatbot (role-aware intents, templated answers, proposals only)
- [ ] 28. Multilingual polish (UI en/hi/ta; assistant replies via translation)

## Verification
- [ ] ArchUnit: assistant cannot depend on workflow write side / repositories
- [ ] Frontend typecheck + build
- [ ] Local end-to-end run (backend + bundled SPA on embedded Postgres) exercised in a browser
- [ ] Public HTTPS deployment (needs Railway access)
