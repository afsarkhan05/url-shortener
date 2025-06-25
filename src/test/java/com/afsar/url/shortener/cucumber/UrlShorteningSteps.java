package com.afsar.url.shortener.cucumber;

import com.afsar.url.shortener.UrlShortenerApplication;
import com.afsar.url.shortener.dto.ShortenRequest;
import com.afsar.url.shortener.model.UrlMapping;
import com.afsar.url.shortener.repository.UrlMappingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest(classes = UrlShortenerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test") // Use the test profile with H2 and embedded Redis
public class UrlShorteningSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private MvcResult latestResult;
    private String currentShortCode;

    @Before // This runs before each scenario
    public void setup() {
        urlMappingRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        latestResult = null;
        currentShortCode = null;
    }

    @Given("the URL shortener service is running")
    public void theUrlShortenerServiceIsRunning() {
        // This is implicitly handled by @SpringBootTest
        assertTrue(true, "Service is assumed to be running.");
    }

    @And("no short URL exists for {string}")
    public void noShortUrlExistsFor(String longUrl) {
        urlMappingRepository.findByLongUrl(longUrl).ifPresent(urlMappingRepository::delete);
    }

    @When("I request to shorten {string}")
    public void iRequestToShorten(String longUrl) throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);
        latestResult = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    @Then("a new short URL should be created")
    public void aNewShortUrlShouldBeCreated() throws Exception {
        latestResult.andExpect(MockMvcResultMatchers.status().isCreated());
        String shortUrl = latestResult.getResponse().getContentAsString();
        currentShortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
        assertTrue(urlMappingRepository.findById(currentShortCode).isPresent());
    }

    @And("a short URL {string} maps to {string}")
    public void aShortUrlMapsTo(String shortCode, String longUrl) {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode(shortCode);
        mapping.setLongUrl(longUrl);
        mapping.setCreatedAt(LocalDateTime.now());
        urlMappingRepository.save(mapping);
        redisTemplate.opsForValue().set(shortCode, longUrl); // Also put in cache
    }

    @When("I access {string}")
    public void iAccess(String shortCode) throws Exception {
        currentShortCode = shortCode; // Store for later assertions
        latestResult = mockMvc.perform(get("/" + shortCode)).andReturn();
    }

    @Then("I should be redirected to {string}")
    public void iShouldBeRedirectedTo(String longUrl) throws Exception {
        latestResult.andExpect(MockMvcResultMatchers.status().isFound()) // 302
                .andExpect(redirectedUrl(longUrl));
    }

    @And("the click count for {string} should be incremented")
    public void theClickCountForShouldBeIncremented(String shortCode) {
        UrlMapping updatedMapping = urlMappingRepository.findById(shortCode).orElseThrow();
        assertEquals(1, updatedMapping.getClicks()); // Assuming it's the first click in this test
    }

    @Then("the service should return a bad request error")
    public void theServiceShouldReturnABadRequestError() throws Exception {
        latestResult.andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @And("no short URL {string} exists")
    public void noShortUrlExists(String shortCode) {
        urlMappingRepository.findById(shortCode).ifPresent(urlMappingRepository::delete);
    }

    @Then("the service should return a not found error")
    public void theServiceShouldReturnANotFoundError() throws Exception {
        latestResult.andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @And("a short URL {string} already maps to {string}")
    public void aShortUrlAlreadyMapsTo(String customCode, String longUrl) {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode(customCode);
        mapping.setLongUrl(longUrl);
        mapping.setCreatedAt(LocalDateTime.now());
        urlMappingRepository.save(mapping);
    }

    @When("I request to shorten {string} with custom code {string}")
    public void iRequestToShortenWithCustomCode(String longUrl, String customCode) throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);
        request.setCustomShortCode(customCode);
        latestResult = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    @Then("the service should return a conflict error")
    public void theServiceShouldReturnAConflictError() throws Exception {
        latestResult.andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Then("a new short URL {string} should be created for {string}")
    public void aNewShortUrlShouldBeCreatedFor(String shortCode, String longUrl) throws Exception {
        latestResult.andExpect(MockMvcResultMatchers.status().isCreated());
        assertTrue(latestResult.getResponse().getContentAsString().endsWith(shortCode));
        Optional<UrlMapping> mapping = urlMappingRepository.findById(shortCode);
        assertTrue(mapping.isPresent());
        assertEquals(longUrl, mapping.get().getLongUrl());
    }

    @When("I request to shorten {string} with expiration of {int} minute")
    public void iRequestToShortenWithExpirationOfMinute(String longUrl, Integer minutes) throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);
        request.setExpirationMinutes(minutes);
        latestResult = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    @Then("a new short URL should be created with an expiration time in the future")
    public void aNewShortUrlShouldBeCreatedWithAnExpirationTimeInTheFuture() throws Exception {
        latestResult.andExpect(MockMvcResultMatchers.status().isCreated());
        String shortUrl = latestResult.getResponse().getContentAsString();
        String shortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);

        Optional<UrlMapping> mapping = urlMappingRepository.findById(shortCode);
        assertTrue(mapping.isPresent());
        assertNotNull(mapping.get().getExpiresAt());
        assertTrue(mapping.get().getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @And("a short URL {string} maps to {string} and expired {int} minutes ago")
    public void aShortURLMapsToAndExpiredMinutesAgo(String shortCode, String longUrl, int minutesAgo) {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode(shortCode);
        mapping.setLongUrl(longUrl);
        mapping.setCreatedAt(LocalDateTime.now().minusMinutes(minutesAgo + 10)); // Ensure creation is well before
        mapping.setExpiresAt(LocalDateTime.now().minusMinutes(minutesAgo));
        urlMappingRepository.save(mapping);
    }
}
