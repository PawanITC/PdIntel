package uk.co.pdintel.payment.repository;

/**
 * Spring Data JPA repository for the ProcessedStripeEvent entity.
 *
 * <p>Decisions:
 * <ul>
 *   <li>JpaRepository&lt;ProcessedStripeEvent, String&gt; — PK is String (stripeEventId),
 *       not UUID. existsById() and save() are the only operations needed.</li>
 *   <li>No custom queries — existsById(stripeEventId) inherited from JpaRepository
 *       provides O(1) PK lookup for the idempotency check.</li>
 *   <li>Used in WebhookService: save() throws DataIntegrityViolationException on
 *       duplicate PK — caught and treated as "already processed".</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import uk.co.pdintel.payment.domain.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, String> {
}
