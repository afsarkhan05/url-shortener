Feature: URL Shortening
  As a user of the URL shortener
  I want to create and access short URLs
  So that I can share long links easily

  Scenario: Shorten a new valid URL
    Given the URL shortener service is running
    And no short URL exists for "https://example.com/very/long/url"
    When I request to shorten "https://example.com/very/long/url"
    Then a new short URL should be created

  Scenario: Retrieve original URL from a valid short URL
    Given the URL shortener service is running
    And a short URL "shorty12" maps to "https://example.com/another/long/url"
    When I access "shorty12"
    Then I should be redirected to "https://example.com/another/long/url"
    And the click count for "shorty12" should be incremented

  Scenario: Attempt to shorten an invalid URL
    Given the URL shortener service is running
    When I request to shorten "invalid-url"
    Then the service should return a bad request error

  Scenario: Attempt to access a non-existent short URL
    Given the URL shortener service is running
    And no short URL "nonexistent" exists
    When I access "nonexistent"
    Then the service should return a not found error

  Scenario: Attempt to use a custom short code that already exists
    Given the URL shortener service is running
    And a short URL "customcode" already maps to "https://example.com/existing/target"
    When I request to shorten "https://example.com/new/target" with custom code "customcode"
    Then the service should return a conflict error

  Scenario: Shorten an URL with custom short code
    Given the URL shortener service is running
    And no short URL "mycustomlink" exists
    When I request to shorten "https://example.com/cool/new/link" with custom code "mycustomlink"
    Then a new short URL "mycustomlink" should be created for "https://example.com/cool/new/link"

  Scenario: Shorten an URL with expiration
    Given the URL shortener service is running
    When I request to shorten "https://example.com/temporary/url" with expiration of 1 minute
    Then a new short URL should be created with an expiration time in the future

  Scenario: Access an expired short URL
    Given the URL shortener service is running
    And a short URL "expiredurl" maps to "https://example.com/old/url" and expired 5 minutes ago
    When I access "expiredurl"
    Then the service should return a not found error
