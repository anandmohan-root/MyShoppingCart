-- Who to write to about an order, learned from its OrderCreated event. Email may be empty if the
-- customer's token had none; then nothing is sent for that order.
CREATE TABLE order_contact (
    order_id   UUID PRIMARY KEY,
    email      TEXT,
    total      NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per email sent. The primary key makes a repeated event send nothing the second time.
CREATE TABLE sent_notification (
    order_id UUID        NOT NULL,
    kind     TEXT        NOT NULL,
    sent_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (order_id, kind)
);
