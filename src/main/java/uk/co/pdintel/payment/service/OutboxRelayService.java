package uk.co.pdintel.payment.service;

/**
 * Scheduled outbox relay — polls PENDING and retryable FAILED outbox rows and
 * publishes them to the Kafka topic plany.stripe.webhook-raw.v1.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@Scheduled(fixedDelay=5000) — polls 5s after previous execution completes.
 *       fixedDelay not fixedRate prevents overlapping executions if a poll takes
 *       longer than 5s under load.</li>
 *   <li>MAX_RETRY_COUNT=3 — rows with retryCount >= 3 are skipped permanently.
 *       Operator must investigate and manually reset or discard.</li>
 *   <li>Fetches PENDING + FAILED below threshold in one query — efficient single
 *       DB round-trip per poll cycle.</li>
 *   <li>KafkaTemplate.send().get() — synchronous send waits for broker ack before
 *       marking PUBLISHED. Guarantees at-least-once delivery to Kafka.</li>
 *   <li>@Transactional on processRow() — each row in its own transaction.
 *       One publish failure does not roll back the rest of the batch.</li>
 *   <li>On publish failure: increments retryCount, sets status=FAILED — relay
 *       backs off on next poll. After MAX_RETRY_COUNT, row is permanently FAILED.</li>
 *   <li>partitionKey used as Kafka message key — councilId pre-computed at webhook
 *       write time. Guarantees ordering per council on the topic.</li>
 *   <li>runRelay() is public — OutboxRelaySteps calls it directly for deterministic
 *       test control. @Scheduled calls it in production.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.pdintel.payment.domain.StripeEventOutbox;
import uk.co.pdintel.payment.repository.StripeEventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final int MAX_RETRY_COUNT = 3;
    private static final String TOPIC = "plany.stripe.webhook-raw.v1";

    private final StripeEventOutboxRepository stripeEventOutboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelayService(StripeEventOutboxRepository stripeEventOutboxRepository,
                              KafkaTemplate<String, String> kafkaTemplate) {
        this.stripeEventOutboxRepository = stripeEventOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void runRelay() {
        List<StripeEventOutbox> pendingRows =
                stripeEventOutboxRepository
                        .findByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                                List.of("PENDING", "FAILED"), MAX_RETRY_COUNT);

        for (StripeEventOutbox row : pendingRows) {
            processRow(row);
        }
    }

    @Transactional
    public void processRow(StripeEventOutbox row) {
        try {
            kafkaTemplate.send(TOPIC, row.getPartitionKey(), row.getPayload()).get();
            row.setStatus("PUBLISHED");
            row.setPublishedAt(OffsetDateTime.now());
            stripeEventOutboxRepository.save(row);
            log.debug("Published event {} to Kafka topic {}", row.getStripeEventId(), TOPIC);
        } catch (Exception e) {
            row.setRetryCount(row.getRetryCount() + 1);
            row.setStatus("FAILED");
            stripeEventOutboxRepository.save(row);
            log.error("Failed to publish event {} — retryCount now {}",
                    row.getStripeEventId(), row.getRetryCount(), e);
        }
    }
}
