-- The customer picks a delivery address at checkout, but until now the address only lived
-- on shipments, which the store operator creates after payment. That left the choice with
-- nowhere to go between checkout and dispatch.
--
-- Safe as NOT NULL because no order has ever been created: the checkout endpoint ships with
-- this migration. On a populated table this would have to be added nullable and backfilled.
ALTER TABLE orders
    ADD COLUMN address_id UUID NOT NULL REFERENCES addresses (id);

CREATE INDEX idx_orders_address_id ON orders (address_id);
