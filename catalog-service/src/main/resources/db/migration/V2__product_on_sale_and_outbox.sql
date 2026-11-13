-- Admins can now add products and take them off sale. An off-sale product keeps its row, so
-- past orders still refer to a real product. New products are announced through the outbox.
ALTER TABLE product ADD COLUMN on_sale BOOLEAN NOT NULL DEFAULT true;

CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    topic        TEXT NOT NULL,
    message_key  TEXT NOT NULL,
    payload      TEXT NOT NULL,
    published_at TIMESTAMPTZ
);
