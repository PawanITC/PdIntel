package uk.co.pdintel.payment.repository;

/**
 * Spring Data JPA repository for the StripeEventOutbox entity.
 *
 * <p>Decisions:
 * <ul>
 *   <li>findByStripeEventId — used in WebhookSteps to assert the outbox row was
 *       written after a valid webhook is received.</li>
 *   <li>countByStripeEventId — used in WebhookSteps to assert exactly 1 row exists
 *       for the duplicate idempotency scenario.</li>
 *   <li>findByStatusOrderByCreatedAtAsc — relay polling query. Fetches PENDING rows
 *       oldest-first to guarantee FIFO processing order per partition key.</li>
 *   <li>All Spring Data derived queries — no @Query needed; method names express
 *       intent clearly and are verified at startup by Spring Data.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import uk.co.pdintel.payment.domain.StripeEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StripeEventOutboxRepository extends JpaRepository<StripeEventOutbox, UUID> {

    Optional<StripeEventOutbox> findByStripeEventId(String stripeEventId);

    long countByStripeEventId(String stripeEventId);

    List<StripeEventOutbox> findByStatusOrderByCreatedAtAsc(String status);

    List<StripeEventOutbox> findByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            java.util.List<String> statuses, int maxRetryCount);
}
