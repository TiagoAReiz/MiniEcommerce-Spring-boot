-- Checkout reserves stock the moment the order is created, before any payment exists. A
-- customer who closes the tab there leaves the order PENDING and the stock reserved with
-- nothing to release it — worse than an abandoned cart, which holds nothing.
--
-- expires_at is both the trigger and the record. It is set at checkout and cleared when the
-- order is paid, so a CANCELLED order that still carries one was abandoned rather than
-- cancelled by hand: that is the lost-sale history, readable with its items and prices.
ALTER TABLE orders
    ADD COLUMN expires_at TIMESTAMPTZ;

-- Partial: the sweep only ever looks for orders still waiting, and those are a small
-- fraction of the table.
CREATE INDEX idx_orders_expiring ON orders (expires_at)
    WHERE status = 'PENDING' AND expires_at IS NOT NULL;
