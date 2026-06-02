package uk.co.pdintel.payment.bdd;

/**
 * Test helper for consuming and asserting on Kafka messages in BDD scenarios.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Uses raw KafkaConsumer — gives fine-grained control over poll/timeout.
 *       @KafkaListener is fire-and-forget and harder to assert synchronously.</li>
 *   <li>ConsumerRecord&lt;String, String&gt; — key is the Kafka partition key (councilId),
 *       value is the raw payload. Both are asserted in relay scenarios.</li>
 *   <li>poll() with 5-second timeout — fails cleanly if no message arrives,
 *       avoids blocking indefinitely in slow CI environments.</li>
 *   <li>auto.offset.reset=earliest — consumer reads from partition start,
 *       no messages missed even if consumer starts after publish.</li>
 *   <li>Unique group-id per instance — prevents test consumers interfering with
 *       the application's real consumer groups.</li>
 *   <li>@ScenarioScope — fresh instance per scenario, own offset tracking,
 *       no cross-scenario message leakage.</li>
 *   <li>@PreDestroy closes consumer — releases partition assignment cleanly,
 *       prevents rebalance noise in test logs.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import io.cucumber.spring.ScenarioScope;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Component
@ScenarioScope
public class KafkaTestHelper {

    private final KafkaConsumer<String, String> consumer;

    public KafkaTestHelper(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-helper-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        this.consumer = new KafkaConsumer<>(props);
    }

    public List<ConsumerRecord<String, String>> consumeMessages(String topic, int expectedCount,
                                                                 Duration timeout) {
        consumer.subscribe(Collections.singletonList(topic));
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline && records.size() < expectedCount) {
            ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
            polled.forEach(records::add);
        }

        return records;
    }

    public List<ConsumerRecord<String, String>> consumeMessages(String topic, Duration timeout) {
        return consumeMessages(topic, Integer.MAX_VALUE, timeout);
    }

    public boolean hasMessageWithKeyContaining(String topic, String keyFragment, Duration timeout) {
        return consumeMessages(topic, timeout).stream()
                .anyMatch(r -> r.key() != null && r.key().contains(keyFragment));
    }

    public boolean hasMessageWithValueContaining(String topic, String valueFragment,
                                                  Duration timeout) {
        return consumeMessages(topic, timeout).stream()
                .anyMatch(r -> r.value() != null && r.value().contains(valueFragment));
    }

    public boolean hasNoMessageWithValueContaining(String topic, String valueFragment,
                                                    Duration timeout) {
        return consumeMessages(topic, timeout).stream()
                .noneMatch(r -> r.value() != null && r.value().contains(valueFragment));
    }

    @PreDestroy
    public void close() {
        consumer.close();
    }
}
