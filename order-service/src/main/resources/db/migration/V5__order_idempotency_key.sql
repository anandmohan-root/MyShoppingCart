-- One idempotency key per order, unique per customer, replacing cart_id: a cart checkout uses
-- 'cart:<cart id>', a POST /orders request 'request:<Idempotency-Key header>'. Orders placed
-- without a key keep it empty, and empty keys never clash.
ALTER TABLE orders ADD COLUMN idempotency_key TEXT;

UPDATE orders SET idempotency_key = 'cart:' || cart_id WHERE cart_id IS NOT NULL;

ALTER TABLE orders DROP COLUMN cart_id;

CREATE UNIQUE INDEX orders_customer_idempotency_key ON orders (customer_id, idempotency_key);
