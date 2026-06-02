package uk.co.pdintel.payment.domain;

/**
 * JPA entity representing an outbox row for a Stripe webhook event.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Separate UUID PK — outbox rows have their own identity independent of
 *       stripeEventId, allowing retry tracking without ambiguity.</li>
 *   <li>stripeEventId as FK to processed_stripe_events — written atomically in the
 *       same transaction. If either write fails, both are rolled back, preventing
 *       an outbox row with no idempotency record or vice versa.</li>
 *   <li>payload stored as JSONB — relay reads and publishes raw JSON to Kafka.
 *       JSONB is queryable if we ever need to inspect payloads directly in the DB.</li>
 *   <li>partitionKey pre-computed at write time — relay reads councilId directly
 *       without parsing JSONB. Guarantees correct Kafka partition key every time.</li>
 *   <li>status: PENDING → PUBLISHED | FAILED — relay state machine. DB CHECK
 *       constraint enforces only these three values.</li>
 *   <li>retryCount incremented by relay on each failed publish attempt. Relay
 *       backs off after a configurable threshold.</li>
 *   <li>publishedAt nullable — only set when Kafka producer receives ack.</li>
 *   <li>Write-once fields have no setters — only status, retryCount, publishedAt
 *       are mutable after creation.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stripe_event_outbox")
public class StripeEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "stripe_event_id", nullable = false, updatable = false)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "partition_key", nullable = false, updatable = false)
    private String partitionKey;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected StripeEventOutbox() {
    }

    public StripeEventOutbox(String stripeEventId, String eventType,
                             String payload, String partitionKey) {
        this.stripeEventId = stripeEventId;
        this.eventType = eventType;
        this.payload = payload;
        this.partitionKey = partitionKey;
    }

    public UUID getId() { return id; }

    public String getStripeEventId() { return stripeEventId; }

    public String getEventType() { return eventType; }

    public String getPayload() { return payload; }

    public String getPartitionKey() { return partitionKey; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public int getRetryCount() { return retryCount; }

    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }

    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
}
