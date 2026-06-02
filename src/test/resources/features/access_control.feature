@access-control
Feature: Access control consumer — subscription status updates

  As the Plany payment service
  I want the access control Kafka consumer to update subscription status
  So that users gain or lose access to Plany services based on payment events

  Background:
    Given the application is running
    And a user exists with email "consumer-test@plany.co.uk" and subscriptionStatus "none"

  @smoke
  Scenario: invoice.payment_succeeded sets subscription status to active
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "invoice.payment_succeeded" for user "consumer-test@plany.co.uk"
    Then the subscription status for user "consumer-test@plany.co.uk" should be "active"

  Scenario: customer.subscription.deleted sets subscription status to canceled
    Given the user "consumer-test@plany.co.uk" has subscriptionStatus "active"
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "customer.subscription.deleted" for user "consumer-test@plany.co.uk"
    Then the subscription status for user "consumer-test@plany.co.uk" should be "canceled"

  Scenario: customer.subscription.updated with past_due suspends access
    Given the user "consumer-test@plany.co.uk" has subscriptionStatus "active"
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "customer.subscription.updated" with status "past_due" for user "consumer-test@plany.co.uk"
    Then the subscription status for user "consumer-test@plany.co.uk" should be "past_due"

  Scenario: customer.subscription.updated with trialing grants trial access
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "customer.subscription.updated" with status "trialing" for user "consumer-test@plany.co.uk"
    Then the subscription status for user "consumer-test@plany.co.uk" should be "trialing"

  Scenario: Unknown event type is ignored gracefully
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "account.updated" for user "consumer-test@plany.co.uk"
    Then the subscription status for user "consumer-test@plany.co.uk" should be "none"

  Scenario: Processing the same event twice does not change state
    When a Kafka message is published to "plany.stripe.webhook-raw.v1" with event type "invoice.payment_succeeded" for user "consumer-test@plany.co.uk"
    And the same Kafka message is published again
    Then the subscription status for user "consumer-test@plany.co.uk" should be "active"
