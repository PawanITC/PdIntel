package uk.co.pdintel.payment.repository;

/**
 * Spring Data JPA repository for the AuditLog entity.
 *
 * <p>Decisions:
 * <ul>
 *   <li>findFirstByEventType — used in AuditConsumerSteps to retrieve the most recent
 *       audit entry for a given event type within a BDD scenario.</li>
 *   <li>countByEventType — used in AuditConsumerSteps to assert exactly 1 entry exists
 *       for the consumer idempotency scenario.</li>
 *   <li>findByEntityId — supports future compliance queries: "show all audit events
 *       for subscription X" or "show all events for user Y".</li>
 *   <li>findByCouncilIdOrderByCreatedAtDesc — supports council-scoped audit reporting
 *       for HMRC MTD compliance and per-council admin views.</li>
 *   <li>All Spring Data derived queries — no @Query needed. Method names are
 *       verified at startup by Spring Data, failing fast on typos.</li>
 *   <li>No delete methods exposed — audit_log is insert-only by design.
 *       deleteAll() accessible only in @Before test hooks via JpaRepository.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import uk.co.pdintel.payment.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Optional<AuditLog> findFirstByEventType(String eventType);

    long countByEventType(String eventType);

    List<AuditLog> findByEntityId(UUID entityId);

    List<AuditLog> findByCouncilIdOrderByCreatedAtDesc(UUID councilId);
}
