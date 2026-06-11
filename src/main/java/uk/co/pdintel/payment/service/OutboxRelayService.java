package uk.co.pdintel.payment.service;

/**
 * Scheduled outbox relay — polls PENDING and retryable FAILED outbox rows and
 * publishes them to the Kinesis stream pd-payment-kinesis-euw2.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@Scheduled(fixedDelay=5000) — polls 5s after previous execution completes.
 *       fixedDelay not fixedRate prevents overlapping executions if a poll takes
 *       longer than 5s under load.</li>
 *   <li>MAX_RETRY_COUNT=3 — rows with retryCount >= 3 are skipped permanently.</li>
 *   <li>KinesisAsyncClient.putRecord().join() — synchronous confirmation before
 *       marking PUBLISHED. Guarantees at-least-once delivery to Kinesis.</li>
 *   <li>partitionKey used as Kinesis partition key — councilId pre-computed at
 *       webhook write time. Guarantees ordering per council on the shard.</li>
 *   <li>@Transactional on processRow() — each row in its own transaction.</li>
 *   <li>runRelay() is public — OutboxRelaySteps calls it directly for deterministic
 *       test control. @Scheduled calls it in production.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import uk.co.pdintel.payment.domain.StripeEventOutbox;
import uk.co.pdintel.payment.repository.StripeEventOutboxRepository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final int MAX_RETRY_COUNT = 3;

    private final StripeEventOutboxRepository stripeEventOutboxRepository;
    private final KinesisAsyncClient kinesisClient;
    private final String streamName;

    public OutboxRelayService(StripeEventOutboxRepository stripeEventOutboxRepository,
                              KinesisAsyncClient kinesisClient,
                              @Value("${kinesis.stream-name:pd-payment-kinesis-euw2}") String streamName) {
        this.stripeEventOutboxRepository = stripeEventOutboxRepository;
        this.kinesisClient = kinesisClient;
        this.streamName = streamName;
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
            PutRecordRequest request = PutRecordRequest.builder()
                    .streamName(streamName)
                    .partitionKey(row.getPartitionKey())
                    .data(SdkBytes.fromString(row.getPayload(), StandardCharsets.UTF_8))
                    .build();

            kinesisClient.putRecord(request).join();

            row.setStatus("PUBLISHED");
            row.setPublishedAt(OffsetDateTime.now());
            stripeEventOutboxRepository.save(row);
            log.debug("Published event {} to Kinesis stream {}", row.getStripeEventId(), streamName);
        } catch (Exception e) {
            row.setRetryCount(row.getRetryCount() + 1);
            row.setStatus("FAILED");
            stripeEventOutboxRepository.save(row);
            log.error("Failed to publish event {} — retryCount now {}",
                    row.getStripeEventId(), row.getRetryCount(), e);
        }
    }
}
