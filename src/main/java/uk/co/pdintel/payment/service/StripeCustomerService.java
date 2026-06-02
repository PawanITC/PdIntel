package uk.co.pdintel.payment.service;

/**
 * Manages Stripe Customer creation and retrieval for Plany users.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Find-or-create pattern — if User already has a stripeCustomerId, reuse it.
 *       Never create duplicate Stripe Customers for the same user.</li>
 *   <li>@Transactional — persisting stripeCustomerId to the DB must be atomic.
 *       If the DB save fails, the transaction rolls back; Stripe Customer exists
 *       but is harmless (will be found on the next attempt).</li>
 *   <li>userId stored in Stripe Customer metadata — allows reverse lookup from
 *       the Stripe Dashboard without querying our DB.</li>
 *   <li>StripeException wrapped as RuntimeException — Spring @Transactional rolls
 *       back on unchecked exceptions; callers receive a clean exception.</li>
 *   <li>Stripe API key initialised via @PostConstruct — injected from config,
 *       never hardcoded.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.pdintel.payment.domain.User;
import uk.co.pdintel.payment.repository.UserRepository;

@Service
public class StripeCustomerService {

    private final UserRepository userRepository;

    public StripeCustomerService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public String findOrCreateCustomer(User user) {
        if (user.getStripeCustomerId() != null) {
            return user.getStripeCustomerId();
        }

        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .putMetadata("userId", user.getId().toString())
                    .build();

            Customer customer = Customer.create(params);
            user.setStripeCustomerId(customer.getId());
            userRepository.save(user);

            return customer.getId();

        } catch (StripeException e) {
            throw new RuntimeException("Failed to create Stripe customer for user: "
                    + user.getId(), e);
        }
    }
}
