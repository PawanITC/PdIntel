package uk.co.pdintel.payment.config;

/**
 * Kafka producer and consumer factory configuration.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@EnableKafka — activates @KafkaListener annotation processing on consumer beans.</li>
 *   <li>@EnableScheduling — activates @Scheduled on OutboxRelayService. Co-located here
 *       since scheduling and messaging are both infrastructure concerns.</li>
 *   <li>KafkaTemplate&lt;String, String&gt; bean — relay publishes with councilId as key
 *       and raw JSON payload as value. Both serialised as plain strings.</li>
 *   <li>Two separate ContainerFactory beans (access-control, audit) — each consumer
 *       must receive every message independently. Separate group-id per consumer
 *       ensures both get a copy of every event on the topic.</li>
 *   <li>MANUAL_IMMEDIATE ack mode — consumer commits offset only after successful DB
 *       write. No message is lost if the DB call fails; Kafka redelivers on restart.</li>
 *   <li>Producer config inherited from application.yml (acks=all, retries=3) —
 *       not repeated here to avoid duplication.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@EnableScheduling
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> accessControlContainerFactory() {
        return buildContainerFactory("plany-access-control");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> auditContainerFactory() {
        return buildContainerFactory("plany-audit");
    }

    private ConcurrentKafkaListenerContainerFactory<String, String> buildContainerFactory(
            String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory<>(props);
        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(factory);
        containerFactory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return containerFactory;
    }
}
