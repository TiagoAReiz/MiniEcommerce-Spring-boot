CREATE TABLE users (
    id         UUID PRIMARY KEY,
    google_sub VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    cpf        VARCHAR(11)  UNIQUE,
    phone      VARCHAR(20),
    photo_url  VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE TABLE owners (
    id         UUID PRIMARY KEY,
    google_sub VARCHAR(255) NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE TABLE products (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    price       NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    stock       INTEGER        NOT NULL DEFAULT 0 CHECK (stock >= 0),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_products_name ON products (name);

CREATE TABLE product_photos (
    id         UUID PRIMARY KEY,
    product_id UUID         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    url        VARCHAR(255) NOT NULL,
    position   SMALLINT     NOT NULL DEFAULT 0,
    is_cover   BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_product_photos_product_id ON product_photos (product_id);
CREATE UNIQUE INDEX uq_product_photos_cover ON product_photos (product_id) WHERE is_cover;

CREATE TABLE addresses (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    is_primary    BOOLEAN      NOT NULL DEFAULT false,
    zip_code      VARCHAR(8)   NOT NULL,
    street        VARCHAR(255) NOT NULL,
    street_number VARCHAR(10),
    neighborhood  VARCHAR(255) NOT NULL,
    city          VARCHAR(255) NOT NULL,
    state         VARCHAR(2)   NOT NULL,
    country       VARCHAR(2)   NOT NULL DEFAULT 'BR',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);

CREATE INDEX idx_addresses_user_id ON addresses (user_id);
CREATE UNIQUE INDEX uq_addresses_primary ON addresses (user_id) WHERE is_primary;

CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    amount          NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    is_paid         BOOLEAN        NOT NULL DEFAULT false,
    mercado_pago_id VARCHAR(255)   UNIQUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE TABLE shipments (
    id                   UUID PRIMARY KEY,
    address_id            UUID        NOT NULL REFERENCES addresses (id),
    estimated_delivery_at TIMESTAMPTZ,
    shipped_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ
);

CREATE INDEX idx_shipments_address_id ON shipments (address_id);

CREATE TABLE orders (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    payment_id  UUID        REFERENCES payments (id),
    shipment_id UUID        REFERENCES shipments (id),
    status      VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_orders_user_id ON orders (user_id);

CREATE TABLE order_items (
    id         UUID PRIMARY KEY,
    order_id   UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID           NOT NULL REFERENCES products (id),
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    UNIQUE (order_id, product_id)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);

CREATE TABLE reviews (
    id            UUID PRIMARY KEY,
    order_item_id UUID        NOT NULL UNIQUE REFERENCES order_items (id) ON DELETE CASCADE,
    product_id    UUID        NOT NULL REFERENCES products (id),
    title         VARCHAR(255),
    comment       TEXT,
    rating        SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);

CREATE INDEX idx_reviews_product_id ON reviews (product_id);
