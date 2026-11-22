# Decisions and verification log

Judgement calls made while building MealTrack, and the real outputs from the final verification run
(2026-11-22, local machine, Docker Compose stack). Written so a reviewer can see *why*, not just *what*.

## Decisions

| # | Decision | Why |
|---|---|---|
| 1 | Maven wrapper + `maven.compiler.release=21` | The dev machine had JDK 25 and no Maven; the stack stays Java 21 / Maven. CI and Docker use JDK 21. `mvnw` is committed executable (`100755`) and forced to LF via `.gitattributes`. |
| 2 | `api.version=1.44` in the surefire config | Docker Engine 29 rejects the old API version docker-java/Testcontainers sends by default. |
| 3 | JVM default timezone forced to `Asia/Kolkata` | Cutoff times are IST, and PostgreSQL rejects the legacy `Asia/Calcutta` id. |
| 4 | `SUDDEN_DROP` is skipped when meals is 0 | `ZERO_MEALS` already reports that event. Without this, one zero-meal day raised two alerts, inflating the demo's "10 sudden drops" to 15. |
| 5 | A zero-meal *message* ("khana nahi bana") is LOW confidence | Per the spec, so a human confirms it rather than the system silently saving 0. |
| 6 | "Previous day" for `MISSING_REPORT` escalation skips Sunday | Centres do not report on Sundays; otherwise every Monday would be HIGH. |
| 7 | Hindi "kal" is read as *yesterday* | It is ambiguous (yesterday/tomorrow), but reports describe past meals. |
| 8 | `openAlerts` counts all open alerts across all dates | Matches the alerts table; decided by the project owner. |
| 9 | Demo day = today, or the previous Saturday if today is a Sunday | No reports exist on Sundays, so an empty "today" would make a poor demo. The dashboard also opens on the latest day with data. |
| 10 | School sizes drawn with exponent 1.25 | Tuned (1.4 gave 135,421; 1.25 gave 140,535 on a normal weekday) to land near 1.4 lakh while keeping the spec's 80-400 range. |
| 11 | Alerts are written with `INSERT ... ON CONFLICT` | Race-safe and idempotent without catching unique violations inside a transaction. |
| 12 | Report + anomaly evaluation in one transaction | A report never exists without its alerts; the unique-violation retry happens around the whole transaction. |
| 13 | LLM output is validated like any input; only transient errors are retried | Bad JSON or a bad key will not improve on retry; timeouts/429/5xx get up to 2 retries with backoff. |
| 14 | LLM clients tested against a **local stub only** | No real provider key was used. Default model ids (`claude-haiku-5-5`, `gemini-2.5-flash`) are unverified against live APIs. |
| 15 | Neon for the database, Render for API + dashboard | Render's free Postgres expires after 30 days; see `DEPLOY.md`. |
| 16 | Dashboard uses a build-time `VITE_API_BASE_URL` on Render | Same-origin (nginx `/api` proxy) is used in Docker Compose. On Render's static hosting I could not confirm that a rewrite can proxy to another host, so the dashboard calls the API directly and the API allows that origin via `CORS_ORIGIN`. Tested locally, including CORS preflight. |
| 17 | `MISSING_REPORT_CRON` is configurable; the public demo sets it to `-` (off) | With synthetic data and no live reporters, the daily job would flag all 1,000 centres every working day. |
| 18 | `DB_POOL_SIZE` is configurable (default 10) | Free-tier databases have small connection limits. |
| 19 | Pending work was already committed as M10/M11 before the "small commits" request | The M10 commit bundles Dockerfiles, compose and `render.yaml`. History was not rewritten (unpushed, but a rewrite adds risk for no functional gain). Later changes are separate commits. |
| 20 | `SPEC.md` is kept in the repo | It documents the requirements; the README is the front page. Delete it if you prefer. |

## Real outputs from the verification run

Stack: `docker compose up -d` (db, api, dashboard), then `docker compose run --rm seed`, from an empty volume.

```
seed:    Seeded 1000 centres, 26195 reports, demo day 2026-11-21 (40 MISSING_REPORT alerts). All data is synthetic.
health:  {"status":"UP"}      swagger-ui: 200      dashboard: 200
nginx proxy:  GET localhost:3000/api/summary/trend?days=2 -> [{"date":"2026-11-21","totalMeals":111563,...},...]
demo-day open alerts by type: MISSING_REPORT 40, SUDDEN_DROP 10, ZERO_MEALS 5, SPIKE 5, EXCEEDS_CAPACITY 2
weekday total (Fri 2026-11-20): 140535 meals, 966 of 1000 centres reported (96.6%)
trend: 30 points, 26 with data (Sundays have none)
```

API exercises (`X-API-Key: local-demo-key`):

```
POST /api/reports {GGN-0001, 2026-11-22, 70}   -> 201  {"id":26196,...,"mealsServed":70,"source":"API"}
POST /api/reports same centre/date, 75          -> 200  {"id":26196,...,"mealsServed":75}   (same id, updatedAt changed)
POST /api/reports mealsServed -5                -> 400  {"status":400,"message":"mealsServed must be greater than or equal to 0",...}
POST /api/reports without key                   -> 401  {"status":401,"message":"Missing or invalid X-API-Key",...}
POST /api/reports/parse "served 60 kids, 5 absent, total enrolled 70"
                                                -> 201  {"mealsServed":60,"parseMethod":"RULES","confidence":"HIGH","created":true,...}
POST /api/reports/parse "khana nahi bana aaj, gas khatam"
                                                -> 422  {"message":"Could not read this message with confidence (RULES found 0 meals). Nothing was saved; a human has been alerted to check it.",
                                                         "parsed":{"mealsServed":0,"date":"2026-11-22","parseMethod":"RULES","confidence":"LOW"}}
```

CORS (API built with `CORS_ORIGIN=http://localhost:3000`; dashboard built with `VITE_API_BASE_URL=http://localhost:8080`):

```
OPTIONS /api/alerts/1/resolve  Origin: http://localhost:3000   -> 200, Access-Control-Allow-Origin: http://localhost:3000, Methods: GET,POST,PATCH,OPTIONS
OPTIONS /api/alerts/1/resolve  Origin: http://evil.example     -> 403
```

Test data created by these exercises was deleted afterwards so screenshots show the clean seed.

## Not verified

- Claude/Gemini clients against a real provider.
- Render deployment, Neon connectivity, and the deployed dashboard (nothing was deployed).
- The GitHub Actions workflow has never run (no remote yet); it was validated as YAML and read carefully.
- Free-tier terms quoted in `DEPLOY.md` come from web search snippets; verify before relying on them.
