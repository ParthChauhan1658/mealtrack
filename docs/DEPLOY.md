# Deploying MealTrack (Render + Neon)

Nothing here has been run. These are the steps for you to do yourself. Total cost: free tiers only.

**Shape of the deployment**

```
Browser -> Render static site (dashboard) --VITE_API_BASE_URL--> Render web service (API, Docker) -> Neon PostgreSQL
```

## 0. Free-tier terms (checked 2026-11-22 from web search; verify on the vendor pages before relying on them)

| Service | What I found | Source |
|---|---|---|
| Render web service (free) | Spins down after 15 minutes without inbound traffic; wakes on the next request (docs say about a minute). 750 free instance hours per workspace per calendar month, shared across services; spun-down time is not counted. If the hours run out, free services are suspended until the next month. | [Render docs: Deploy for Free](https://render.com/docs/free) |
| Render Postgres (free) | **Expires 30 days after creation**, only one per workspace, no backups. This is why this guide uses Neon instead. | [Render changelog](https://render.com/changelog/free-postgresql-instances-now-expire-after-30-days-previously-90) |
| Neon (free) | 1 GB storage per project, 100 CU-hours of compute per project per month, compute suspends after 5 minutes idle (cannot be disabled on Free), storage overage makes writes fail rather than bill. Other pages list older, lower limits (0.5 GB / 50 CU-hours). | [Neon FAQ: free plan limits](https://neon.com/faqs/free-plan-limits-and-quotas) |
| Neon connections | TLS is required (`sslmode=require`). A pooled hostname has a `-pooler` suffix; the direct hostname does not. A woken compute can take a few seconds to accept connections. | [Neon connection errors](https://neon.tech/docs/connect/connection-errors) |

Not confirmed (**verify**): Render free web service RAM (I believe 512 MB; the image is tuned for that), and whether Render's free tier needs a payment method on file.

MealTrack's whole dataset (1,000 centres, ~26k reports) is a few MB, far under Neon's limit.

## 1. Create the Neon database

1. Sign up at neon.com and create a **Project** (any region near you; name `mealtrack`). A database and role are created for you.
2. Open the project's **Connect** dialog. Choose the **direct** connection (not "Pooled connection"), and copy the parts: host (`ep-....neon.tech`), database name, user, password.
3. Build the three values you will need. Note the JDBC form is different from the `postgresql://` URL Neon shows:

   | Env var | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://<host>/<database>?sslmode=require` |
   | `DB_USER` | the Neon role name |
   | `DB_PASSWORD` | the Neon role password |

   Flyway creates the tables on the API's first start, so there is nothing to run in Neon's SQL editor.

## 2. Generate a strong API key

This is the `MEALTRACK_API_KEY` that protects every write endpoint. Generate 32 random bytes:

```bash
# Git Bash / macOS / Linux
openssl rand -hex 32
```
```powershell
# Windows PowerShell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); ($b | ForEach-Object { $_.ToString('x2') }) -join ''
```

Save it in a password manager. You will paste it into the Render dashboard and, later, into the dashboard's "API key" box. Never commit it.

## 3. Push the repo

```bash
# create an EMPTY repo named mealtrack on github.com (no README/licence), then:
git remote add origin https://github.com/ParthChauhan1658/mealtrack.git
git push -u origin main
```

Check the **Actions** tab: the CI workflow should go green (backend tests need Docker, which GitHub's `ubuntu-latest` has). Then add the badge to the README (see the TODO comment there).

## 4. Deploy both services on Render from `render.yaml`

1. render.com -> **New** -> **Blueprint** -> connect your GitHub account -> select the `mealtrack` repo. Render reads `render.yaml` and proposes two services: `mealtrack-api` (Docker) and `mealtrack-dashboard` (static site).
2. Render asks for the values marked `sync: false`. Enter:

   | Variable | Value |
   |---|---|
   | `DB_URL` | the JDBC URL from step 1 |
   | `DB_USER` | Neon role |
   | `DB_PASSWORD` | Neon password |
   | `MEALTRACK_API_KEY` | the key from step 2 |
   | `CORS_ORIGIN` | the dashboard's URL. You do not know it yet: enter `https://placeholder.invalid` and fix it in step 5 |

   Already set by the blueprint: `LLM_PROVIDER=none` (rules only), `CUTOFF_TIME=14:00`, `DB_POOL_SIZE=5`, `MISSING_REPORT_CRON=-` (job disabled, see caveats), and the dashboard's `VITE_API_BASE_URL`.
3. Click **Apply**. The first Docker build takes several minutes. The API is ready when `https://<api>.onrender.com/actuator/health` returns `{"status":"UP"}`.

## 5. Point the two services at each other

The blueprint assumes the API gets the URL `https://mealtrack-api.onrender.com`. Render appends a suffix if the name is taken, so check the real URLs on each service's page.

- **How this differs from Docker Compose.** Locally, nginx serves the dashboard and forwards `/api` to the API, so the browser only ever talks to one origin. A Render static site has no server-side proxy that I could confirm, so the dashboard is built with `VITE_API_BASE_URL=<API url>` and calls the API directly from the browser. That makes it a cross-origin call, so the API must allow the dashboard's origin.
- If the API URL differs from `https://mealtrack-api.onrender.com`: dashboard service -> **Environment** -> set `VITE_API_BASE_URL` to the real API URL (no trailing slash) -> **Manual Deploy**. (It is read at build time, so a rebuild is required.)
- API service -> **Environment** -> set `CORS_ORIGIN` to the dashboard URL exactly, e.g. `https://mealtrack-dashboard.onrender.com` (no trailing slash, no path) -> Render redeploys the API.

## 6. Seed the Neon database (once, from your machine)

This runs the project's seed profile against Neon: 1,000 synthetic centres, 30 days of history, and the demo-day anomalies. It sends about 26,000 rows to Neon, so do it only when you are ready. It needs Docker running locally.

```bash
docker build -t mealtrack-api .
docker run --rm \
  -e DB_URL='jdbc:postgresql://<host>/<database>?sslmode=require' \
  -e DB_USER='<neon role>' -e DB_PASSWORD='<neon password>' \
  -e MEALTRACK_API_KEY=unused \
  -e SPRING_PROFILES_ACTIVE=seed -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  -e MEALTRACK_SEED_EXIT_WHEN_DONE=true \
  mealtrack-api
```

PowerShell: put it on one line, or end each line with a backtick instead of `\`.

Expect a log line like `Seeded 1000 centres, 26195 reports, demo day ... All data is synthetic.` and exit code 0. If centres already exist the seed skips itself. To wipe and re-seed add `-e MEALTRACK_SEED_RESET=true`.

## 7. Post-deploy smoke test

Replace `API`, `DASH` and `KEY`:

```bash
API=https://<api>.onrender.com ; DASH=https://<dashboard>.onrender.com ; KEY=<your key>
curl -s $API/actuator/health                                   # {"status":"UP"}  (first call after idle can take ~1 min)
curl -s -o /dev/null -w "%{http_code}\n" $API/swagger-ui/index.html   # 200
curl -s "$API/api/centres?size=1" | head -c 200                # "totalElements":1000 in the JSON
curl -s "$API/api/summary/daily" | head -c 300                 # totals for the latest day with data
curl -s -X POST $API/api/reports -H 'Content-Type: application/json' -d '{"centreCode":"GGN-0001","date":"2026-01-01","mealsServed":1}' -w " [%{http_code}]\n"   # 401: key required
curl -s -X POST $API/api/reports/parse -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"centreCode":"GGN-0002","message":"served 60 kids, 5 absent, total enrolled 70"}' -w " [%{http_code}]\n"   # 201 or 200, parseMethod RULES
curl -s -i -X OPTIONS $API/api/alerts/1/resolve -H "Origin: $DASH" -H "Access-Control-Request-Method: PATCH" | head -3   # 200 + Access-Control-Allow-Origin: $DASH
```

Then in the browser:

- [ ] Open `$DASH`: the "Demo with synthetic data." banner shows; cards, chart, district table and alerts all load (no red error boxes).
- [ ] Paste the API key into the key box, use the sample message "aaj 80 bachchon..." with centre `GGN-0001`: it saves, shows `RULES` / `HIGH`.
- [ ] Use the sample "khana nahi bana aaj, gas khatam": shows "Not saved: needs human review", and a new `PARSE_UNCERTAIN` alert appears.
- [ ] Click Resolve on an alert: it disappears from the table.
- [ ] Put the live URL at the top of the README and replace the CI-badge TODO.

## 8. Caveats to expect

- **Cold starts.** After 15 idle minutes the API sleeps; the next request takes about a minute (Render docs) plus JVM start. Open the dashboard a minute before a demo. Neon's compute also suspends after 5 idle minutes and takes a few seconds to wake; the API's first database call after that can be slow.
- **Instance hours.** One always-on free service uses about 720 of the 750 monthly hours, but sleeping services are not counted. Several services share the pool.
- **Demo data goes stale.** History is anchored to the day you seed; the dashboard opens on the latest day with data, and the trend chart shows the last 30 days, so it slowly empties. Re-seed with `MEALTRACK_SEED_RESET=true` before showing it again.
- **Why `MISSING_REPORT_CRON=-`.** With no live reporters, the 14:00 job would flag all 1,000 centres as missing every working day. Remove the variable to restore the default `0 0 14 * * MON-SAT`.
- **Single shared API key.** Anyone who has it can write. Rotate it in the Render dashboard if it leaks.
- **Neon storage limit.** If it were ever exceeded, writes fail rather than bill (per Neon's FAQ).
