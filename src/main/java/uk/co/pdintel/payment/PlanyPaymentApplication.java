package uk.co.pdintel.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Plany Payment Service.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@SpringBootApplication combines @Configuration, @EnableAutoConfiguration, and @ComponentScan —
 *       no extra annotations needed at this level.</li>
 *   <li>Base package is uk.co.pdintel.payment — Spring scans the full tree automatically.</li>
 *   <li>No @EnableKafka / @EnableJpa — auto-configuration handles both.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

@SpringBootApplication
public class PlanyPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlanyPaymentApplication.class, args);
    }
}
