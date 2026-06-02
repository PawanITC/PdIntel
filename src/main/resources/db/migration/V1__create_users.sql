CREATE TABLE users (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255)    NOT NULL,
    stripe_customer_id  VARCHAR(255),
    subscription_status VARCHAR(50)     NOT NULL DEFAULT 'none',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email               UNIQUE (email),
    CONSTRAINT uq_users_stripe_customer_id  UNIQUE (stripe_customer_id)
);

CREATE INDEX idx_users_email               ON users (email);
CREATE INDEX idx_users_stripe_customer_id  ON users (stripe_customer_id);
