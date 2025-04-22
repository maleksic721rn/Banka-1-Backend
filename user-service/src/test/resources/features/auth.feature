Feature: Authentication and authorization
  Scenario: Successful authorization
    Given Petar (petar.petrovic@banka.com) is an employee
    When Petar logs in with the valid credentials petar.petrovic@banka.com and Per@12345
    And Petar tries to view the details of the customer with the ID 101
    Then the request is authorized

  Scenario: Unsuccessful authentication
    Given Petar (petar.petrovic@banka.com) is an employee
    And Petar is not logged in
    When Petar tries to view the details of the customer with the ID 101
    Then the request is not authorized

  Scenario: Unsuccessful authorization
    Given Jovana (jovana.jovanovic@banka.com) is an employee
    When Jovana logs in with the valid credentials jovana.jovanovic@banka.com and Jovan@12345
    And Jovana tries to view the details of the employee with the ID 101
    Then the request is not authorized

  Scenario: User requests a password reset
    Given a user with email "admin@admin.com" is an employee
    When the user requests a password reset
    Then a password reset email should be sent
    And the email should contain a reset link


