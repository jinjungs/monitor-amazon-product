# Implementation Checklist

## Step 1 — Project Setup
- [x] Generate project with Spring Initializr (dependencies below)
- [x] Configure `application.yml` (interval, threshold, thread-pool, datasource)
- [x] Create `.env.example`
- [x] Update `.gitignore` (`.env`, `data/`, `target/`)

**Dependencies:** Spring Web, Spring Data JPA, Spring Scheduler, Thymeleaf, PostgreSQL Driver, H2, Lombok, Spring Retry, AOP, logstash-logback-encoder

---

## Step 2 — Storage Layer
- [ ] `Product` JPA entity
- [ ] `PriceCheck` JPA entity
- [ ] `ProductRepository`
- [ ] `PriceCheckRepository` (include query for last `status='ok'` price)
- [ ] `schema.sql` for PostgreSQL (or Flyway migration)
- [ ] H2 config for test profile (`application-test.yml`)

---

## Step 3 — Scraper Layer
- [ ] Define custom exceptions (`PriceParseException`, `CaptchaException`)
- [ ] `AmazonScraper` — jsoup, parse `span.a-price-whole` + `span.a-price-fraction`
- [ ] Apply `@Retryable` (retry on timeout / 5xx only, `noRetryFor` on parse/CAPTCHA exceptions)
- [ ] Apply `@Recover` — record `status='error'` after retries exhausted

---

## Step 4 — Checker + Notifier
- [ ] `PriceChecker` — absolute OR percentage threshold logic
- [ ] `Notifier` interface
- [ ] `SlackNotifier` — HTTP POST to webhook URL, read from env var

---

## Step 5 — Scheduler
- [ ] `ThreadPoolTaskExecutor` bean (configured from `application.yml`)
- [ ] `@Scheduled` job — fetch active products, submit one task per product
- [ ] Orchestrate: Scraper → Storage → Checker → Notifier per product
- [ ] Enable `@EnableRetry`, `@EnableAsync`, `@EnableScheduling`

---

## Step 6 — REST API + UI
- [ ] `ProductController` — `GET/POST /api/products`, `DELETE /api/products/{id}`, `PATCH /api/products/{id}/toggle`
- [ ] `HistoryController` — `GET /api/products/{id}/history`
- [ ] Thymeleaf product management page (`GET /products`)
- [ ] Thymeleaf dashboard page with Chart.js (`GET /dashboard`)

---

## Step 7 — Tests
- [ ] `ScraperTest` — parse static HTML fixture in `src/test/resources`
- [ ] `StorageTest` — `@DataJpaTest`, write/read roundtrip, last-price query
- [ ] `CheckerTest` — threshold logic, no false positive on price increase, null price
- [ ] `NotifierTest` — WireMock, correct payload, failure does not crash

---

## Step 8 — Docker + CI
- [ ] `Dockerfile`
- [ ] `docker-compose.yml` (app + PostgreSQL)
- [ ] `.github/workflows/ci.yml` — run tests on push

---

## Step 9 — Docs
- [ ] `README.md` — install, configure, run, verify end-to-end
- [ ] `Design.md` — three tradeoffs
- [ ] `AI-NOTES.md` — one thing Claude got wrong
