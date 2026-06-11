package uk.co.pdintel.payment;

import org.springframework.boot.SpringApplication;

/**
 * Local dev launcher — activates "dev" + "local" profiles.
 *
 * <p>dev  — connects to RDS via DB_URL/DB_USERNAME/DB_PASSWORD from .env
 * local — points Kinesis endpoint to LocalStack (http://localhost:4566)
 *
 * <p>Run via: {@code mvn spring-boot:test-run -Dspring-boot.run.main-class=uk.co.pdintel.payment.TestPlanyPaymentApplication}
 *
 * @author Pawan
 * @copyright 2026
 */
public class TestPlanyPaymentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PlanyPaymentApplication.class);
        app.setAdditionalProfiles("dev", "local");
        app.run(args);
    }
}
