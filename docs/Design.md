# Design Document

## Objective

Build a system that monitors a configurable set of Amazon product prices, persists every check, and sends a notification when a meaningful price drop is detected.

---

## Language & Stack Choice

**Java 21 + Spring Boot 3**

The brief prefers Java. More importantly, the problem maps naturally to Spring's built-in primitives: `@Scheduled` for periodic checks, Spring Data JPA for persistence, and Spring Retry for resilient scraping. The alternative (Python + APScheduler + SQLAlchemy) would have solved the same problem but offered no meaningful advantage given familiarity with the Spring ecosystem.

Scraping is handled by **jsoup**. Amazon renders product prices server-side for SEO — the price is present in the raw HTML response and does not require JavaScript execution. jsoup is sufficient for this and carries no meaningful limitation compared to Python's BeautifulSoup for this specific use case.

---

## Tradeoff 1 — Storage: PostgreSQL over H2

**Chosen:** PostgreSQL via docker-compose  
**Alternative considered:** H2 in file mode (zero infrastructure, embedded in the app)

H2 file mode would have eliminated the need for Docker entirely and simplified local setup. However, H2 is a development-grade database — not what you would run in production, and harder to defend in a panel discussion. PostgreSQL is the correct choice for a system that needs to durably persist price history.

The cost is one additional docker-compose service. The JPA layer is identical either way — switching between H2 (tests) and PostgreSQL (prod) requires changing a single `datasource.url` config line.

**At 10x scale (100+ products):** PostgreSQL handles the write volume without schema changes. The index on `(product_id, checked_at DESC)` already covers all query patterns. The real 10x problem is scraping throughput and Amazon rate limiting, not storage.

---

## Tradeoff 2 — Scheduling: In-Process `@Scheduled` over External Worker

**Chosen:** Spring `@Scheduled` + `ThreadPoolTaskExecutor` (in-process)  
**Alternative considered:** Celery (Python) or Quartz Scheduler with a job store

An external task queue (Celery + Redis, or Quartz + DB-backed job store) would provide job persistence across restarts, distributed execution, and a dedicated retry infrastructure. However, it also requires a broker dependency and significantly more configuration.

For a system that checks a handful of products on an hourly interval, in-process scheduling is the right fit. If the process restarts mid-check, the in-flight job is lost — but the next scheduled tick resumes normally from DB state within at most one interval. This is an acceptable gap for a price monitor where hourly resolution is sufficient.

**Known breakage point:** if two instances of the app run simultaneously (e.g. a deploy overlap), both will scrape and potentially both will notify. Addressed via DB-level transaction isolation: the last-price read and new check write happen atomically, so duplicate notifications are prevented within a single instance. Cross-instance deduplication would require a distributed lock (Redis), which is out of scope here.

---

## Tradeoff 3 — Notification: Slack Webhook over Email or SMS

**Chosen:** Slack webhook  
**Alternatives considered:** Email (Gmail SMTP), SMS (Twilio)

Email requires SMTP authentication, is prone to landing in spam, and adds setup friction for a reviewer verifying the system end-to-end. SMS costs money per message. A Slack webhook is a single HTTP POST, free, and verifiable in real time by watching a channel.

The notification layer is abstracted behind a `Notifier` interface. Swapping to email or SMS means implementing one new class — no changes to the scheduler or checker logic.

**Notification failures** are logged and silently dropped. A delivery failure does not crash the scheduler. The tradeoff: if Slack is down at the moment of a price drop, that specific notification is lost. At production scale, an outbox table with retry would guarantee delivery — not implemented here as it adds complexity disproportionate to the scope.

---

## What I Would Change at Production Scale

| Current | At scale |
|---|---|
| Single-instance in-process scheduler | Distributed lock (Redis) or dedicated job service |
| Slack webhook | Pluggable notification with outbox pattern for guaranteed delivery |
| Direct HTML scraping (jsoup) | Proxy rotation or a product data API to handle bot detection |
| One PostgreSQL instance | Read replica for dashboard queries; time-series partitioning on `price_checks` |

---

## Known Gaps (Left Intentionally)

- **Amazon bot detection:** a plain HTTP client is detectable. The system handles CAPTCHA gracefully (records `status='unavailable'`, no notification) but does not solve it. Proxy rotation is out of scope.
- **No authentication:** single-user tool running on private infrastructure. Spring Security would be the natural addition for a multi-user deployment.
- **No notification retry:** delivery failures are logged and accepted. Next scheduled check re-evaluates the price independently.
