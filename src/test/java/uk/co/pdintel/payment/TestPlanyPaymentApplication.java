package uk.co.pdintel.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Local dev launcher that starts the full application with an in-process embedded Kafka broker.
 *
 * <p>Activates the "dev" + "local" Spring profiles:
 * <ul>
 *   <li>dev  — connects to RDS via DB_URL/DB_USERNAME/DB_PASSWORD from .env</li>
 *   <li>local — overrides kafka.bootstrap-servers to the embedded broker address</li>
 * </ul>
 *
 * <p>Run via: {@code mvn spring-boot:test-run -Dspring-boot.run.main-class=uk.co.pdintel.payment.TestPlanyPaymentApplication}
 *
 * @author Pawan
 * @copyright 2026
 */
@EmbeddedKafka(
        partitions = 1,
        topics = {"plany.stripe.webhook-raw.v1"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"}
)
public class TestPlanyPaymentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PlanyPaymentApplication.class);
        app.setAdditionalProfiles("dev", "local");
        app.run(args);
    }
}
