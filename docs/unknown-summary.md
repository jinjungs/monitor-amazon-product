# Unknown Summary

Topics covered in `unknown.md` — one line per item.

## Covered ✅

| Topic | Summary |
|---|---|
| Docker volume | Named volume (`pgdata`) persists data across container restarts; deleted only with `down -v` |
| Dockerfile vs docker-compose | Dockerfile = single image build recipe; docker-compose = multi-container orchestration |
| H2 usage | Test only (`@ActiveProfiles("test")`); prod always PostgreSQL |
| PostgreSQL scale | Capacity not the bottleneck — scraping throughput and scheduler distribution are |
| TimescaleDB | PostgreSQL extension for time-series; auto-partitioning + compression; relevant at 100x+ scale |
| PostgreSQL vs MySQL | Both work here; PostgreSQL chosen for TimescaleDB extension path and Spring Boot ecosystem standard |
| @Value vs @ConfigurationProperties | `@Value` = single field; `@ConfigurationProperties` = grouped object binding, reusable across beans |
| @Scheduled + SpEL | Annotation attributes require compile-time constants; SpEL `${}` is the workaround for runtime config |
| @Async default executor | No name → `SimpleAsyncTaskExecutor` (no pool, new thread per call) — always specify explicitly |
| coreSize vs maxSize | core = always-alive threads; max = burst ceiling; excess threads terminate after load |
| @Transactional + notifier gap | Save and send are in the same transaction; notifier exception would rollback price_check save |
| Outbox Pattern | Store "pending notification" in DB atomically with price_check; separate poller handles delivery |
| Notification sync call | `notifier.send()` blocks the price-check thread; safe now but should be separated |
| Multi-user extension | Add `users` table, `user_id` FK on products, per-user `user_settings`; dedup scraping by URL |
| Auth not needed locally | Risk only when publicly exposed; localhost is safe |
| SLACK_WEBHOOK_URL security | env var is correct without auth; UI input is fine only with auth + HTTPS + masking |
| Dashboard UX gap | All products shown at once; better UX = click product → show its chart |
| @Retryable spec vs impl | 5xx should retry per spec but was excluded alongside 4xx when fixing the 404 bug |
| REST plural naming | `/api/products` not `/api/product` — URL represents the collection, not a singleton |
| PATCH vs POST | POST = create; PATCH = partial update; PUT = full replace |
| Useful phrases | Phrases for "I don't know" in English |
| Retrospective | Underspecified requirements + CLAUDE.md not maintained + test-last approach = gaps |
| Production config separation | User business settings (threshold, webhook) → DB + UI; infra settings stay in config file |
| Concurrency — single instance | @Transactional guarantees atomicity, not dedup; same product never assigned to two threads in one tick |
| Concurrency — multi instance | Redis lock, dedicated scheduler server, or message queue depending on scale |
| Message queue architecture | Scheduler publishes per-product messages; Workers consume and call checkProduct(); same JAR, different ROLE env var |
| Quartz vs @Scheduled vs Spring Batch | @Scheduled = simple timer; Quartz = persistent + clustered scheduler; Spring Batch = chunk-based data processing, not a scheduler |

---

## To Explore 🔲

| Topic | Notes |
|---|---|
| Test strategy — how tests were built | Walk through each layer's test and why |

---

## Interview Prep 🔲

| Item | Status |
|---|---|
| Project / Architecture / Implementation explanation practice | 🔲 |
| Tradeoffs, Known Gaps, Production Scale explanation (Design.md) | 🔲 |
| AI-NOTES explanation (two perspectives) | 🔲 |
| Useful expressions for interview | 🔲 |
| Short script — project intro | 🔲 |
| Short script — architecture & implementation | 🔲 |
| Short script — choice & tradeoffs | 🔲 |
| Short script — known gaps | 🔲 |
| Short script — production scale | 🔲 |
| Short script — AI-NOTES | 🔲 |
| Short script — retrospective | 🔲 |
| How to stand out as a candidate | 🔲 |
