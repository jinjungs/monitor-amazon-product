## Intro

### Time Management

- Target Time: 2–4 hours
- 2 hour: Planning & Research
  - Requirement deep dive
  - Clarify expectations and constraints
  - Decide architecture
- 30 minutes: Implementation
- 1 hour: Testing
- 30 minutes: Make a document

### Language and tooling

- Java & Spring Boot
- jsoup for scraping

why?
- I am familiar with Java and Spring Boot
- Python has mature ecosystem for web scraping, but there's no limitation for using jsoup in this case.


### AI Agent
: Claude code

---

## Core requirements

### 1. Monitor multiple products

- Track at least 3 Amazon product URLs
- Adding or removing a product must not require a code change or restart
- Product management via a small UI (add / remove / toggle active)
  - Product list is persisted in the database, not in `application.yml`
  - UI calls a REST endpoint to mutate the product list at runtime

### 2. Periodic price checks

- Scheduling: Spring `@Scheduled` with `fixedRateString` from `application.yml`
- Interval: 1 hour (default). Configurable via `monitor.interval-ms`.
  - Amazon prices rarely change more than once or twice a day
  - Below 5 min risks CAPTCHA / 503; 1 hour is safe and defensible
- Concurrency: one `@Scheduled` job fires per interval, submits one task per product to a `ThreadPoolTaskExecutor`
  - One failure or timeout does not block other products
  - Pool configured in `application.yml` via `@ConfigurationProperties`

```yaml
monitor:
  interval-ms: 3600000
  thread-pool:
    core-size: 3
    max-size: 10
```

### 3. Durable price history

- Storage: PostgreSQL (via docker-compose)
  - Production-standard, concurrency-safe, full SQL tooling
  - H2 for local tests only (in-memory, no extra infra needed for test runs)
- ORM: Spring Data JPA / Hibernate
  - Switching between H2 (test) and PostgreSQL (prod) = one `datasource.url` change, JPA layer untouched
- Schema:

```sql
CREATE TABLE products (
    id         BIGSERIAL PRIMARY KEY,
    url        TEXT UNIQUE NOT NULL,
    name       TEXT,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    added_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE price_checks (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES products(id),
    price       NUMERIC(10, 2),        -- NULL when scrape failed
    currency    VARCHAR(3) DEFAULT 'USD',
    status      VARCHAR(20) NOT NULL,  -- 'ok' | 'error' | 'unavailable'
    error_msg   TEXT,
    checked_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_price_checks_product_checked
    ON price_checks(product_id, checked_at DESC);
```

- Tradeoff (Design.md): H2 is zero-infra but dev-grade. PostgreSQL is one docker-compose service and defensible at any scale.
- Panel answer on 10x scale:
  - PostgreSQL handles 10x without schema changes — the index on `(product_id, checked_at DESC)` already covers the common query patterns
  - 100 products × 24 checks/day × 365 days ≈ 876,000 rows/year — trivial for PostgreSQL
  - The real 10x problem is **scraping**, not storage: Amazon rate limiting, thread pool sizing, notification flood
  - What would change at 100x+: read replicas, monthly partitioning on `price_checks`, or TimescaleDB (PostgreSQL extension for time-series — automatic hypertable partitioning and compression)

### 4. Price-drop detection and notification

- Detection: compare current price to last `status='ok'` record for the same product
- Threshold: both absolute and percentage, OR logic. Configurable in `application.yml`
  ```yaml
  monitor:
    threshold:
      absolute: 1.00    # notify if drop >= $1.00
      percentage: 2.0   # OR drop >= 2%
  ```
- Notification: **Slack webhook**
  - Free, no credit card, no SMTP setup
  - One HTTP POST to a webhook URL
  - Reviewer can verify in real time by watching the Slack channel
  - Webhook URL stored in environment variable — never in the repo

