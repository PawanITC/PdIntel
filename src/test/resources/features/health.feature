@smoke @health
Feature: Application health

  As an operations engineer
  I want the health endpoint to be available and report the application status
  So that I can verify the service is running correctly after deployment

  Scenario: Health endpoint returns HTTP 200
    When I request the application health endpoint
    Then the response status should be 200

  Scenario: Health endpoint reports application is UP
    When I request the application health endpoint
    Then the response status should be 200
    And the health status should be "UP"
