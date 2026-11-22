#!/usr/bin/env python3
"""Submit N meal reports concurrently and report throughput and latency.

Standard library only. Uses the seeded centres (run the seed first), one report per centre, dated today,
with meals equal to each centre's expected_meals so every request is valid.

    python loadtest/load_test.py --base-url http://localhost:8080 --api-key <your MEALTRACK_API_KEY>
"""
import argparse
import http.client
import json
import os
import platform
import statistics
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse

IST = timezone(timedelta(hours=5, minutes=30))


def fetch_centres(base, limit):
    """Read active centres through the public API (200 per page)."""
    u = urlparse(base)
    conn = http.client.HTTPConnection(u.hostname, u.port or 80, timeout=30)
    out, page = [], 0
    while len(out) < limit:
        conn.request("GET", f"/api/centres?active=true&size=200&page={page}")
        data = json.loads(conn.getresponse().read())
        out.extend((c["code"], c["expectedMeals"]) for c in data["content"])
        if data["last"]:
            break
        page += 1
    return out[:limit]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default="http://localhost:8080")
    ap.add_argument("--api-key", required=True)
    ap.add_argument("--requests", type=int, default=1000)
    ap.add_argument("--concurrency", type=int, default=50)
    ap.add_argument("--json-out", default=None)
    a = ap.parse_args()

    centres = fetch_centres(a.base_url, a.requests)
    if len(centres) < a.requests:
        raise SystemExit(f"only {len(centres)} centres available; seed the database first")
    today = datetime.now(IST).date().isoformat()
    u = urlparse(a.base_url)
    local = threading.local()
    latencies, statuses = [], {}
    lock = threading.Lock()

    def send(item):
        code, meals = item
        if not hasattr(local, "conn"):  # one keep-alive connection per worker thread
            local.conn = http.client.HTTPConnection(u.hostname, u.port or 80, timeout=30)
        body = json.dumps({"centreCode": code, "date": today, "mealsServed": meals})
        start = time.perf_counter()
        try:
            local.conn.request("POST", "/api/reports", body,
                               {"Content-Type": "application/json", "X-API-Key": a.api_key})
            r = local.conn.getresponse()
            r.read()
            status = r.status
        except Exception:
            status = 0
            local.conn.close()
            del local.conn
        ms = (time.perf_counter() - start) * 1000
        with lock:
            latencies.append(ms)
            statuses[status] = statuses.get(status, 0) + 1

    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=a.concurrency) as pool:
        list(pool.map(send, centres))
    wall = time.perf_counter() - t0

    latencies.sort()
    pct = lambda p: latencies[min(len(latencies) - 1, int(len(latencies) * p / 100))]
    ok = sum(n for s, n in statuses.items() if s in (200, 201))
    result = {
        "requests": len(latencies),
        "concurrency": a.concurrency,
        "wall_seconds": round(wall, 2),
        "requests_per_second": round(len(latencies) / wall, 1),
        "latency_ms": {"p50": round(pct(50), 1), "p95": round(pct(95), 1), "p99": round(pct(99), 1),
                       "mean": round(statistics.mean(latencies), 1), "max": round(latencies[-1], 1)},
        "error_rate_percent": round(100 * (len(latencies) - ok) / len(latencies), 2),
        "status_counts": statuses,
        "machine": {"os": platform.platform(), "cpus": os.cpu_count(), "python": platform.python_version()},
    }
    print(json.dumps(result, indent=2))
    if a.json_out:
        with open(a.json_out, "w") as f:
            json.dump(result, f, indent=2)


if __name__ == "__main__":
    main()
