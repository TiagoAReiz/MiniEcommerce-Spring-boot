-- Freight, charged by distance between the store and the delivery address.
--
-- The origin lives on the owner row rather than in configuration so the operator can change
-- it without a redeploy. It starts NULL because V2 seeds the owner with an e-mail only --
-- there is no CEP to invent for them. NULL therefore means "not configured yet", and the
-- checkout refuses while it holds: every order carries freight, so a store that cannot
-- measure it must not sell rather than deliver for free by accident. Setting it is a
-- one-time step for the operator, through PUT /owners/origin.
ALTER TABLE owners
    ADD COLUMN origin_zip_code VARCHAR(8);

-- The quote is frozen here for the same reason unit_price is frozen on order_items: it is
-- calculated from a third-party lookup that can answer differently tomorrow, or not at all.
-- Recomputing it at read time would let the amount owed drift after the customer agreed to
-- it. DEFAULT 0 keeps orders placed before this migration honest -- they were never charged
-- freight, and backfilling one now would invent a charge that never happened.
ALTER TABLE orders
    ADD COLUMN shipping_cost NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (shipping_cost >= 0);

-- Nullable on purpose, and the null carries meaning: a row with a cost but no distance was
-- priced by the contingency rate because the CEP lookup failed. That distinction is what
-- makes a freight charge explainable to a customer who disputes it, and it is also how you
-- count how often the fallback is firing -- without spending a second boolean column on it.
ALTER TABLE orders
    ADD COLUMN shipping_distance_km NUMERIC(10, 2) CHECK (shipping_distance_km >= 0);
