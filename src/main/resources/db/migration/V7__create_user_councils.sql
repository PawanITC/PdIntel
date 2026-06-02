CREATE TABLE user_councils (
    user_id     UUID        NOT NULL,
    council_id  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_councils         PRIMARY KEY (user_id, council_id),
    CONSTRAINT fk_user_councils_user_id FOREIGN KEY (user_id)
                                            REFERENCES users (id)
                                            ON DELETE CASCADE
);

CREATE INDEX idx_user_councils_user_id    ON user_councils (user_id);
CREATE INDEX idx_user_councils_council_id ON user_councils (council_id);
