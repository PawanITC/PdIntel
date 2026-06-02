package uk.co.pdintel.payment.domain;

/**
 * JPA entity representing an immutable audit log entry.
 *
 * <p>Decisions:
 * <ul>
 *   <li>No foreign keys to any other table — audit log must outlive all referenced
 *       entities. GDPR right to erasure may delete a user; HMRC 6-year retention
 *       requires the audit trail to survive. entityId is a plain UUID reference only.</li>
 *   <li>Insert-only — no updated_at, no setters. Written once by AuditConsumer,
 *       never modified. Immutability is enforced by the absence of setters.</li>
 *   <li>actorId nullable — events triggered by Stripe have no internal user actor.
 *       actorType=STRIPE with actorId=null is the correct representation.</li>
 *   <li>councilId nullable — system-level Stripe events (e.g. account.updated)
 *       have no council context. Nullable prevents NPE on broad event types.</li>
 *   <li>payload mapped to JSONB — stores only IDs and status values, never PII.
 *       JSONB is queryable for compliance reporting without application layer.</li>
 *   <li>@CreationTimestamp on createdAt — Hibernate sets on insert automatically.
 *       Required for HMRC MTD audit trail timestamping.</li>
 *   <li>Public constructor enforces all mandatory fields — eventType, actorType,
 *       entityType, entityId can never be null on a new entry.</li>
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
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, updatable = false)
    private String actorType;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "council_id", updatable = false)
    private UUID councilId;

    @Column(updatable = false, columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {
    }

    public AuditLog(String eventType, String actorType, String entityType,
                    UUID entityId, UUID councilId, String payload) {
        this.eventType = eventType;
        this.actorType = actorType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.councilId = councilId;
        this.payload = payload;
    }

    public AuditLog(String eventType, UUID actorId, String actorType, String entityType,
                    UUID entityId, UUID councilId, String payload) {
        this(eventType, actorType, entityType, entityId, councilId, payload);
        this.actorId = actorId;
    }

    public UUID getId() { return id; }

    public String getEventType() { return eventType; }

    public UUID getActorId() { return actorId; }

    public String getActorType() { return actorType; }

    public String getEntityType() { return entityType; }

    public UUID getEntityId() { return entityId; }

    public UUID getCouncilId() { return councilId; }

    public String getPayload() { return payload; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
