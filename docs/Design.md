# Design Document

## Objective

Build a system that monitors a configurable set of Amazon product prices, persists every check, and sends a notification when a meaningful price drop is detected.

---

## Tradeoff 1 — Language: Java over Python

**Chosen:** Java 21 + Spring Boot 3

| | Java + Spring Boot | Python + FastAPI/APScheduler |
|---|---|---|
| **Pros** | Preferred by the brief; familiar; `@Scheduled`, JPA, Retry all built-in | Mature scraping ecosystem; faster to prototype |
| **Cons** | More boilerplate than Python | Weaker justification for scraping advantage (jsoup handles Amazon fine) |

**Why Java:** The brief explicitly prefers Java, and the problem maps naturally to Spring primitives. jsoup parses Amazon's server-side rendered HTML just as well as Python's BeautifulSoup — there is no scraping limitation that would justify switching languages.

**Tradeoff:** Python would have been faster to prototype under a strict time budget. Java produces a more defensible solution given the panel context.

---

## Tradeoff 2 — Storage: PostgreSQL over H2

**Chosen:** PostgreSQL via docker-compose

| | PostgreSQL | H2 (file mode) |
|---|---|---|
| **Pros** | Production-grade; full SQL tooling; defensible at any scale | Zero infrastructure; single config line; no Docker required |
| **Cons** | Requires Docker to run locally | Dev-grade only; hard to defend in production discussion |

**Why PostgreSQL:** H2 file mode works for this scope but is not what you would run in production. PostgreSQL adds one docker-compose service and the JPA layer stays identical — switching between H2 (tests) and PostgreSQL (prod) is a single `datasource.url` change.

**Tradeoff:** H2 would have eliminated Docker as a dependency and simplified setup. The cost of PostgreSQL is worth the defensibility.

**At 10x scale:** Schema unchanged. The index on `(product_id, checked_at DESC)` covers all query patterns. At 100x+: read replicas, monthly partitioning, or TimescaleDB.

---

## Tradeoff 3 — Scheduling: `@Scheduled` over Quartz / External Queue

**Chosen:** Spring `@Scheduled` + `ThreadPoolTaskExecutor` (in-process)

| | `@Scheduled` in-process | Quartz / Celery + broker |
|---|---|---|
| **Pros** | Single deployment artifact; no broker; zero config | Job persistence across restarts; distributed execution; dedicated retry |
| **Cons** | In-flight jobs lost on restart; no cross-instance deduplication | Requires Redis or DB-backed job store; significant configuration overhead |

**Why `@Scheduled`:** A handful of products on an hourly interval does not justify a broker dependency. If the process restarts mid-check, the next tick resumes from DB state within one hour — acceptable for a price monitor.

**Tradeoff:** Cross-instance duplicate notifications are not prevented. If two app instances run simultaneously, both may notify. Within a single instance, DB transaction isolation prevents duplicates.

---

## Tradeoff 4 — Notification: Slack Webhook over Email / SMS

**Chosen:** Slack webhook

| | Slack Webhook | Email (SMTP) | SMS (Twilio) |
|---|---|---|---|
| **Pros** | Free; zero setup; verifiable in real time | Universal; no account needed | Reaches anywhere |
| **Cons** | Requires a Slack workspace | SMTP auth friction; spam risk | Costs money per message |

**Why Slack:** A single HTTP POST, free, and a reviewer can verify it works by watching a channel in real time. The `Notifier` interface abstracts the delivery method — swapping to email or SMS is one new class, no changes to scheduler or checker logic.

**Tradeoff:** Delivery failures are logged and silently dropped. If Slack is down at the moment of a drop, that notification is lost. An outbox pattern would guarantee delivery — not implemented as the complexity is disproportionate to this scope.

---

## Known Gaps (Left Intentionally)

| Gap | Why left |
|---|---|
| Amazon bot detection / CAPTCHA | Proxy rotation or headless browser required — out of scope. Handled gracefully: recorded as `status='unavailable'`, no notification triggered |
| No authentication | Single-user tool on private infrastructure. Spring Security is the natural next step for multi-user deployment |
| No notification retry | Fire-and-forget is acceptable for hourly monitoring. Outbox pattern is the production solution |
| No cross-instance deduplication | Single-instance deployment assumed. Redis distributed lock would be needed at scale |

---

## What I Would Change at Production Scale

| Component | Now | At scale |
|---|---|---|
| Scraping | jsoup direct HTTP | Proxy rotation or product data API |
| Scheduler | In-process `@Scheduled` | Distributed lock (Redis) or dedicated job service |
| Storage | Single PostgreSQL | Read replica + time-series partitioning |
| Notifications | Slack fire-and-forget | Outbox table with retry |
