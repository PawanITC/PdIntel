CREATE TABLE stripe_event_outbox (
    id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_event_id  VARCHAR(255)    NOT NULL,
    event_type       VARCHAR(100)    NOT NULL,
    payload          JSONB           NOT NULL,
    partition_key    VARCHAR(255)    NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    retry_count      SMALLINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ,

    CONSTRAINT fk_outbox_stripe_event_id    FOREIGN KEY (stripe_event_id)
                                                REFERENCES processed_stripe_events (stripe_event_id)
                                                ON DELETE CASCADE,
    CONSTRAINT chk_outbox_status            CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_created_at
    ON stripe_event_outbox (created_at);

CREATE INDEX idx_outbox_stripe_event_id
    ON stripe_event_outbox (stripe_event_id);

CREATE INDEX idx_outbox_status_pending
    ON stripe_event_outbox (created_at)
    WHERE status = 'PENDING';
