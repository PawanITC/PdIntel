package uk.co.pdintel.payment.bdd;

/**
 * Test helper for Kinesis interactions in BDD scenarios.
 *
 * <p>Two modes:
 * <ol>
 *   <li>Relay assertions — getPublishedRecords() reads from KinesisMockConfig.capturedRecords,
 *       which the real KinesisAsyncClient mock populates when OutboxRelayService calls putRecord().
 *       Snapshot taken at construction time so each scenario sees only its own records.</li>
 *   <li>Consumer publishing — publish(partitionKey, payload) calls
 *       KinesisConsumerService.dispatchRecord() synchronously, bypassing the stream entirely.
 *       The consumers process the payload immediately and update the DB before the step returns.</li>
 * </ol>
 *
 * <p>@ScenarioScope — fresh instance per scenario. capturedRecords offset recorded at
 * construction avoids records from prior scenarios leaking into relay assertions.
 *
 * @author Pawan
 * @copyright 2026
 */

import io.cucumber.spring.ScenarioScope;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import uk.co.pdintel.payment.config.KinesisMockConfig;
import uk.co.pdintel.payment.service.KinesisConsumerService;

import java.util.List;

@Component
@ScenarioScope
public class KinesisTestHelper {

    private final KinesisConsumerService kinesisConsumerService;
    private int startOffset;

    public KinesisTestHelper(KinesisConsumerService kinesisConsumerService) {
        this.kinesisConsumerService = kinesisConsumerService;
    }

    @PostConstruct
    public void recordStartOffset() {
        startOffset = KinesisMockConfig.capturedRecords.size();
    }

    /** Dispatch a record directly to consumers — used by access-control and audit scenarios. */
    public void publish(String partitionKey, String payload) {
        kinesisConsumerService.dispatchRecord(payload);
    }

    /** Records captured by the mock KinesisAsyncClient since this scenario started — relay scenarios only. */
    public List<KinesisMockConfig.CapturedRecord> getPublishedRecords() {
        List<KinesisMockConfig.CapturedRecord> all = KinesisMockConfig.capturedRecords;
        return List.copyOf(all.subList(startOffset, all.size()));
    }

    public boolean hasRecordWithPayloadContaining(String fragment) {
        return getPublishedRecords().stream()
                .anyMatch(r -> r.payload().contains(fragment));
    }

    public record PublishedRecord(String partitionKey, String payload) {}
}
