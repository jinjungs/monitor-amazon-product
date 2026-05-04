# Architecture

## 1. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 21 | Familiar, preferred by the brief |
| Framework | Spring Boot 3 | `@Scheduled`, JPA, MVC — everything needed in one dependency |
| Scraping | jsoup | Amazon renders prices server-side; no JS execution needed |
| ORM | Spring Data JPA | Swapping H2 (test) ↔ PostgreSQL (prod) = one config line |
| Database | PostgreSQL (prod) / H2 (test) | Production-grade, full SQL, zero schema change at 10x |
| Scheduler | Spring `@Scheduled` + `ThreadPoolTaskExecutor` | In-process, no broker, one deployment artifact |
| Notification | Slack webhook | Free, zero setup, verifiable by reviewer in real time |
| Templates | Thymeleaf + Chart.js | Server-rendered, no separate frontend build |
| Retry | Spring Retry | Declarative `@Retryable` — keeps scraper method clean |
| Logging | SLF4J + Logback + logstash-logback-encoder | Structured JSON logs, filterable by field |
| Infra | Docker + docker-compose | Required for PostgreSQL; Dockerfile included for the app |
| CI | GitHub Actions | Run tests on every push |

---

## 2. Architecture & Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Spring Boot Application                                                │
│                                                                         │
│  ┌──────────────────────────────────────────────────┐                  │
│  │  scheduling-1 thread  (@Scheduled every 1 hour)  │                  │
│  │                                                  │                  │
│  │  PriceMonitorScheduler.runPriceChecks()          │                  │
│  │    └─ for each product:                          │                  │
│  │         checkProduct(product)  ──────────────────┼──► returns       │
│  │         (@Async — non-blocking handoff)          │    immediately   │
│  └──────────────────────────────────────────────────┘                  │
│                    │ submits tasks                                      │
│                    ▼                                                    │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  ThreadPoolTaskExecutor  (concurrent, one thread per product)   │   │
│  │                                                                 │   │
│  │  ┌────────────────── price-check-N thread ─────────────────┐   │   │
│  │  │  (sequential / synchronous inside)                      │   │   │
│  │  │                                                         │   │   │
│  │  │  1. Storage READ                                        │   │   │
│  │  │     └─ findLastSuccessfulByProductId() → lastPrice      │   │   │
│  │  │                    │                                    │   │   │
│  │  │                    ▼                                    │   │   │
│  │  │  2. Scraper  (@Retryable: timeout/5xx only)            │   │   │
│  │  │     └─ jsoup HTTP GET ──────────────────────────────────┼───┼───┼──► Amazon
│  │  │        parse price  → currentPrice                     │   │   │
│  │  │                    │                                    │   │   │
│  │  │                    ▼                                    │   │   │
│  │  │  3. Storage WRITE  (@Transactional) ───────────────────┼───┼───┼──► PostgreSQL
│  │  │     └─ priceCheckRepository.save()                     │   │   │
│  │  │        status: ok | error | unavailable                │   │   │
│  │  │                    │                                    │   │   │
│  │  │                    ▼                                    │   │   │
│  │  │  4. Checker                                             │   │   │
│  │  │     └─ isSignificantDrop() → true / false              │   │   │
│  │  │                    │                                    │   │   │
│  │  │                    ▼                                    │   │   │
│  │  │  5. Notifier  (sync — blocks until Slack responds)     │   │   │
│  │  │     └─ SlackNotifier.send() ───────────────────────────┼───┼───┼──► Slack
│  │  │        exceptions caught & swallowed                   │   │   │
│  │  └─────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────┐                  │
│  │  Tomcat threads  (HTTP requests)                 │                  │
│  │  └─ GET /products, /dashboard                    │                  │
│  │  └─ GET /api/products/{id}/history ──────────────┼──► PostgreSQL    │
│  │  └─ POST/DELETE/PATCH /api/products              │                  │
│  └──────────────────────────────────────────────────┘                  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Async boundary:** `@Scheduled` → `checkProduct()` is the only async handoff. Everything inside `checkProduct()` runs sequentially in the same thread.

