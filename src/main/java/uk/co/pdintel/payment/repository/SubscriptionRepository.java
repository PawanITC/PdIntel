package uk.co.pdintel.payment.repository;

/**
 * Spring Data JPA repository for the Subscription entity.
 *
 * <p>Decisions:
 * <ul>
 *   <li>findByStripeSubscriptionId — used by webhook handler to resolve which local
 *       subscription a Stripe event belongs to (e.g. customer.subscription.updated).</li>
 *   <li>findByUserIdAndCouncilId — used to check if a user already has an active
 *       subscription for a specific council before creating a new one.</li>
 *   <li>findByUserId — used to list all subscriptions for a user.</li>
 *   <li>All return Optional or List — forces callers to handle not-found explicitly.</li>
 *   <li>No @Query needed — Spring Data derives all queries from method names.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import uk.co.pdintel.payment.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<Subscription> findByUserId(UUID userId);

    Optional<Subscription> findByUserIdAndCouncilId(UUID userId, UUID councilId);
}
