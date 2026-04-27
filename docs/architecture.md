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
[application.yml]
  interval, threshold, thread-pool, slack webhook URL
        │
        ▼
[@Scheduled job — fires every 1 hour]
        │
        │  submits one task per active product
        ▼
[ThreadPoolTaskExecutor]
   │        │        │
   ▼        ▼        ▼
[Scraper]  [Scraper]  [Scraper]     ← jsoup, per-product isolation
   │
   ▼
[Storage]  ── write price_checks row (status: ok / error / unavailable)
   │       ── read last status='ok' price for this product
   │
   ▼
[Checker]  ── (current price, last price, threshold) → notify? true/false
   │
   ▼
[Notifier] ── HTTP POST to Slack webhook
```

**Each layer has one responsibility and no knowledge of layers above it.**

One scraper failure does not block others — each product runs in its own thread. Failures are recorded as `status='error'` and retried on the next tick naturally.

### Database Schema

```sql
CREATE TABLE products (
    id        BIGSERIAL PRIMARY KEY,
    url       TEXT UNIQUE NOT NULL,
    name      TEXT,
    active    BOOLEAN NOT NULL DEFAULT TRUE,
    added_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE price_checks (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    price      NUMERIC(10, 2),
    currency   VARCHAR(3) DEFAULT 'USD',
    status     VARCHAR(20) NOT NULL,  -- ok | error | unavailable
    error_msg  TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_price_checks_product_checked
    ON price_checks(product_id, checked_at DESC);
```

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

| Layer | Type | What it catches |
|---|---|---|
| Scraper | Unit — static HTML fixture in `src/test/resources` | Price extraction breaks if Amazon changes the DOM |
| Storage | `@DataJpaTest` with H2 in-memory | Write/read roundtrip; last-price query returns correct row |
| Checker | Plain unit test, no Spring context | Threshold logic (absolute + percentage OR); no false positive on price increase; null price handled |
| Notifier | WireMock mock server | Correct payload; HTTP call to right URL; failure does not crash scheduler |
