package uk.co.pdintel.payment.domain;

/**
 * JPA entity representing a Plany platform user.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@ElementCollection for councilIds — user_councils is a simple UUID join table.
 *       No separate UserCouncil entity needed; Set&lt;UUID&gt; mapped directly.</li>
 *   <li>FetchType.LAZY on councilIds — avoid loading council memberships unless needed.</li>
 *   <li>stripeCustomerId nullable — only populated when user initiates first payment.</li>
 *   <li>subscriptionStatus as String not enum — Stripe can add new statuses without a migration.</li>
 *   <li>protected no-arg constructor — JPA requirement; hidden from application code to
 *       enforce the public constructor that requires email.</li>
 *   <li>@CreationTimestamp / @UpdateTimestamp — Hibernate-managed, no @PrePersist hooks needed.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "stripe_customer_id", unique = true)
    private String stripeCustomerId;

    @Column(name = "subscription_status", nullable = false)
    private String subscriptionStatus = "none";

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "user_councils",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "council_id")
    private Set<UUID> councilIds = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {
    }

    public User(String email) {
        this.email = email;
    }

    public UUID getId() { return id; }

    public String getEmail() { return email; }

    public String getStripeCustomerId() { return stripeCustomerId; }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getSubscriptionStatus() { return subscriptionStatus; }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public Set<UUID> getCouncilIds() { return councilIds; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
