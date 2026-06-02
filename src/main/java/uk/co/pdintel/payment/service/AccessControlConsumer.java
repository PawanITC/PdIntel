package uk.co.pdintel.payment.service;

/**
 * Kafka consumer that updates user subscription status based on Stripe webhook events.
 *
 * <p>Decisions:
 * <ul>
 *   <li>containerFactory="accessControlContainerFactory" — uses dedicated factory with
 *       group-id=plany-access-control. Receives every message independently from
 *       the audit consumer which has its own group.</li>
 *   <li>@RetryableTopic — exponential backoff (1s, 2s, 4s) on transient failures.
 *       After 3 attempts, message routed to dead-letter topic for manual inspection.</li>
 *   <li>acknowledgment.acknowledge() called only after successful DB write — manual
 *       ack mode. Offset only committed after userRepository.save() succeeds.
 *       No message loss on DB failure; Kafka redelivers on restart.</li>
 *   <li>Event type drives status transition — not the payload status field, except
 *       for customer.subscription.updated which carries the explicit new status.</li>
 *   <li>Idempotency via processedStripeEventRepository — duplicate message detected
 *       by existing row; acknowledged and skipped without DB update.</li>
 *   <li>User resolved by customer_email — present in all relevant Stripe events.
 *       If user not found, message acknowledged and warning logged (not a failure).</li>
 *   <li>Unknown event types acknowledged and skipped — consumer never fails on
 *       unrecognised events. Feature file asserts status unchanged.</li>
 *   <li>@Transactional — DB writes atomic. If save fails, no ack, Kafka redelivers.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.pdintel.payment.domain.ProcessedStripeEvent;
import uk.co.pdintel.payment.repository.ProcessedStripeEventRepository;
import uk.co.pdintel.payment.repository.UserRepository;

@Service
public class AccessControlConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccessControlConsumer.class);

    private final UserRepository userRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final ObjectMapper objectMapper;

    public AccessControlConsumer(UserRepository userRepository,
                                 ProcessedStripeEventRepository processedStripeEventRepository,
                                 ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.processedStripeEventRepository = processedStripeEventRepository;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @KafkaListener(
            topics = "plany.stripe.webhook-raw.v1",
            containerFactory = "accessControlContainerFactory"
    )
    @Transactional
    public void consume(String payload, Acknowledgment acknowledgment) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId   = root.path("id").asText();
            String eventType = root.path("type").asText();
            String email     = root.path("data").path("object")
                                   .path("customer_email").asText();

            try {
                processedStripeEventRepository.save(
                        new ProcessedStripeEvent(eventId + "-access-control", eventType));
            } catch (DataIntegrityViolationException e) {
                log.debug("Access control: duplicate event {} — skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            String newStatus = resolveStatus(eventType, root);
            if (newStatus == null) {
                log.debug("Access control: unhandled event type {} — skipping", eventType);
                acknowledgment.acknowledge();
                return;
            }

            userRepository.findByEmail(email).ifPresentOrElse(user -> {
                user.setSubscriptionStatus(newStatus);
                userRepository.save(user);
                log.info("Access control: user {} status → {}", email, newStatus);
            }, () -> log.warn("Access control: user not found for email {}", email));

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Access control consumer failed to process message", e);
            throw new RuntimeException("Access control consumer error", e);
        }
    }

    private String resolveStatus(String eventType, JsonNode root) {
        return switch (eventType) {
            case "invoice.payment_succeeded"      -> "active";
            case "customer.subscription.deleted"  -> "canceled";
            case "customer.subscription.updated"  ->
                    root.path("data").path("object").path("status").asText(null);
            case "customer.subscription.created"  -> "active";
            default                               -> null;
        };
    }
}
