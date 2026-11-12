CREATE TABLE product (
    id    UUID PRIMARY KEY,
    name  TEXT           NOT NULL,
    price NUMERIC(10, 2) NOT NULL
);

INSERT INTO product (id, name, price) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Espresso Machine', 249.00),
    ('22222222-2222-2222-2222-222222222222', 'Coffee Grinder', 89.50),
    ('33333333-3333-3333-3333-333333333333', 'Coffee Beans 1kg', 18.75);
