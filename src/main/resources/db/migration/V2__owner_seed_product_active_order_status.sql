-- Products are retired, never deleted: order_items references them with RESTRICT, so
-- removing a sold product would destroy order history.
ALTER TABLE products
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;

-- Only active products with stock are worth listing, and that is the catalogue's hottest
-- query.
CREATE INDEX idx_products_sellable ON products (name) WHERE active AND stock > 0;

-- The store operator is provisioned before ever signing in, so google_sub is unknown at
-- insert time. It is filled in the first time that account signs in with Google, matched
-- by e-mail. Postgres allows many NULLs in a unique index, so unclaimed owners coexist.
ALTER TABLE owners
    ALTER COLUMN google_sub DROP NOT NULL;

INSERT INTO owners (id, email)
VALUES (gen_random_uuid(), 'bruttobpt@gmail.com')
ON CONFLICT (email) DO NOTHING;

-- orders.status is a closed set now, backed by the OrderStatus enum in the domain. The
-- check is the database's half of that contract.
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_status
    CHECK (status IN ('PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'));
