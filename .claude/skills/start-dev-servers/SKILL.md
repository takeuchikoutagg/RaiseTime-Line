---
name: start-dev-servers
description: Start RaiseTime Line's PostgreSQL (Docker, port 5434) and backend (Spring Boot, port 8081) dev servers. Use whenever asked to run, start, or verify the app locally. Always uses backend/start.sh — never gradlew bootRun directly — so a port conflict stops the existing process instead of falling back to a different port. Never touches Taskmanagement's servers (8080 / 5173 / 5433).
---

# Start dev servers

This project's dev servers must always come up on their fixed ports —
PostgreSQL on 5434, backend on 8081 — never on a fallback port.
`backend/start.sh` detects a LISTENing process on 8081, stops it, then
starts on that same port.

**Never run `./gradlew bootRun` directly** — always go through the
script below, so the port rule applies every time.

The ports are deliberately different from the sibling Taskmanagement
project (backend 8080 / frontend 5173 / PostgreSQL 5433), which is
often running at the same time. **Never stop processes on 8080, 5173
or 5433.**

The frontend (Vite, port 5174) does not exist yet; it will be added
here when `frontend/` is created.

## 1. PostgreSQL (Docker, port 5434)

```bash
docker info > /dev/null 2>&1 || open -a Docker   # start Docker Desktop if not running
# wait for the daemon:
until docker info > /dev/null 2>&1; do sleep 2; done
docker compose up -d
until docker exec raisetime-postgres pg_isready -U raisetime > /dev/null 2>&1; do sleep 1; done
```

## 2. Backend (Spring Boot, port 8081)

```bash
cd backend && nohup ./start.sh > /tmp/raisetime-backend.log 2>&1 &
```

Poll until ready (don't just sleep):

```bash
until grep -q "Started RaisetimeApplication" /tmp/raisetime-backend.log 2>/dev/null; do sleep 2; done
```

Flyway applies `backend/src/main/resources/db/migration/` on startup.

## 3. Verify

```bash
curl -s -o /dev/null -w "backend health: %{http_code}\n" http://localhost:8081/actuator/health
```

Should return `200`.

## Stop

```bash
lsof -ti tcp:8081 -sTCP:LISTEN | xargs -r kill
docker compose stop   # only this project's PostgreSQL (raisetime-postgres)
```

## Gotchas

- Don't `pkill -f` with a broad pattern — it can match the agent's own
  process, or Taskmanagement's servers, and kill them.
- If Docker Desktop was just launched, `docker compose up -d` can fail
  until the daemon socket exists — poll `docker info`, don't just
  sleep once.
- Backend tests (`./gradlew test`) need PostgreSQL running, because
  `@SpringBootTest` and the Mapper tests connect to the real database.