**Failure isolation:** one product's thread crashing does not affect others. Each product runs independently in `ThreadPoolTaskExecutor`.

**Known design gap:** `@Transactional` wraps the entire `checkProduct()` including `notifier.send()`. If the notifier threw an unchecked exception, the `price_check` save would roll back. Currently safe because `SlackNotifier` catches all exceptions — but storing and notifying should ideally be in separate transaction boundaries.

### Database Schema

```sql
CREATE TABLE products (
    id         BIGSERIAL PRIMARY KEY,
    url        TEXT UNIQUE NOT NULL,
    name       TEXT,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE price_checks (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    price      NUMERIC(10, 2),
    currency   VARCHAR(3) DEFAULT 'USD',
    status     VARCHAR(20) NOT NULL,  -- ok | error | unavailable
    error_msg  TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_price_checks_product_checked
    ON price_checks(product_id, checked_at DESC);
```

`created_at` / `updated_at` are managed automatically by Spring Data JPA Auditing (`BaseEntity` with `@CreatedDate` / `@LastModifiedDate`). `checked_at` on `price_checks` is the business timestamp — when the price was actually fetched.

---

## 3. UI

### Product Management (`GET /products`)
- List of tracked products with active/inactive toggle
- Form to add a new product URL + name
- Delete button per product
- Backed by REST endpoints (`POST /api/products`, `DELETE /api/products/{id}`)
- Changes take effect on the next scheduled tick — no restart required

### Price History Dashboard (`GET /dashboard`)
- Thymeleaf template, one Chart.js line chart per product
- Price history data injected server-side via `th:inline="javascript"`
- Data is fresh at page load — sufficient for hourly checks
- Also exposed as JSON: `GET /api/products/{id}/history`

---

## 4. Endpoints

### Pages (Thymeleaf)

| Method | Path | Description |
|---|---|---|
| `GET` | `/products` | Product management page — add / remove / toggle active |
| `GET` | `/dashboard` | Price history charts per product |

### REST API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/products` | List all products |
| `POST` | `/api/products` | Add a new product `{ url, name }` |
| `DELETE` | `/api/products/{id}` | Remove a product |
| `PATCH` | `/api/products/{id}/toggle` | Toggle active / inactive |
| `GET` | `/api/products/{id}/history` | Price check history as JSON (Stretch Goal #7) |

---

## 5. Tests

```
Scraper   → fetches current price from HTML
Storage   → persists price check, retrieves last recorded price
Checker   → decides whether to notify (pure logic, no I/O)
Notifier  → sends Slack POST
```

| Layer | Class | Type | What it catches |
|---|---|---|---|
| Scraper | `AmazonScraperTest` | Unit — `Jsoup.parse()` with HTML fixture in `src/test/resources/fixtures/` | Price extraction breaks if Amazon changes CSS class names |
| Storage | `PriceCheckRepositoryTest` | `@DataJpaTest` + H2 in-memory + `@Import(JpaConfig.class)` | Write/read roundtrip; `findLastSuccessfulByProductId` skips error rows and returns most recent ok |
| Checker | `PriceCheckerTest` | Plain unit — `MonitorProperties` constructed directly, no Spring | Absolute threshold, percentage threshold (OR logic), no alert on increase, no alert when either price is null |
| Notifier | `SlackNotifierTest` | WireMock standalone server on dynamic port | POST sent to correct path with `application/json`; body contains "Price Drop Alert"; Slack 500 does not throw |

### Key design notes

- `AmazonScraper.parseDocument(Document, String)` is package-private — exposed for testing without needing to mock HTTP
- `@DataJpaTest` loads only the JPA slice; `@Import(JpaConfig.class)` is required to activate `@EnableJpaAuditing`
- `AmazonProductApplicationTests` uses `@ActiveProfiles("test")` to load H2 instead of PostgreSQL for context load verification
