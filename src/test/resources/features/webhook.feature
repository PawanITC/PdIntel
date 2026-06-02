@webhook
Feature: Stripe webhook handling

  As the Plany payment service
  I want to securely receive and process Stripe webhook events
  So that subscription state is updated reliably without data loss or duplication

  Background:
    Given the application is running
    And I have no Authorization header

  @smoke
  Scenario: Valid webhook event is accepted and written to outbox
    Given a valid Stripe-Signature for event type "invoice.payment_succeeded"
    When I post the webhook payload to "/api/v1/webhooks/stripe"
    Then the response status should be 200
    And the stripe event outbox contains the event

  Scenario: Duplicate webhook event is accepted but not written twice
    Given a valid Stripe-Signature for event type "invoice.payment_succeeded"
    And the event has already been processed
    When I post the webhook payload to "/api/v1/webhooks/stripe"
    Then the response status should be 200
    And the stripe event outbox contains exactly 1 entry for the event

  Scenario: Webhook event with invalid signature is rejected
    Given an invalid Stripe-Signature header
    When I post the webhook payload to "/api/v1/webhooks/stripe"
    Then the response status should be 400

  Scenario: Webhook event with missing signature header is rejected
    Given no Stripe-Signature header
    When I post the webhook payload to "/api/v1/webhooks/stripe"
    Then the response status should be 400

  Scenario: Webhook endpoint does not require JWT authentication
    Given a valid Stripe-Signature for event type "customer.subscription.created"
    When I post the webhook payload to "/api/v1/webhooks/stripe"
    Then the response status should not be 401

  Scenario: Webhook handler responds within 30 milliseconds
    Given a valid Stripe-Signature for event type "invoice.payment_succeeded"
    When I post the webhook payload to "/api/v1/webhooks/stripe"
    Then the response status should be 200
    And the response time should be less than 30 milliseconds
