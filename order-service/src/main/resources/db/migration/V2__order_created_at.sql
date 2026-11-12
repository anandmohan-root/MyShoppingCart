-- When each order was placed, so a customer's orders can be listed newest first.
-- Orders that existed before this column get the migration time.
ALTER TABLE orders ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX orders_customer_created ON orders (customer_id, created_at DESC);
