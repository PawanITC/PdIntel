package uk.co.pdintel.payment.config;

/**
 * Test-only Spring configuration that replaces the real KinesisAsyncClient bean with
 * an in-memory stub. Captured PutRecord calls are surfaced via KinesisTestHelper so
 * BDD relay scenarios can assert on partition key and payload without a running stream.
 *
 * @author Pawan
 * @copyright 2026
 */

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@TestConfiguration
public class KinesisMockConfig {

    public static final List<CapturedRecord> capturedRecords = new ArrayList<>();

    @Bean
    @Primary
    public KinesisAsyncClient kinesisAsyncClient() {
        return new KinesisAsyncClient() {

            @Override
            public CompletableFuture<PutRecordResponse> putRecord(PutRecordRequest request) {
                String payload = request.data().asString(StandardCharsets.UTF_8);
                capturedRecords.add(new CapturedRecord(request.partitionKey(), payload));
                return CompletableFuture.completedFuture(
                        PutRecordResponse.builder()
                                .shardId("shardId-000000000000")
                                .sequenceNumber("1")
                                .build());
            }

            @Override
            public String serviceName() {
                return "kinesis-mock";
            }

            @Override
            public void close() {}
        };
    }

    public record CapturedRecord(String partitionKey, String payload) {}
}
