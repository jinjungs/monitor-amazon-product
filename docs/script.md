# Interview Scripts

---

## 1. Project Intro

"I built an Amazon price monitor system that tracks a configurable set of products, persists every price check, and sends notifications when a meaningful price drop is detected.

The stack is Java with Spring Boot — which the requirements preferred, and I'm most comfortable there. Spring Boot gave me everything I needed out of the box: `@Scheduled` for periodic checks, JPA for persistence, and Spring Retry for resilient scraping. PostgreSQL via Docker for storage, and a Slack webhook for notifications.

The part I found most interesting was thinking through where the system could break, and making deliberate decisions about what to handle versus what to leave out. 

I intentionally kept the system simple to avoid over-engineering, but if it were to scale to multiple users or servers, I would revisit those trade-offs and introduce more robust solutions."

---

## 2. Architecture & Implementation

"The core flow is: a scheduled job fires every hour, submits one async task per product to a thread pool, and each task runs scrape → store → compare → notify sequentially in its own thread.

Each layer has one responsibility and no knowledge of layers above it — scraper just parses HTML, checker just compares prices against a threshold, notifier just sends a Slack POST.

The async boundary is only at the scheduler handoff. Everything inside a product's thread is synchronous. That was a deliberate choice — it keeps the flow easy to trace and reason about."

---

## 3. Choice & Tradeoffs

"I made four main tradeoffs.

First, Java over Python. Python has a stronger scraping ecosystem, but jsoup is fully sufficient here — Amazon renders prices server-side, so there's no JavaScript execution needed. The scraping gap between the two languages is zero for this use case.

Second, PostgreSQL over H2. H2 would have been simpler, but it's dev-grade. PostgreSQL adds one Docker service and gives me a defensible answer at any scale. The JPA layer is identical either way.

Third, in-process scheduling over Quartz or Celery. A broker dependency isn't justified for a handful of products on an hourly interval. If the process restarts mid-check, the next tick resumes from DB state within an hour — acceptable for a price monitor.

Fourth, Slack webhook over email or SMS. It's free, zero setup, and a reviewer can verify it works in real time."

---

## 4. Known Gaps

"There are four things I knowingly left out.

Amazon bot detection — a plain HTTP client is detectable. I handle it gracefully by recording status as unavailable and moving on, but I don't solve it. Proxy rotation would be the real fix.

No authentication — this is a single-user tool running locally, so it's not a concern here. Spring Security would be the first addition for a public deployment.

No notification retry — if Slack is down at the moment of a price drop, that notification is lost. An outbox pattern would guarantee delivery, but it's disproportionate complexity for hourly monitoring.

Cross-instance deduplication — within one instance, the DB transaction prevents duplicates. If two instances ran simultaneously, both could notify. A Redis distributed lock would solve that."

---

## 5. Production Scale

"At 10x — say 100 products — PostgreSQL handles it without schema changes. The index on product ID and check time already covers the query patterns. The real bottleneck at 10x isn't storage, it's scraping: Amazon will start blocking an IP that hits it dozens of times an hour from the same address.

At 100x, I'd look at read replicas for the dashboard queries, monthly partitioning or TimescaleDB for the price history table, and a distributed lock or dedicated job service to replace the in-process scheduler."

---

## 6. AI-NOTES

"I tracked six issues across two categories.

Some were underspecified on my side — I didn't give Claude enough context, so it made reasonable choices that didn't match my intent. For example, I never explicitly said 'fetch the price immediately when a product is registered,' so Claude just saved the product and moved on. Obvious in hindsight, but I should have specified it.

Others were genuine implementation errors by Claude. The most interesting one was the retry logic — Claude added HttpStatusException to noRetryFor to fix a 404 bug, but HttpStatusException covers both 4xx and 5xx. The spec clearly said 5xx should be retried, but Claude fixed 4xx without cross-checking that it wasn't also breaking 5xx.

The pattern I noticed: Category A gaps shrink when requirements are more explicit and CLAUDE.md is kept up to date. Category B gaps shrink when you test right after each implementation block instead of at the end."

---

## 7. Retrospective

"If I did this again, three things would change.

First, I'd keep CLAUDE.md updated with key constraints as I made decisions — not just in conversation context, which is session-scoped. That would have prevented Claude from breaking the 5xx retry behavior when fixing the 404 bug.

Second, I'd write tests alongside each implementation block, not at the end. Tests force you to define expected behavior precisely, which is exactly where the requirement gaps were hiding.

Third, I'd write integration tests for the full flow — scrape to notification — using mocks, not manual DB manipulation. That's the only way to verify the end-to-end behavior automatically."
