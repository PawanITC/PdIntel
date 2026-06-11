package uk.co.pdintel.payment.service;

/**
 * Kinesis consumer that updates user subscription status based on Stripe webhook events.
 *
 * <p>Decisions:
 * <ul>
 *   <li>consume(String payload) is a plain method called by KinesisConsumerService —
 *       no Kafka annotations. Receives every record dispatched from the Kinesis shard.</li>
 *   <li>Idempotency via processedStripeEventRepository — duplicate record detected
 *       by existing row with suffix "-access-control"; skipped without DB update.</li>
 *   <li>Event type drives status transition — not the payload status field, except
 *       for customer.subscription.updated which carries the explicit new status.</li>
 *   <li>User resolved by customer_email — present in all relevant Stripe events.
 *       If user not found, warning logged (not a failure).</li>
 *   <li>Unknown event types silently skipped — consumer never fails on unrecognised events.</li>
 *   <li>@Transactional — DB writes atomic. Exception propagates to the polling service
 *       which will retry on next poll cycle.</li>
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

    @Transactional
    public void consume(String payload) {
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
                return;
            }

            String newStatus = resolveStatus(eventType, root);
            if (newStatus == null) {
                log.debug("Access control: unhandled event type {} — skipping", eventType);
                return;
            }

            userRepository.findByEmail(email).ifPresentOrElse(user -> {
                user.setSubscriptionStatus(newStatus);
                userRepository.save(user);
                log.info("Access control: user {} status → {}", email, newStatus);
            }, () -> log.warn("Access control: user not found for email {}", email));

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
