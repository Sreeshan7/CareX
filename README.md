# CareX Leave

Leave management with a **Manager → HR approval chain**, an explicit state machine, automatic escalation, team-conflict **flagging** (never auto-rejection), pro-rated balances for mid-year joiners, role-based UIs for Employee / Manager / HR, a full audit trail, and a **voice + multilingual assistant (Sarvam AI)** that can only *propose*: a human confirms, and the normal API enforces every rule.

- Design blueprint: [implementation.md](implementation.md)
- Build log: [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md) · Checklist: [TODO.md](TODO.md) · Requirement audit: [FINAL_AUDIT.md](FINAL_AUDIT.md)

## Stack

Spring Boot 3.5 (Java 21) modular monolith · PostgreSQL 16 + Flyway · React 18 + TypeScript + Vite + TanStack Query + Tailwind · Sarvam AI (saaras:v3 STT, sarvam-105b, mayura:v1 translate, bulbul:v3 TTS). The build produces a single Docker image: the SPA is bundled into the jar and served from the same HTTPS origin.

```
backend/   Spring Boot API (+ bundled SPA at build time)
frontend/  React SPA
deploy/    optional Vercel frontend script
Dockerfile railway.json render.yaml .github/workflows/ci.yml
```

## Run locally (no Docker or PostgreSQL install needed)

Requirements: JDK 21+, Maven 3.9+, Node 20+.

```bash
cd frontend && npm ci && npm run build && cd ..
```

```bash
rm -rf backend/src/main/resources/static && mkdir -p backend/src/main/resources/static && cp -r frontend/dist/* backend/src/main/resources/static/
```

```bash
cd backend && mvn spring-boot:test-run -Dspring-boot.run.main-class=com.carex.leave.LocalDevApplication
```

Then open http://localhost:8080. `LocalDevApplication` starts a real embedded PostgreSQL 16 and runs the `demo` profile (seeded org, requests and quick-login buttons). It also reads a gitignored repo-root `.env`, so put `SARVAM_API_KEY=...` there to enable voice and multilingual features.

For frontend hot reload, run `npm run dev` in `frontend/`. Vite proxies `/api` to `:8080`.

## Tests

```bash
cd backend && mvn test
```

There are 184 tests. They run against **real PostgreSQL 16** (zonky embedded binaries, so no Docker is needed). The suite covers the full state machine (all 49 status×event pairs), pro-rating, workflow, concurrency races, escalation (exactly-once, restart-safe), IDOR/RBAC, audit immutability, the AI boundary (ArchUnit plus "no business-table writes"), and AI failure fallback. `SarvamLiveSmokeTest` runs only when `SARVAM_API_KEY` is set.

## Demo accounts (demo profile)

| Key | Name | Role | Notes |
|---|---|---|---|
| arjun | Arjun Kumar | Employee | Engineering; pending casual leave |
| bala | Bala Subramanian | Employee | request **flagged** (team absence 2/6 > 30%) |
| chitra | Chitra Nair | Employee | request seeded overdue → **escalated** to Dev on startup |
| divya | Divya Menon | Employee | sick leave awaiting HR; one rejected request |
| esha | Esha Patel | Employee | fully approved leave |
| farhan | Farhan Ali | Employee | **mid-year joiner** (joined on the 20th, two months ago) → pro-rated 7.5 annual / 3.5 casual |
| meera | Meera Iyer | Manager | manages Engineering |
| dev | Dev Raman | Manager | leads Leadership (Meera's manager / skip-level) |
| hema | Hema Krishnan | HR | HR head (manages People Ops) |
| harish, isha | Harish Rao, Isha Gupta | HR | |

Log in with the one-click buttons on the login page, or use `<key>@demo.carex.app` with the `DEMO_USER_PASSWORD` you configured. If none is configured, a random password is generated and only quick-login works.

## Deploy (Railway: recommended, single service)

1. Push this repo to GitHub.
2. In Railway, create a project, then **+ New → Database → PostgreSQL**.
3. **+ New → GitHub Repo** → this repo. Railway detects the root `Dockerfile` and `railway.json` (health check `/actuator/health`).
4. Set these service variables (Railway reference variables shown):

   | Variable | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod,demo` |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
   | `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` |
   | `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `JWT_SECRET` | output of `openssl rand -base64 48` |
   | `DEMO_USER_PASSWORD` | a strong password to share with judges |
   | `SARVAM_API_KEY` | your Sarvam key (server-side only) |

5. **Settings → Networking → Generate Domain**. This gives an HTTPS URL like `https://carex-leave.up.railway.app`.
6. Verify: `/actuator/health` returns `UP`, the login page loads, the quick-login buttons work, and Chitra's request shows **Escalated** within about a minute.

Rollback: Railway → Deployments → redeploy a previous build. Migrations are forward-only (Flyway).

**Fallback:** `render.yaml` provides the same image on Render (use a non-sleeping plan so the scheduler keeps running).

**Optional Vercel frontend:** Vercel cannot run the Spring Boot backend or PostgreSQL. If you still want the SPA on Vercel, deploy the backend first, then run:

```bash
BACKEND_URL=https://<backend-host> VERCEL_TOKEN=<token> ./deploy/vercel-deploy.sh
```

This proxies `/api/*` to the backend, so no CORS is needed.

## Security notes

- Secrets live only in environment variables. `.env` is gitignored, and the Sarvam key never reaches the browser or the logs.
- Authorization comes from the JWT principal plus DB-reloaded roles. Object-level `AccessPolicy` returns 404 for resources you can't see.
- The assistant cannot write. ArchUnit forbids its package from depending on the workflow write side, repositories or JDBC. Proposals are executed only by the regular endpoints after a human clicks Confirm.
