@cognito
Feature: Cognito JWT Authentication

  As a Plany API consumer in production
  I want requests to be authenticated via AWS Cognito RS256 JWTs
  So that real user identity is verified against Cognito's public keys

  Background:
    Given the Cognito-backed application is running

  Scenario: Valid Cognito JWT grants access and returns correct user details
    Given I have a valid Cognito JWT for user "a1b2c3d4-0000-0000-0000-000000000001" with email "alice@plany.co.uk" and councilIds "123e4567-e89b-12d3-a456-426614174000"
    When I request GET "/api/v1/me" with Cognito auth
    Then the response status should be 200
    And the response body contains field "userId"
    And the response body contains field "email"
    And the response body contains field "councilIds"

  Scenario: Valid Cognito JWT returns the correct userId from the sub claim
    Given I have a valid Cognito JWT for user "a1b2c3d4-0000-0000-0000-000000000002" with email "bob@plany.co.uk" and councilIds "123e4567-e89b-12d3-a456-426614174000"
    When I request GET "/api/v1/me" with Cognito auth
    Then the response status should be 200
    And the response body contains "a1b2c3d4-0000-0000-0000-000000000002"

  Scenario: Valid Cognito JWT returns the correct email from the email claim
    Given I have a valid Cognito JWT for user "a1b2c3d4-0000-0000-0000-000000000003" with email "carol@plany.co.uk" and councilIds "123e4567-e89b-12d3-a456-426614174000"
    When I request GET "/api/v1/me" with Cognito auth
    Then the response status should be 200
    And the response body contains "carol@plany.co.uk"

  Scenario: Valid Cognito JWT with multiple council IDs returns all of them
    Given I have a valid Cognito JWT for user "a1b2c3d4-0000-0000-0000-000000000004" with email "dan@plany.co.uk" and councilIds "123e4567-e89b-12d3-a456-426614174000,223e4567-e89b-12d3-a456-426614174001"
    When I request GET "/api/v1/me" with Cognito auth
    Then the response status should be 200
    And the response body contains "123e4567-e89b-12d3-a456-426614174000"
    And the response body contains "223e4567-e89b-12d3-a456-426614174001"

  Scenario: Request with a tampered (invalid signature) token is rejected
    Given I have a tampered Cognito JWT
    When I request GET "/api/v1/me" with Cognito auth
    Then the response status should be 401

  Scenario: Request with an expired Cognito JWT is rejected
    Given I have an expired Cognito JWT for user "a1b2c3d4-0000-0000-0000-000000000005" with email "expired@plany.co.uk"
    When I request GET "/api/v1/me" with Cognito auth
    Then the response status should be 401

  Scenario: Request with a completely random string token is rejected
    Given I have a random string Bearer token
    When I request GET "/api/v1/me" with Cognito auth
    Then the response status should be 401

  Scenario: Request with no Authorization header is rejected
    Given I have no Cognito Authorization header
    When I request GET "/api/v1/me" with Cognito auth
    Then the response status should be 401

  Scenario: Actuator health endpoint is accessible without a Cognito token
    Given I have no Cognito Authorization header
    When I request GET "/actuator/health" with Cognito auth
    Then the response status should be 200

  Scenario: Stripe webhook endpoint is accessible without a Cognito token
    Given I have no Cognito Authorization header
    When I request POST "/api/v1/webhooks/stripe" with Cognito auth
    Then the response status should not be 401
