CREATE TABLE orders (
    id          UUID PRIMARY KEY,
    customer_id TEXT           NOT NULL,
    product_id  UUID           NOT NULL,
    quantity    INT            NOT NULL,
    total       NUMERIC(12, 2) NOT NULL,
    status      TEXT           NOT NULL
);

CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    topic        TEXT NOT NULL,
    message_key  TEXT NOT NULL,
    payload      TEXT NOT NULL,
    published_at TIMESTAMPTZ
);
