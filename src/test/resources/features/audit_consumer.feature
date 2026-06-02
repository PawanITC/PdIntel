@audit
Feature: Audit consumer — Kafka events to audit log

  As the Plany payment service
  I want every Stripe webhook event to be recorded in the audit log
  So that we have a complete, tamper-proof record for HMRC compliance and debugging

  Background:
    Given the application is running
    And a user exists with email "audit-test@plany.co.uk" and subscriptionStatus "none"

  @smoke
  Scenario: invoice.payment_succeeded is recorded in the audit log
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "invoice.payment_succeeded" for user "audit-test@plany.co.uk"
    Then an audit log entry exists for event type "PAYMENT_SUCCEEDED"
    And the audit log entry has actor_type "STRIPE"
    And the audit log entry has entity_type "SUBSCRIPTION"
    And the audit log entry has a council_id
    And the audit log entry has a created_at timestamp

  Scenario: customer.subscription.created is recorded in the audit log
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "customer.subscription.created" for user "audit-test@plany.co.uk"
    Then an audit log entry exists for event type "SUBSCRIPTION_CREATED"
    And the audit log entry has actor_type "STRIPE"
    And the audit log entry has entity_type "SUBSCRIPTION"

  Scenario: customer.subscription.deleted is recorded in the audit log
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "customer.subscription.deleted" for user "audit-test@plany.co.uk"
    Then an audit log entry exists for event type "SUBSCRIPTION_CANCELED"
    And the audit log entry has actor_type "STRIPE"
    And the audit log entry has entity_type "SUBSCRIPTION"

  Scenario: Audit log entry does not contain PII
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "invoice.payment_succeeded" for user "audit-test@plany.co.uk"
    Then an audit log entry exists for event type "PAYMENT_SUCCEEDED"
    And the audit log payload does not contain "audit-test@plany.co.uk"
    And the audit log payload does not contain personal data fields

  Scenario: Processing the same Kafka message twice writes only one audit entry
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "invoice.payment_succeeded" for user "audit-test@plany.co.uk"
    And the same Kafka message is published again
    Then exactly 1 audit log entry exists for event type "PAYMENT_SUCCEEDED"

  Scenario: Unknown event type is recorded with UNKNOWN classification
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "account.updated" for user "audit-test@plany.co.uk"
    Then an audit log entry exists for event type "UNKNOWN"
    And the audit log entry has actor_type "STRIPE"
