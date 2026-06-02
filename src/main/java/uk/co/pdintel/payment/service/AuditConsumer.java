package uk.co.pdintel.payment.service;

/**
 * Kafka consumer that writes every Stripe webhook event to the audit log.
 *
 * <p>Decisions:
 * <ul>
 *   <li>containerFactory="auditContainerFactory" — dedicated factory with group-id=plany-audit.
 *       Receives every message independently from AccessControlConsumer.</li>
 *   <li>Idempotency key suffix "-audit" — evt_xxx-audit is separate from evt_xxx-access-control
 *       and the raw webhook entry. Each consumer deduplicates independently.</li>
 *   <li>mapEventType() normalises Stripe event types to internal audit constants —
 *       decouples audit schema from Stripe naming. UNKNOWN for unmapped types ensures
 *       every event is logged, nothing silently discarded.</li>
 *   <li>sanitisePayload() strips PII fields before persisting — removes customer_email,
 *       email, name, phone, address from JSON. Non-negotiable: no PII in audit log.</li>
 *   <li>entityId from data.object.id — Stripe object ID (subscription, invoice etc.)
 *       is the entity reference in the audit entry.</li>
 *   <li>councilId from data.object.metadata.councilId — same extraction as webhook handler.
 *       Null-safe: falls back to null for events without council context.</li>
 *   <li>actorType=STRIPE always — all Kafka events are Stripe-triggered, no internal actor.</li>
 *   <li>@RetryableTopic with exponential backoff — consistent retry behaviour across consumers.</li>
 *   <li>@Transactional + manual ack — DB write first, offset committed after success.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.pdintel.payment.domain.AuditLog;
import uk.co.pdintel.payment.domain.ProcessedStripeEvent;
import uk.co.pdintel.payment.repository.AuditLogRepository;
import uk.co.pdintel.payment.repository.ProcessedStripeEventRepository;

import java.util.List;
import java.util.UUID;

@Service
public class AuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);

    private static final List<String> PII_FIELDS = List.of(
            "customer_email", "email", "name", "phone", "address", "postcode", "dob");

    private final AuditLogRepository auditLogRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final ObjectMapper objectMapper;

    public AuditConsumer(AuditLogRepository auditLogRepository,
                         ProcessedStripeEventRepository processedStripeEventRepository,
                         ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.processedStripeEventRepository = processedStripeEventRepository;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @KafkaListener(
            topics = "plany.stripe.webhook-raw.v1",
            containerFactory = "auditContainerFactory"
    )
    @Transactional
    public void consume(String payload, Acknowledgment acknowledgment) {
        try {
            JsonNode root       = objectMapper.readTree(payload);
            String eventId      = root.path("id").asText();
            String eventType    = root.path("type").asText();
            JsonNode dataObject = root.path("data").path("object");

            try {
                processedStripeEventRepository.save(
                        new ProcessedStripeEvent(eventId + "-audit", eventType));
            } catch (DataIntegrityViolationException e) {
                log.debug("Audit: duplicate event {} — skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            String internalEventType = mapEventType(eventType);
            UUID entityId            = parseUuid(dataObject.path("id").asText(null));
            UUID councilId           = parseUuid(dataObject.path("metadata")
                                            .path("councilId").asText(null));
            String sanitisedPayload  = sanitisePayload(dataObject);

            AuditLog entry = new AuditLog(
                    internalEventType,
                    "STRIPE",
                    resolveEntityType(eventType),
                    entityId != null ? entityId : UUID.randomUUID(),
                    councilId,
                    sanitisedPayload);

            auditLogRepository.save(entry);
            log.debug("Audit: recorded {} for event {}", internalEventType, eventId);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Audit consumer failed to process message", e);
            throw new RuntimeException("Audit consumer error", e);
        }
    }

    private String mapEventType(String stripeEventType) {
        return switch (stripeEventType) {
            case "invoice.payment_succeeded"      -> "PAYMENT_SUCCEEDED";
            case "invoice.payment_failed"         -> "PAYMENT_FAILED";
            case "customer.subscription.created"  -> "SUBSCRIPTION_CREATED";
            case "customer.subscription.updated"  -> "SUBSCRIPTION_UPDATED";
            case "customer.subscription.deleted"  -> "SUBSCRIPTION_CANCELED";
            default                               -> "UNKNOWN";
        };
    }

    private String resolveEntityType(String stripeEventType) {
        if (stripeEventType.startsWith("invoice.")) return "INVOICE";
        if (stripeEventType.startsWith("customer.subscription.")) return "SUBSCRIPTION";
        return "UNKNOWN";
    }

    private String sanitisePayload(JsonNode dataObject) {
        try {
            ObjectNode sanitised = dataObject.deepCopy();
            PII_FIELDS.forEach(sanitised::remove);
            return objectMapper.writeValueAsString(sanitised);
        } catch (Exception e) {
            return "{}";
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
