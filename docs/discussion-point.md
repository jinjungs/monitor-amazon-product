### Panel Discussion Points

> Think clearly about tradeoffs.

---

#### One place where the solution could break (known, left alone)

- **Amazon bot detection / CAPTCHA**
  - A plain HTTP client (jsoup) is detectable. Amazon may return a CAPTCHA page or 503 instead of the product page.
  - Handled gracefully: detected as `status='unavailable'`, logged, no notification triggered, retried next tick
  - Not fully solved: persistent IP bans would require proxy rotation or a headless browser — out of scope for this window
  - This is the single most likely real-world failure point

---

#### No event reprocessing queue (deliberate)

- Scraping failures retry naturally on the next scheduled tick — no separate outbox needed
- Notification failures are a known gap: if Slack is down at the moment of a price drop, that notification is lost
- At production scale: add an outbox table for guaranteed delivery
- Chose not to here: complexity not justified for a handful of products on an hourly interval

---

#### DB at 10x scale

- PostgreSQL handles 10x (100 products, hourly checks) without schema changes
- The index on `(product_id, checked_at DESC)` already covers all query patterns
- The real 10x problem is **scraping**: Amazon rate limiting, thread pool sizing, notification flood
- At 100x+: read replicas, monthly partitioning on `price_checks`, or TimescaleDB

---

#### Multi-source extensibility (not implemented, design note)

- Current scraper is Amazon-only — URL parsing and CSS selectors are Amazon-specific
- Stretch Goal #1 (multi-source comparison) would require a `ScraperFactory` pattern:
  - `ScraperFactory.getScraper(url)` → returns the right `Scraper` implementation based on domain
  - Each source (`AmazonScraper`, `EbayScraper`, etc.) implements a common `Scraper` interface
  - Checker and Notifier layers are untouched — they only see `price` and `productId`
- Not implemented: only one source required; adding the abstraction now would be premature
- Known extension point — worth calling out in Design.md as a deliberate deferral

---

#### No user authentication (deliberate)

- Requirements make no mention of user management — this is a single-user personal tool
- Adding Spring Security would mean login form, session management, CSRF config — complexity with no stated requirement
- Known gap: if the app is exposed publicly, the product management UI and dashboard are unprotected
- At multi-user or public deployment scale: Spring Security + basic auth or OAuth2 would be the natural addition

---

#### AWS is not suitable for this project

- Previous project used Lambda + EventBridge + DynamoDB (CDK)
- Lambda is event-driven; this project is process-driven (long-running scheduler)
- Spring Boot cold start on Lambda adds friction for hourly invocations
- DynamoDB is less ergonomic than SQL for time-series price history queries
- docker-compose + PostgreSQL is simpler, faster to build, and fully defensible
