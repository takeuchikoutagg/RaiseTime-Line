---
name: start-dev-servers
description: Start RaiseTime Line's PostgreSQL (Docker, port 5434), backend (Spring Boot, port 8081) and frontend (Vite, port 5174) dev servers. Use whenever asked to run, start, or verify the app locally. Always uses backend/start.sh and frontend/start.sh — never gradlew bootRun / npm run dev directly — so a port conflict stops the existing process instead of falling back to a different port. Never touches Taskmanagement's servers (8080 / 5173 / 5433).
---

# Start dev servers

This project's dev servers must always come up on their fixed ports —
PostgreSQL on 5434, backend on 8081, frontend on 5174 — never on a
fallback port. Both `start.sh` scripts detect a LISTENing process on
the target port, stop it, then start on that same port.
`frontend/vite.config.js` also sets `server.strictPort: true`, so Vite
errors out instead of silently moving to another port.

**Never run `./gradlew bootRun` or `npm run dev` directly** — always go
through the scripts below, so the port rule applies every time.

The ports are deliberately different from the sibling Taskmanagement
project (backend 8080 / frontend 5173 / PostgreSQL 5433), which is
often running at the same time. **Never stop processes on 8080, 5173
or 5433.**

## 1. PostgreSQL (port 5434) and S3Mock (port 9090) via Docker

```bash
docker info > /dev/null 2>&1 || open -a Docker   # start Docker Desktop if not running
# wait for the daemon:
until docker info > /dev/null 2>&1; do sleep 2; done
docker compose up -d
until docker exec raisetime-postgres pg_isready -U raisetime > /dev/null 2>&1; do sleep 1; done
until curl -sf -o /dev/null http://localhost:9090/; do sleep 1; done
```

S3Mock (`raisetime-s3mock`, Adobe S3Mock) stands in for AWS S3 in
development: no authentication, bucket `raisetime-images` is created on
start, and uploaded images can be fetched at
`http://localhost:9090/raisetime-images/{key}`. The backend tests
(`S3ImageStorageTest`) also need it running.

## 2. Backend (Spring Boot, port 8081)

```bash
cd backend && nohup ./start.sh > /tmp/raisetime-backend.log 2>&1 &
```

Poll until ready (don't just sleep):

```bash
until grep -q "Started RaisetimeApplication" /tmp/raisetime-backend.log 2>/dev/null; do sleep 2; done
```

Flyway applies `backend/src/main/resources/db/migration/` on startup.

## 3. Frontend (Vite, port 5174)

First time only: `cd frontend && npm install`.

```bash
cd frontend && nohup ./start.sh > /tmp/raisetime-frontend.log 2>&1 &
```

Poll until ready:

```bash
until curl -sf http://localhost:5174 > /dev/null; do sleep 1; done
```

## 4. Verify

```bash
curl -s -o /dev/null -w "backend health: %{http_code}\n" http://localhost:8081/actuator/health
curl -s -o /dev/null -w "frontend: %{http_code}\n" http://localhost:5174/
curl -s -o /dev/null -w "frontend proxy (me, not logged in): %{http_code}\n" http://localhost:5174/api/auth/me
```

Expect `200`, `200` and `401`. The last call goes through the Vite dev
proxy (`/api` → `http://localhost:8081`, configured in
`frontend/vite.config.js`), which is what avoids CORS in development
and lets the auth cookies work as-is.

To see the screens, open `http://localhost:5174` in a real browser
(`open http://localhost:5174` on macOS). This sandbox cannot take
screenshots, so ask the user to confirm visually.

## Stop

```bash
lsof -ti tcp:8081 -sTCP:LISTEN | xargs -r kill
lsof -ti tcp:5174 -sTCP:LISTEN | xargs -r kill
docker compose stop   # only this project's containers (raisetime-postgres, raisetime-s3mock)
```

## Gotchas

- Don't `pkill -f` with a broad pattern — it can match the agent's own
  process, or Taskmanagement's servers, and kill them.
- If Docker Desktop was just launched, `docker compose up -d` can fail
  until the daemon socket exists — poll `docker info`, don't just
  sleep once.
- Backend tests (`./gradlew test`) need PostgreSQL running, because
  `@SpringBootTest` and the Mapper tests connect to the real database.
