CREATE TABLE audit_log (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type   VARCHAR(100)  NOT NULL,
    actor_id     UUID,
    actor_type   VARCHAR(20)   NOT NULL,
    entity_type  VARCHAR(50)   NOT NULL,
    entity_id    UUID          NOT NULL,
    council_id   UUID,
    payload      JSONB,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_audit_log_actor_type  CHECK (actor_type IN ('USER', 'SYSTEM', 'STRIPE'))
);

CREATE INDEX idx_audit_log_entity        ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_actor_id      ON audit_log (actor_id);
CREATE INDEX idx_audit_log_council_id    ON audit_log (council_id);
CREATE INDEX idx_audit_log_event_type    ON audit_log (event_type);
CREATE INDEX idx_audit_log_created_at    ON audit_log (created_at);
