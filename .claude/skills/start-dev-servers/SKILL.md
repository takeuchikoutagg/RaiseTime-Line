---
name: start-dev-servers
description: Start this task-management app's backend (Spring Boot, port 8080) and frontend (Vite, port 5173) dev servers, and PostgreSQL via Docker. Use whenever asked to run, start, or verify the app locally. Always uses backend/start.sh and frontend/start.sh — never gradlew/npm dev directly — so a port conflict stops the existing process instead of falling back to a different port.
---

# Start dev servers

This project's dev servers must always come up on their fixed ports —
backend on 8080, frontend on 5173 — never on a fallback port. Both
`start.sh` scripts already enforce this: they detect a LISTENing
process on the target port, stop it, then start on that same port.
`frontend/vite.config.js` also sets `server.strictPort: true`, so Vite
errors out instead of silently moving to 5174+ if the port is still
busy after that.

**Never run `./gradlew bootRun` or `npm run dev` directly** — always
go through the scripts below, so the port rule applies every time.

## 1. PostgreSQL (Docker)

```bash
docker info > /dev/null 2>&1 || open -a Docker   # start Docker Desktop if not running
# wait for the daemon:
until docker info > /dev/null 2>&1; do sleep 2; done
docker compose up -d
```

## 2. Backend (Spring Boot, port 8080)

```bash
cd backend && nohup ./start.sh > /tmp/backend-start.log 2>&1 &
```

Poll until ready (don't just sleep):

```bash
until grep -q "Started BackendApplication" /tmp/backend-start.log 2>/dev/null; do sleep 2; done
```

## 3. Frontend (Vite, port 5173)

```bash
cd frontend && nohup ./start.sh > /tmp/frontend-start.log 2>&1 &
```

Poll until ready:

```bash
until curl -sf http://localhost:5173 > /dev/null; do sleep 1; done
```

## 4. Verify

```bash
curl -s -o /dev/null -w "backend: %{http_code}\n" http://localhost:8080/api/board
curl -s -o /dev/null -w "frontend proxy: %{http_code}\n" http://localhost:5173/api/board
```

Both should return `200`. The frontend call goes through the Vite dev
proxy (`/api` → `http://localhost:8080`, configured in
`frontend/vite.config.js`), which is what avoids CORS in development.

## Stop

```bash
lsof -ti tcp:8080 -sTCP:LISTEN | xargs -r kill
lsof -ti tcp:5173 -sTCP:LISTEN | xargs -r kill
```

## Gotchas

- Don't `pkill -f` with a broad pattern — it can match the agent's own
  process and kill the session.
- If Docker Desktop was just launched, `docker compose up -d` can fail
  until the daemon socket exists — poll `docker info`, don't just
  sleep once.
- To actually see the rendered board (not just the API), open
  `http://localhost:5173` in a real browser (`open http://localhost:5173`
  on macOS) — this sandbox has no screen-recording permission, so an
  automated screenshot isn't available here; ask the user to confirm
  visually, or install a headless-browser tool if one is needed.
