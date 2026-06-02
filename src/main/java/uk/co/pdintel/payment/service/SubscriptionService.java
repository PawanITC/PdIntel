package uk.co.pdintel.payment.service;

/**
 * Orchestrates Stripe subscription creation for Plany users.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Council membership validated first — fail fast with 403 before any Stripe API call.</li>
 *   <li>Duplicate check before Stripe call — prevents orphaned Stripe subscriptions
 *       for a user+council combination that already has one.</li>
 *   <li>payment_behavior=DEFAULT_INCOMPLETE — mandatory for UK PSD2/SCA compliance.
 *       Subscription stays incomplete until 3DS/SCA confirms payment.</li>
 *   <li>automatic_tax=enabled — Stripe Tax handles UK VAT automatically.</li>
 *   <li>expand=latest_invoice.payment_intent — Stripe returns only IDs by default.
 *       Must expand to get client_secret for the frontend Payment Element.</li>
 *   <li>Amount sourced from Stripe price item — server is the source of truth,
 *       frontend amount is never trusted.</li>
 *   <li>User loaded via email from UserPrincipal — if user doesn't exist in our DB
 *       yet, create them. Handles first-time users seamlessly.</li>
 *   <li>@Transactional — DB save is the last operation. If it fails, no orphaned
 *       subscription row. Stripe subscription exists but will be found on retry.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionCreateParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.pdintel.payment.api.CreateSubscriptionRequest;
import uk.co.pdintel.payment.api.CreateSubscriptionResponse;
import uk.co.pdintel.payment.domain.User;
import uk.co.pdintel.payment.exception.CouncilAccessDeniedException;
import uk.co.pdintel.payment.exception.DuplicateSubscriptionException;
import uk.co.pdintel.payment.repository.SubscriptionRepository;
import uk.co.pdintel.payment.repository.UserRepository;
import uk.co.pdintel.payment.security.UserPrincipal;

import java.util.List;

@Service
public class SubscriptionService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StripeCustomerService stripeCustomerService;

    public SubscriptionService(UserRepository userRepository,
                               SubscriptionRepository subscriptionRepository,
                               StripeCustomerService stripeCustomerService) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.stripeCustomerService = stripeCustomerService;
    }

    @Transactional
    public CreateSubscriptionResponse createSubscription(UserPrincipal principal,
                                                         CreateSubscriptionRequest request) {
        if (!principal.councilIds().contains(request.councilId())) {
            throw new CouncilAccessDeniedException(request.councilId());
        }

        User user = userRepository.findByEmail(principal.email())
                .orElseGet(() -> userRepository.save(new User(principal.email())));

        subscriptionRepository.findByUserIdAndCouncilId(user.getId(), request.councilId())
                .ifPresent(existing -> {
                    throw new DuplicateSubscriptionException(user.getId(), request.councilId());
                });

        String customerId = stripeCustomerService.findOrCreateCustomer(user);

        try {
            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                    .setCustomer(customerId)
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(request.priceId())
                            .build())
                    .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                    .setAutomaticTax(SubscriptionCreateParams.AutomaticTax.builder()
                            .setEnabled(true)
                            .build())
                    .addAllExpand(List.of("latest_invoice.payment_intent"))
                    .build();

            Subscription stripeSubscription = Subscription.create(params);

            String clientSecret = stripeSubscription
                    .getLatestInvoiceObject()
                    .getPaymentIntentObject()
                    .getClientSecret();

            Long amountPence = stripeSubscription.getItems().getData().get(0)
                    .getPrice().getUnitAmount();

            uk.co.pdintel.payment.domain.Subscription subscription =
                    new uk.co.pdintel.payment.domain.Subscription(
                            user,
                            stripeSubscription.getId(),
                            request.priceId(),
                            stripeSubscription.getStatus(),
                            request.planName(),
                            amountPence,
                            request.councilId());

            uk.co.pdintel.payment.domain.Subscription saved =
                    subscriptionRepository.save(subscription);

            return new CreateSubscriptionResponse(
                    saved.getId(),
                    stripeSubscription.getId(),
                    clientSecret,
                    stripeSubscription.getStatus(),
                    customerId);

        } catch (StripeException e) {
            throw new RuntimeException("Failed to create Stripe subscription for user: "
                    + user.getId(), e);
        }
    }
}
