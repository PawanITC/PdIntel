@auth
Feature: Authentication

  As a Plany API consumer
  I want requests to be authenticated via a Bearer token
  So that only authorised users can access protected resources

  Background:
    Given the application is running

  Scenario: Authenticated request returns current user details
    Given I have a valid Bearer token
    When I request GET "/api/v1/me"
    Then the response status should be 200
    And the response body contains field "userId"
    And the response body contains field "email"
    And the response body contains field "councilIds"

  Scenario: Authenticated user has multiple council IDs
    Given I have a valid Bearer token
    When I request GET "/api/v1/me"
    Then the response status should be 200
    And the "councilIds" field contains more than 0 entries

  Scenario: Request with invalid token is rejected
    Given I have an invalid Bearer token
    When I request GET "/api/v1/me"
    Then the response status should be 401

  Scenario: Request with missing Authorization header is rejected
    Given I have no Authorization header
    When I request GET "/api/v1/me"
    Then the response status should be 401

  Scenario: Stripe webhook endpoint is accessible without a token
    Given I have no Authorization header
    When I request POST "/api/v1/webhooks/stripe"
    Then the response status should not be 401

  Scenario: Actuator health endpoint is accessible without a token
    Given I have no Authorization header
    When I request GET "/actuator/health"
    Then the response status should be 200
