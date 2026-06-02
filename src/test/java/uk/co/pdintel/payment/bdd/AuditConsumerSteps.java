package uk.co.pdintel.payment.bdd;

/**
 * Cucumber step definitions for audit consumer scenarios.
 *
 * <p>Decisions:
 * <ul>
 *   <li>No redefinition of shared steps — Given/When steps for user creation and
 *       Kafka publishing are already defined in AccessControlSteps and reused here.
 *       Redefining them would cause AmbiguousStepDefinitionException.</li>
 *   <li>lastAuditEntry stored as field — multiple And assertions in a scenario
 *       reference the same entry. Fetched once via Awaitility, then asserted many
 *       times without re-querying the DB.</li>
 *   <li>Awaitility with 10s timeout — audit consumer is async, same pattern as
 *       AccessControlSteps. Never Thread.sleep().</li>
 *   <li>@Before("@audit") cleans audit_log — tag-scoped hook, only runs before
 *       @audit scenarios. Clean state per scenario without affecting other suites.</li>
 *   <li>"personal data fields" explicitly defined as email, phone, name, address —
 *       not vague. Asserts specific PII field names are absent from the JSON payload.
 *       Enforces the non-negotiable: no PII in Kafka/audit payloads.</li>
 *   <li>findFirstByEventType() — single entry expected per scenario. "first" avoids
 *       ambiguity if multiple entries exist from parallel scenario runs.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import uk.co.pdintel.payment.domain.AuditLog;
import uk.co.pdintel.payment.repository.AuditLogRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AuditConsumerSteps {

    private static final List<String> PII_FIELD_NAMES = List.of(
            "email", "phone", "name", "address", "postcode", "dob");

    @Autowired
    private AuditLogRepository auditLogRepository;

    private AuditLog lastAuditEntry;

    @Before("@audit")
    public void cleanAuditLog() {
        auditLogRepository.deleteAll();
        lastAuditEntry = null;
    }

    @Then("an audit log entry exists for event type {string}")
    public void anAuditLogEntryExistsForEventType(String eventType) {
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(java.time.Duration.ofMillis(500))
                .untilAsserted(() -> {
                    AuditLog entry = auditLogRepository.findFirstByEventType(eventType)
                            .orElseThrow(() -> new AssertionError(
                                    "No audit log entry found for event type: " + eventType));
                    lastAuditEntry = entry;
                });
    }

    @And("the audit log entry has actor_type {string}")
    public void theAuditLogEntryHasActorType(String expectedActorType) {
        assertThat(lastAuditEntry.getActorType()).isEqualTo(expectedActorType);
    }

    @And("the audit log entry has entity_type {string}")
    public void theAuditLogEntryHasEntityType(String expectedEntityType) {
        assertThat(lastAuditEntry.getEntityType()).isEqualTo(expectedEntityType);
    }

    @And("the audit log entry has a council_id")
    public void theAuditLogEntryHasACouncilId() {
        assertThat(lastAuditEntry.getCouncilId()).isNotNull();
    }

    @And("the audit log entry has a created_at timestamp")
    public void theAuditLogEntryHasACreatedAtTimestamp() {
        assertThat(lastAuditEntry.getCreatedAt()).isNotNull();
    }

    @And("the audit log payload does not contain {string}")
    public void theAuditLogPayloadDoesNotContain(String forbidden) {
        if (lastAuditEntry.getPayload() != null) {
            assertThat(lastAuditEntry.getPayload())
                    .as("Audit log payload must not contain: %s", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    @And("the audit log payload does not contain personal data fields")
    public void theAuditLogPayloadDoesNotContainPersonalDataFields() {
        if (lastAuditEntry.getPayload() != null) {
            String payload = lastAuditEntry.getPayload().toLowerCase();
            PII_FIELD_NAMES.forEach(field ->
                    assertThat(payload)
                            .as("Audit log payload must not contain PII field: %s", field)
                            .doesNotContain("\"" + field + "\""));
        }
    }

    @Then("exactly {int} audit log entry exists for event type {string}")
    public void exactlyNAuditLogEntriesExistForEventType(int expectedCount, String eventType) {
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(java.time.Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(auditLogRepository.countByEventType(eventType))
                                .isEqualTo(expectedCount));
    }
}
