package uk.co.pdintel.payment.service;

/**
 * Scheduled Kinesis shard poller — reads records from pd-payment-kinesis-euw2
 * and dispatches each payload to AccessControlConsumer and AuditConsumer.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Single shard iterator per stream — stream is single-shard in non-prod.
 *       For multi-shard prod, split this into one thread per shard.</li>
 *   <li>TRIM_HORIZON on first start — reads from the oldest available record so
 *       no events are missed after a restart.</li>
 *   <li>Shard iterator refreshed from AFTER_SEQUENCE_NUMBER after each batch —
 *       tracks position across poll cycles.</li>
 *   <li>Both consumers invoked per record — mirrors the two independent Kafka
 *       consumer groups (access-control and audit). Each deduplicates independently.</li>
 *   <li>Errors per record are caught and logged; the next record is still processed.
 *       Consumer-level idempotency (processed_stripe_events table) handles retries.</li>
 *   <li>@Scheduled(fixedDelay=1000) — 1s poll matches Kinesis recommended rate for
 *       low-latency processing without exceeding the 5 GetRecords calls/sec limit.</li>
 *   <li>dispatchRecord() is package-private — KinesisTestHelper calls it directly
 *       in BDD tests to inject records without a running stream.</li>
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
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class KinesisConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KinesisConsumerService.class);

    private final KinesisAsyncClient kinesisClient;
    private final AccessControlConsumer accessControlConsumer;
    private final AuditConsumer auditConsumer;
    private final String streamName;

    private volatile String shardIterator;

    public KinesisConsumerService(KinesisAsyncClient kinesisClient,
                                  AccessControlConsumer accessControlConsumer,
                                  AuditConsumer auditConsumer,
                                  @Value("${kinesis.stream-name:pd-payment-kinesis-euw2}") String streamName) {
        this.kinesisClient = kinesisClient;
        this.accessControlConsumer = accessControlConsumer;
        this.auditConsumer = auditConsumer;
        this.streamName = streamName;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        try {
            if (shardIterator == null) {
                shardIterator = fetchInitialIterator();
                if (shardIterator == null) return;
            }

            GetRecordsRequest request = GetRecordsRequest.builder()
                    .shardIterator(shardIterator)
                    .limit(100)
                    .build();

            GetRecordsResponse response = kinesisClient.getRecords(request).join();

            List<Record> records = response.records();
            for (Record record : records) {
                String payload = record.data().asString(StandardCharsets.UTF_8);
                dispatchRecord(payload);
            }

            shardIterator = response.nextShardIterator();

        } catch (Exception e) {
            log.error("Kinesis poll failed — resetting shard iterator", e);
            shardIterator = null;
        }
    }

    public void dispatchRecord(String payload) {
        try {
            accessControlConsumer.consume(payload);
        } catch (Exception e) {
            log.error("AccessControlConsumer failed on record", e);
        }
        try {
            auditConsumer.consume(payload);
        } catch (Exception e) {
            log.error("AuditConsumer failed on record", e);
        }
    }

    private String fetchInitialIterator() {
        try {
            ListShardsResponse shardsResponse = kinesisClient.listShards(
                    ListShardsRequest.builder().streamName(streamName).build()).join();

            if (shardsResponse.shards().isEmpty()) {
                log.warn("No shards found for stream {}", streamName);
                return null;
            }

            String shardId = shardsResponse.shards().get(0).shardId();

            GetShardIteratorResponse iteratorResponse = kinesisClient.getShardIterator(
                    GetShardIteratorRequest.builder()
                            .streamName(streamName)
                            .shardId(shardId)
                            .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
                            .build()).join();

            return iteratorResponse.shardIterator();

        } catch (Exception e) {
            log.error("Failed to fetch initial shard iterator for stream {}", streamName, e);
            return null;
        }
    }
}
