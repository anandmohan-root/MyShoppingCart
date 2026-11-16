-- An order keeps the product's name as it was when ordered, so order history still reads well
-- after the product is renamed or taken off sale. Lines of earlier orders have no name: the
-- catalog is another service's database, so they cannot be filled in from here.
ALTER TABLE order_line ADD COLUMN product_name TEXT;
