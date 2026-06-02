package uk.co.pdintel.payment.service;

/**
 * Handles incoming Stripe webhook events — signature verification, idempotency, and outbox write.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Signature verified first via Webhook.constructEvent() — cheapest operation,
 *       rejects tampered/invalid requests before any DB interaction.</li>
 *   <li>Missing signature header throws WebhookSignatureException → 400. Stripe always
 *       sends Stripe-Signature; absence means misconfiguration or attack.</li>
 *   <li>DataIntegrityViolationException on ProcessedStripeEvent save → already processed.
 *       Return silently with no error — idempotency guarantee for Stripe retries.</li>
 *   <li>@Transactional wraps both saves atomically — processed_stripe_events and
 *       stripe_event_outbox are written together. If either fails, both roll back.
 *       Prevents outbox row with no idempotency record or vice versa.</li>
 *   <li>councilId extracted from data.object.metadata — pre-computed as Kafka partition
 *       key so the relay never has to parse JSONB. Falls back to "unknown" for
 *       Stripe events with no council context (e.g. account.updated).</li>
 *   <li>No downstream calls inside this method — returns in <30ms. Kafka publishing
 *       is the relay's job (Step 7), not the webhook handler's.</li>
 *   <li>Raw payload String passed to Webhook.constructEvent() — Stripe signature is
 *       computed over the original raw bytes; re-serialised JSON would fail verification.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.pdintel.payment.domain.ProcessedStripeEvent;
import uk.co.pdintel.payment.domain.StripeEventOutbox;
import uk.co.pdintel.payment.exception.WebhookSignatureException;
import uk.co.pdintel.payment.repository.ProcessedStripeEventRepository;
import uk.co.pdintel.payment.repository.StripeEventOutboxRepository;

@Service
public class WebhookService {

    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final StripeEventOutboxRepository stripeEventOutboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public WebhookService(ProcessedStripeEventRepository processedStripeEventRepository,
                          StripeEventOutboxRepository stripeEventOutboxRepository,
                          ObjectMapper objectMapper) {
        this.processedStripeEventRepository = processedStripeEventRepository;
        this.stripeEventOutboxRepository = stripeEventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleWebhookEvent(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new WebhookSignatureException("Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new WebhookSignatureException("Invalid Stripe-Signature", e);
        }

        String partitionKey = extractCouncilId(payload);

        try {
            processedStripeEventRepository.save(
                    new ProcessedStripeEvent(event.getId(), event.getType()));

            stripeEventOutboxRepository.save(
                    new StripeEventOutbox(event.getId(), event.getType(), payload, partitionKey));

        } catch (DataIntegrityViolationException e) {
            // duplicate event — idempotency guarantee, return silently
        }
    }

    private String extractCouncilId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode councilId = root
                    .path("data")
                    .path("object")
                    .path("metadata")
                    .path("councilId");
            return councilId.isMissingNode() ? "unknown" : councilId.asText();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
