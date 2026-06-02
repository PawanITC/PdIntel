package uk.co.pdintel.payment.domain;

/**
 * JPA entity mirroring a Stripe Subscription object locally.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@ManyToOne(LAZY) to User — a user can have multiple subscriptions;
 *       lazy loading avoids pulling the full User graph on every subscription query.</li>
 *   <li>councilId denormalised here — a subscription IS for a specific council.
 *       Avoids joining to user_councils every time the Kafka partition key is needed.</li>
 *   <li>amountPence as Long (BIGINT) — aligns with Stripe's pence-based API format.</li>
 *   <li>status as String not enum — Stripe may add new statuses; VARCHAR avoids migration.</li>
 *   <li>councilId is updatable=false — a subscription cannot be reassigned to another council.</li>
 *   <li>currentPeriodStart/End and canceledAt are nullable — only set by webhook events.</li>
 *   <li>Public constructor enforces all mandatory fields — status, planName, amountPence,
 *       councilId can never be null on a new Subscription.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "stripe_subscription_id", nullable = false, unique = true)
    private String stripeSubscriptionId;

    @Column(name = "stripe_price_id", nullable = false)
    private String stripePriceId;

    @Column(nullable = false)
    private String status;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Column(name = "amount_pence", nullable = false)
    private Long amountPence;

    @Column(nullable = false, length = 3)
    private String currency = "gbp";

    @Column(name = "council_id", nullable = false, updatable = false)
    private UUID councilId;

    @Column(name = "current_period_start")
    private OffsetDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private OffsetDateTime currentPeriodEnd;

    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Subscription() {
    }

    public Subscription(User user, String stripeSubscriptionId, String stripePriceId,
                        String status, String planName, Long amountPence, UUID councilId) {
        this.user = user;
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.stripePriceId = stripePriceId;
        this.status = status;
        this.planName = planName;
        this.amountPence = amountPence;
        this.councilId = councilId;
    }

    public UUID getId() { return id; }

    public User getUser() { return user; }

    public String getStripeSubscriptionId() { return stripeSubscriptionId; }

    public String getStripePriceId() { return stripePriceId; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public String getPlanName() { return planName; }

    public Long getAmountPence() { return amountPence; }

    public String getCurrency() { return currency; }

    public UUID getCouncilId() { return councilId; }

    public OffsetDateTime getCurrentPeriodStart() { return currentPeriodStart; }

    public void setCurrentPeriodStart(OffsetDateTime currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }

    public OffsetDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }

    public void setCurrentPeriodEnd(OffsetDateTime currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public OffsetDateTime getCanceledAt() { return canceledAt; }

    public void setCanceledAt(OffsetDateTime canceledAt) { this.canceledAt = canceledAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
