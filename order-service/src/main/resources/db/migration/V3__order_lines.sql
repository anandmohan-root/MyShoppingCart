-- Orders can hold several products. Each product moves to its own line, at the price it was
-- sold for; orders placed before this change become one-line orders.
CREATE TABLE order_line (
    order_id   UUID           NOT NULL REFERENCES orders (id),
    line_no    INT            NOT NULL,
    product_id UUID           NOT NULL,
    quantity   INT            NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (order_id, line_no)
);

INSERT INTO order_line (order_id, line_no, product_id, quantity, unit_price)
SELECT id, 1, product_id, quantity, ROUND(total / quantity, 2)
FROM orders;

ALTER TABLE orders DROP COLUMN product_id, DROP COLUMN quantity;
