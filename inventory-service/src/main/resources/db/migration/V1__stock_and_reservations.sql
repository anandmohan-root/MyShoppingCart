-- How many of each product can still be sold. The check keeps stock from going below zero
-- even if two reservations race.
CREATE TABLE stock (
    product_id UUID PRIMARY KEY,
    available  INT NOT NULL CHECK (available >= 0)
);

INSERT INTO stock (product_id, available) VALUES
    ('11111111-1111-1111-1111-111111111111', 25),   -- Espresso Machine
    ('22222222-2222-2222-2222-222222222222', 500),  -- Coffee Grinder
    ('33333333-3333-3333-3333-333333333333', 1000); -- Coffee Beans 1kg

-- One row per order, whatever happened to it: the primary key makes every event about an order
-- safe to receive twice or in any order.
CREATE TABLE reservation (
    order_id   UUID PRIMARY KEY,
    status     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation_line (
    order_id   UUID NOT NULL REFERENCES reservation (order_id),
    product_id UUID NOT NULL,
    quantity   INT  NOT NULL,
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    topic        TEXT NOT NULL,
    message_key  TEXT NOT NULL,
    payload      TEXT NOT NULL,
    published_at TIMESTAMPTZ
);
