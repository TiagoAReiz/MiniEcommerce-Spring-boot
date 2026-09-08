-- What a customer actually compares when buying electronics: the category they browse by,
-- the two or three headline figures on the card, and the full spec sheet on the product page.
-- Until now a product carried only a free-text description, which cannot be filtered, cannot
-- be lined up next to another product, and cannot be rendered as a table.

-- One category per product, typed by the operator rather than drawn from a fixed list. There
-- is a single storefront and a single person filling this in, so an enum or a lookup table
-- would buy referential tidiness at the cost of a migration every time they stock something
-- new. The catalogue's filter bar is built from the distinct values actually present, so an
-- empty category simply means the product answers only to "Todos".
ALTER TABLE products
    ADD COLUMN category VARCHAR(60);

-- Filtering the catalogue is the only query this column serves, and it always pairs with the
-- active flag -- a shopper never sees a retired product under its category.
CREATE INDEX idx_products_category ON products (category) WHERE category IS NOT NULL;

-- Headline figures: "144 Hz", "2 TB", "40 h". Split into value and unit because the card
-- renders them at two different sizes, and joining them here would force the front end to
-- guess where the number ends.
--
-- JSONB rather than a child table, for both of these. They are written and read as a whole
-- sheet, never queried into, and their ORDER is meaningful -- a JSON array carries that for
-- free, where a table would need a position column and an ORDER BY on every read. The check
-- keeps the column honest about being a list; what is inside each element is the DTO's
-- business, validated at the edge where a useful error message can still be produced.
ALTER TABLE products
    ADD COLUMN highlights JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(highlights) = 'array');

-- The spec sheet: ordered label/value rows, shown on the product page and lined up column by
-- column in the comparison table.
ALTER TABLE products
    ADD COLUMN specs JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(specs) = 'array');