- Tradeoffs vs alternatives:
  | Method | Why not chosen |
  |---|---|
  | Email (Gmail SMTP) | Requires app password setup, fiddly auth, easy to land in spam |
  | SMS (Twilio) | Costs money per message |
  | Desktop toast | Not verifiable by a remote reviewer |
  | Web UI banner | Only visible if someone has the dashboard open at the moment of the drop |

### 5. Price history visualization

- Thymeleaf template served by Spring Boot (`GET /dashboard`)
- Controller fetches price history from DB, passes it to the template
- Chart.js renders a line chart per product (data injected server-side into `<script>` block via `th:inline="javascript"`)
- Data is fresh at page load — sufficient for hourly checks
- Live updates not needed for core requirement; page refresh covers it

### 6. Configurable parameters

- "Without a code change" includes editing a configuration file — `application.yml` qualifies
- All parameters are configurable without touching source code:

| Parameter | Where |
|---|---|
| Product list | DB, managed via UI at runtime (no restart needed) |
| Check interval | `application.yml` → `monitor.interval-ms` |
| Thread pool size | `application.yml` → `monitor.thread-pool.*` |
| Notification threshold | `application.yml` → `monitor.threshold.*` |
| Slack webhook URL | Environment variable `SLACK_WEBHOOK_URL` |
| DB credentials | Environment variables |

- Bound via `@ConfigurationProperties` — type-safe, validated at startup
- Secrets (webhook URL, DB password) go in env vars — never in the repo

### 7. Logging and observability

- Logging stack: SLF4J (API) → Logback (Spring Boot default impl) → `logstash-logback-encoder` (JSON formatter)
- Code only touches SLF4J — implementation is swappable without changing any logging calls
- Structured JSON output — filterable by field (`productId`, `status`, etc.) when multiple products fire concurrently

- What to log per **price check**:
  - `productId`, `url`, `price`, `currency`, `status` (`ok` / `error` / `unavailable`), `durationMs`, `timestamp`

- What to log per **notification**:
  - `productId`, `oldPrice`, `newPrice`, `dropAmount`, `dropPercentage`, `notificationStatus` (`sent` / `failed`)

- What to log on **failure**:
  - `productId`, `errorType`, `errorMessage`, full stack trace

- Extra dependency:
  ```xml
  <dependency>
      <groupId>net.logstash.logback</groupId>
      <artifactId>logstash-logback-encoder</artifactId>
      <version>7.4</version>
  </dependency>
  ```
- `logback-spring.xml` configures the JSON appender

### 8. Failure handling

- Individual product failures are isolated — one failure does not block other products (ThreadPoolTaskExecutor)
- Retry: Spring Retry (`@Retryable`) on the scraper method only — not all failures are worth retrying

| Failure | Retry? | How |
|---|---|---|
| Network timeout (`SocketTimeoutException`) | ✅ maxAttempts=2, delay=5s | Transient — worth one retry |
| 5xx server error | ✅ maxAttempts=2, delay=5s | Transient |
| 4xx client error | ❌ `noRetryFor` | Retrying won't change the outcome |
| Price element not found (`PriceParseException`) | ❌ `noRetryFor` | Page structure issue, not transient |
| CAPTCHA detected (`CaptchaException`) | ❌ `noRetryFor` | Retrying makes it worse |
| Slack notification failure | ❌ log and skip | Next tick re-evaluates naturally |
| DB connection failure | ❌ let it crash | Better to restart than silently lose data |
| Amazon IP ban | ❌ out of scope | Would need proxy rotation |

- No event reprocessing queue (outbox pattern):
  - Scraping failures are naturally retried on the next scheduled tick (1 hour) — no separate queue needed
  - Notification failures are a known gap — at production scale an outbox table would guarantee delivery, but over-engineering for this scope
  - This is a deliberate choice, not an oversight — worth stating in Design.md

### 9. Tests

#### Data flow (why the layers are split this way)

```
Scraper   → fetches the current price from Amazon HTML
Storage   → persists the new price check, retrieves the last recorded price
Checker   → (current price, last price, threshold) → decides whether to notify
Notifier  → sends the Slack webhook POST
```

