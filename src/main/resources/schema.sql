CREATE TABLE IF NOT EXISTS products (
    id         BIGSERIAL PRIMARY KEY,
    url        TEXT UNIQUE NOT NULL,
    name       TEXT,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS price_checks (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    price      NUMERIC(10, 2),
    currency   VARCHAR(3) DEFAULT 'USD',
    status     VARCHAR(20) NOT NULL,
    error_msg  TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_price_checks_product_checked
    ON price_checks(product_id, checked_at DESC);
