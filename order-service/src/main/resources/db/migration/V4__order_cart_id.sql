-- The cart an order was checked out from. Unique, so one cart can never become two orders;
-- empty for orders placed directly through POST /orders.
ALTER TABLE orders ADD COLUMN cart_id UUID;

CREATE UNIQUE INDEX orders_cart_id ON orders (cart_id);