Each layer has exactly one responsibility and knows nothing about the layers around it.
This is the Separation of Concerns the evaluation criteria explicitly calls out.

**Checker** is pure decision logic — no DB, no HTTP. Given current price, last price, and threshold config, it returns true/false. This makes it the easiest and most important layer to test.

---

#### Test strategy (JUnit 5)

| Layer | Test type | What it catches |
|---|---|---|
| **Scraper** | Unit test — parse static HTML fixture in `src/test/resources` | Price extraction breaks if Amazon changes the DOM structure |
| **Storage** | `@DataJpaTest` with H2 in-memory | Write + read roundtrip; last-price query returns the most recent `status='ok'` row |
| **Checker** | Plain unit test, no Spring context | Drop detected correctly; threshold logic (absolute + percentage OR); no false positives on price increase; null price (scrape failed) handled |
| **Notifier** | WireMock mock server for Slack endpoint | Correct payload format; HTTP call made to right URL; failure does not throw |

### 10. Legal and ethical considerations

- Ensure your solution **adheres to Amazon's terms of service regarding web scraping**.
- **Do not use or distribute personal data** without proper authorization and consent.
- If you use a paid API or LLM as part of the solution, keep your **API keys out of the repository**.

## Stretch goals (optional)

### Selected (will implement)

**3. Deployability** ✅
- Already needed: docker-compose for PostgreSQL is required, not optional
- Add `Dockerfile` for the Spring Boot app
- GitHub Actions CI: run tests on every push (~20 lines of YAML)

**6. Concurrency correctness** ✅
- Problem: if two scheduler threads fire at the same time (or process restarts mid-check), duplicate notifications could be sent
- Solution: DB-level unique constraint on `(product_id, DATE(checked_at))` prevents duplicate check records for the same product on the same day
- For notification deduplication: check within the same transaction — read last price and write new check atomically, so two concurrent threads can't both see a drop and both notify
- Document in Design.md

**7. REST export** ✅
- Near-zero effort alongside the Thymeleaf controller
- `GET /api/products` — list of tracked products
- `GET /api/products/{id}/history` — price check records as JSON
- Document the contract in README as if another team would consume it

### Skipped

| # | Reason |
|---|---|
| 1. Multi-source comparison | Scope creep, high effort |
| 2. Alert rules engine | New schema + rule evaluation, risky in time budget |
| 4. Live-updating dashboard | Nice to have; Thymeleaf page refresh is sufficient for hourly checks |
| 5. Cost/rate-limit awareness | No paid resources in this stack |
| 8. AI-assisted summary | Requires paid API, adds dependency |
| 9. Self-healing | Will describe degradation strategy in Design.md only |

---
## Deliverables
 
- GitHub repository 
- README.md
  - how to install
  - configure
  - run the application
  - how to verify it works end to end.
- 1-page design document
  - at least three real tradeoffs
  - why you chose the path you chose
  - Example: 
    - Storage choice
    - scheduling approach
    - notification strategy
- AI-NOTES.md
    - one thing your AI assistant got wrong or tried to oversimplify during this build,
    - how you caught it and corrected it
    - Short is fine; honest is better.

---
## Evaluation

- Functionality: does the application actually run end to end and produce correct notifications on a real price drop?
- Design clarity: can you articulate the tradeoffs in your 1-page design doc and defend them in conversation?
- Failure handling: what happens when something goes wrong, and what did you choose not to handle and why?
- Data model: is the history durably stored with a schema that makes sense?
- Separation of concerns: is scraping, storage, comparison, and notification cleanly decoupled, or is it all one file?
- AI collaboration: does your AI-NOTES entry show real judgment about where your tools helped and where they misled?
- Readability: can a reviewer understand the code and the design without running it first?

### Panel Discussion Points

> Think clearly about tradeoffs.

- Your design doc and the three tradeoffs you named.
- One place **where your solution could break** that you knew about and left alone, and why.
- One place **where you learned something unexpected** while building this.
- Your AI-NOTES entry, in detail.