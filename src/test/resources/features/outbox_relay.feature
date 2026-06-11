@relay
Feature: Outbox relay — Stripe events to Kinesis

  As the Plany payment service
  I want the outbox relay to publish pending Stripe events to Kinesis
  So that downstream consumers receive every event exactly once in council order

  Background:
    Given the application is running

  @smoke
  Scenario: PENDING outbox row is published to Kinesis and marked PUBLISHED
    Given a PENDING outbox row exists for event "evt_relay_001" of type "invoice.payment_succeeded" with councilId "123e4567-e89b-12d3-a456-426614174000"
    When the outbox relay runs
    Then a Kafka message is published to topic "pd-payment-kinesis-euw2"
    And the Kafka message partition key is "123e4567-e89b-12d3-a456-426614174000"
    And the outbox row for event "evt_relay_001" has status "PUBLISHED"

  Scenario: PUBLISHED outbox row is not re-published
    Given a PUBLISHED outbox row exists for event "evt_relay_002" of type "invoice.payment_succeeded" with councilId "123e4567-e89b-12d3-a456-426614174000"
    When the outbox relay runs
    Then no Kafka message is published for event "evt_relay_002"
    And the outbox row for event "evt_relay_002" has status "PUBLISHED"

  Scenario: FAILED outbox row below retry threshold is retried
    Given a FAILED outbox row exists for event "evt_relay_003" of type "invoice.payment_succeeded" with councilId "123e4567-e89b-12d3-a456-426614174000" and retry count 1
    When the outbox relay runs
    Then a Kafka message is published to topic "pd-payment-kinesis-euw2"
    And the outbox row for event "evt_relay_003" has status "PUBLISHED"

  Scenario: FAILED outbox row above retry threshold is not retried
    Given a FAILED outbox row exists for event "evt_relay_004" of type "invoice.payment_succeeded" with councilId "123e4567-e89b-12d3-a456-426614174000" and retry count 5
    When the outbox relay runs
    Then no Kafka message is published for event "evt_relay_004"
    And the outbox row for event "evt_relay_004" has status "FAILED"

  Scenario: Kinesis record payload contains the original Stripe event
    Given a PENDING outbox row exists for event "evt_relay_005" of type "customer.subscription.created" with councilId "123e4567-e89b-12d3-a456-426614174000"
    When the outbox relay runs
    Then the Kafka message payload contains "evt_relay_005"
    And the Kafka message payload contains "customer.subscription.created"
