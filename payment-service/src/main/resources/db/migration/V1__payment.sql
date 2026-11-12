CREATE TABLE payment (
    id       UUID PRIMARY KEY,
    order_id UUID           NOT NULL UNIQUE,
    amount   NUMERIC(12, 2) NOT NULL
);
