package uk.co.pdintel.payment.config;

/**
 * AWS Kinesis producer and consumer client configuration.
 *
 * <p>Decisions:
 * <ul>
 *   <li>KinesisAsyncClient — non-blocking I/O; producer puts use async send
 *       with .join() for at-least-once synchronous confirmation in the relay.</li>
 *   <li>Endpoint override — only wired when KINESIS_ENDPOINT is non-blank,
 *       allowing LocalStack in tests and local dev without touching prod paths.</li>
 *   <li>@EnableScheduling — activates @Scheduled on OutboxRelayService.</li>
 *   <li>AwsCredentialsProvider defaults to DefaultCredentialsChain — picks up
 *       IRSA pod credentials automatically in EKS; falls back to env vars / profile
 *       for local dev.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClientBuilder;

import java.net.URI;

@Configuration
@EnableScheduling
public class KinesisConfig {

    @Value("${kinesis.endpoint:}")
    private String endpoint;

    @Value("${kinesis.region:eu-west-2}")
    private String region;

    @Bean
    public KinesisAsyncClient kinesisAsyncClient() {
        KinesisAsyncClientBuilder builder = KinesisAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}
