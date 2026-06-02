CREATE TABLE processed_stripe_events (
    stripe_event_id  VARCHAR(255)   NOT NULL,
    event_type       VARCHAR(100)   NOT NULL,
    processed_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_stripe_events PRIMARY KEY (stripe_event_id)
);

CREATE INDEX idx_processed_stripe_events_processed_at
    ON processed_stripe_events (processed_at);
