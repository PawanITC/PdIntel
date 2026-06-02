CREATE TABLE subscriptions (
    id                       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID            NOT NULL,
    stripe_subscription_id   VARCHAR(255)    NOT NULL,
    stripe_price_id          VARCHAR(255)    NOT NULL,
    status                   VARCHAR(50)     NOT NULL,
    plan_name                VARCHAR(100)    NOT NULL,
    amount_pence             BIGINT          NOT NULL,
    currency                 VARCHAR(3)      NOT NULL DEFAULT 'gbp',
    council_id               UUID            NOT NULL,
    current_period_start     TIMESTAMPTZ,
    current_period_end       TIMESTAMPTZ,
    canceled_at              TIMESTAMPTZ,
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_subscriptions_stripe_id   UNIQUE (stripe_subscription_id),
    CONSTRAINT fk_subscriptions_user_id     FOREIGN KEY (user_id)
                                                REFERENCES users (id)
                                                ON DELETE RESTRICT
);

CREATE INDEX idx_subscriptions_user_id               ON subscriptions (user_id);
CREATE INDEX idx_subscriptions_stripe_subscription_id ON subscriptions (stripe_subscription_id);
CREATE INDEX idx_subscriptions_council_id            ON subscriptions (council_id);
CREATE INDEX idx_subscriptions_status                ON subscriptions (status);
