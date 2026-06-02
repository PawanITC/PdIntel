@subscription
Feature: Subscription creation

  As an authenticated Plany user
  I want to create a subscription for a council I manage
  So that the council gains access to Plany services
  Note: Stripe API calls are intercepted by WireMock in all scenarios

  Background:
    Given the application is running
    And I have a valid Bearer token

  @smoke
  Scenario: Successfully create a subscription for a managed council
    Given Stripe will successfully create a customer
    And Stripe will successfully create a subscription with client secret "pi_test_secret_abc123"
    When I submit a subscription request for my council with plan "Basic" and priceId "price_basic_monthly"
    Then the response status should be 201
    And the response body contains field "subscriptionId"
    And the response body contains field "clientSecret"
    And the response body contains field "stripeSubscriptionId"
    And the response body contains field "customerId"

  Scenario: Created subscription has status incomplete for PSD2 compliance
    Given Stripe will successfully create a customer
    And Stripe will successfully create a subscription with client secret "pi_test_secret_abc123"
    When I submit a subscription request for my council with plan "Basic" and priceId "price_basic_monthly"
    Then the response status should be 201
    And the response body field "status" equals "incomplete"

  Scenario: Cannot create subscription for a council not managed by the user
    When I submit a subscription request for an unmanaged council with plan "Basic" and priceId "price_basic_monthly"
    Then the response status should be 403

  Scenario: Cannot create a duplicate subscription for the same council
    Given Stripe will successfully create a customer
    And Stripe will successfully create a subscription with client secret "pi_test_secret_abc123"
    And a subscription already exists for my council
    When I submit a subscription request for my council with plan "Basic" and priceId "price_basic_monthly"
    Then the response status should be 409

  Scenario: Unauthenticated request is rejected
    Given I have no Authorization header
    When I submit a subscription request for my council with plan "Basic" and priceId "price_basic_monthly"
    Then the response status should be 401

  Scenario Outline: Request with missing required field is rejected
    When I submit a subscription request with missing "<field>"
    Then the response status should be 400
    And the response body contains field "errors"

    Examples:
      | field     |
      | planName  |
      | priceId   |
      | councilId |
