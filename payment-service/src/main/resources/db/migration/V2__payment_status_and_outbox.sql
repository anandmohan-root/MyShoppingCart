-- A payment can now be declined or refunded, and its events leave through an outbox
-- so the payment row and its event commit together.
ALTER TABLE payment ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED';

CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    topic        TEXT NOT NULL,
    message_key  TEXT NOT NULL,
    payload      TEXT NOT NULL,
    published_at TIMESTAMPTZ
);
