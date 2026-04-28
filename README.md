# Amazon Price Monitor

Monitors Amazon product prices and sends a Slack notification when a price drop is detected.

## Requirements

- Java 21
- Docker & Docker Compose

## Setup

### 1. Clone and configure

```bash
git clone https://github.com/jinjungs/monitor-amazon-product.git
cd monitor-amazon-product

cp .env.example .env
```

Edit `.env` and fill in the values:

```
DB_PASSWORD=any_password_you_choose
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL
```

> **Slack webhook:** go to [api.slack.com/apps](https://api.slack.com/apps) → Create App → Incoming Webhooks → Activate → Add to Workspace → copy the URL.

---

## Run

### Option A — Local (recommended for development)

Start PostgreSQL only:

```bash
docker-compose up db -d
```

Export env vars and run the app:

```bash
export $(cat .env | xargs)
./mvnw spring-boot:run
```

### Option B — Full Docker

```bash
./mvnw clean package -DskipTests
docker-compose up --build
```

---

## Verify

Open [http://localhost:8080/products](http://localhost:8080/products) and add at least 3 Amazon product URLs.

The scheduler runs every **1 hour** by default. To trigger a check immediately for testing, set a short interval in `application.yml`:

```yaml
monitor:
  interval-ms: 30000  # 30 seconds
```

When a price drop is detected, a Slack message appears in your channel:

```
Price Drop Alert! 🎉
Product Name
$6.00 → $5.19 (-13.5% / -$0.81)
View on Amazon
```

Price history is visible at [http://localhost:8080/dashboard](http://localhost:8080/dashboard).

---

## Configuration

All parameters are in `application.yml` — no code changes needed:

| Parameter | Default | Description |
|---|---|---|
| `monitor.interval-ms` | `3600000` | Check interval in ms (1 hour) |
| `monitor.threshold.absolute` | `1.00` | Notify if drop ≥ $1.00 |
| `monitor.threshold.percentage` | `2.0` | Notify if drop ≥ 2% (OR logic) |
| `monitor.thread-pool.core-size` | `3` | Concurrent scrape threads |

Secrets (`DB_PASSWORD`, `SLACK_WEBHOOK_URL`) go in `.env` — never committed.

---

## Run Tests

```bash
./mvnw test
```

Tests use H2 in-memory — no database setup needed.
