CREATE TABLE invoices (
    id                        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID            NOT NULL,
    subscription_id           UUID            NOT NULL,
    stripe_invoice_id         VARCHAR(255)    NOT NULL,
    stripe_payment_intent_id  VARCHAR(255),
    amount_pence              BIGINT          NOT NULL,
    tax_pence                 BIGINT          NOT NULL DEFAULT 0,
    total_pence               BIGINT          NOT NULL,
    currency                  VARCHAR(3)      NOT NULL DEFAULT 'gbp',
    status                    VARCHAR(50)     NOT NULL,
    invoice_date              TIMESTAMPTZ     NOT NULL,
    due_date                  TIMESTAMPTZ,
    paid_at                   TIMESTAMPTZ,
    s3_pdf_path               VARCHAR(500),
    hmrc_mtd_reference        VARCHAR(100),
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_invoices_stripe_invoice_id        UNIQUE (stripe_invoice_id),
    CONSTRAINT chk_invoices_total                   CHECK (total_pence = amount_pence + tax_pence),
    CONSTRAINT chk_invoices_amounts_non_negative    CHECK (amount_pence >= 0 AND tax_pence >= 0 AND total_pence >= 0),
    CONSTRAINT fk_invoices_user_id                  FOREIGN KEY (user_id)
                                                        REFERENCES users (id)
                                                        ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_subscription_id          FOREIGN KEY (subscription_id)
                                                        REFERENCES subscriptions (id)
                                                        ON DELETE RESTRICT
);

CREATE INDEX idx_invoices_user_id                ON invoices (user_id);
CREATE INDEX idx_invoices_subscription_id        ON invoices (subscription_id);
CREATE INDEX idx_invoices_stripe_invoice_id      ON invoices (stripe_invoice_id);
CREATE INDEX idx_invoices_status                 ON invoices (status);
CREATE INDEX idx_invoices_invoice_date           ON invoices (invoice_date);
CREATE INDEX idx_invoices_hmrc_mtd_reference     ON invoices (hmrc_mtd_reference);
