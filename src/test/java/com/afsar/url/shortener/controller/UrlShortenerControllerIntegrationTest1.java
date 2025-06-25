package com.afsar.url.shortener.controller;

import com.afsar.url.shortener.UrlShortenerApplication;
import com.afsar.url.shortener.dto.ShortenRequest;
import com.afsar.url.shortener.model.UrlMapping;
import com.afsar.url.shortener.repository.UrlMappingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = UrlShortenerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test") // Use a test profile to configure H2 and embedded Redis
@DisplayName("UrlShortenerController Integration Tests")
class UrlShortenerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // For converting objects to JSON

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        urlMappingRepository.deleteAll(); // Clear DB before each test
        redisTemplate.getConnectionFactory().getConnection().flushAll(); // Clear Redis cache
    }

    @Test
    @DisplayName("Should successfully shorten a new URL")
    void shouldShortenNewUrlSuccessfully() throws Exception {
        String longUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);

        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("http://localhost"))) // Assuming base-url is localhost
                .andReturn();

        String shortUrl = result.getResponse().getContentAsString();
        String shortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);

        // Verify it's in the database
        assertTrue(urlMappingRepository.findById(shortCode).isPresent());
        assertEquals(longUrl, urlMappingRepository.findById(shortCode).get().getLongUrl());

        // Verify it's in Redis cache
        assertEquals(longUrl, redisTemplate.opsForValue().get(shortCode));
    }

    @Test
    @DisplayName("Should return existing short URL for a pre-existing long URL")
    void shouldReturnExistingShortUrlForPreExistingUrl() throws Exception {
        String longUrl = "https://www.dev-example.com/already/exists";
        String existingShortCode = "xyzXYZ";

        UrlMapping existingMapping = new UrlMapping();
        existingMapping.setShortCode(existingShortCode);
        existingMapping.setLongUrl(longUrl);
        existingMapping.setCreatedAt(LocalDateTime.now());
        urlMappingRepository.save(existingMapping);

        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl);

        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("http://localhost/" + existingShortCode)))
                .andReturn();

        String shortUrl = result.getResponse().getContentAsString();
        assertTrue(shortUrl.endsWith(existingShortCode));
        verify(urlMappingRepository, never()).save(any(UrlMapping.class)); // Should not save a new entry
    }

    @Test
    @DisplayName("Should handle invalid URL format for shortening")
    void shouldHandleInvalidUrlFormat() throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("not-a-valid-url");

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid URL format")));
    }

    @Test
    @DisplayName("Should handle custom short code collision")
    void shouldHandleCustomShortCodeCollision() throws Exception {
        String longUrl1 = "https://www.site1.com";
        String longUrl2 = "https://www.site2.com";
        String customCode = "myCustomCode";

        // First, create a mapping with the custom code
        UrlMapping existingMapping = new UrlMapping();
        existingMapping.setShortCode(customCode);
        existingMapping.setLongUrl(longUrl1);
        existingMapping.setCreatedAt(LocalDateTime.now());
        urlMappingRepository.save(existingMapping);

        // Now try to create another one with the same custom code
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl(longUrl2);
        request.setCustomShortCode(customCode);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Custom short code '" + customCode + "' already exists.")));
    }

    @Test
    @DisplayName("Should redirect successfully for existing short code")
    void shouldRedirectSuccessfully() throws Exception {
        String longUrl = "https://www.redirect-target.com/page";
        String shortCode = "redirT";

        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode(shortCode);
        mapping.setLongUrl(longUrl);
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setClicks(0);
        urlMappingRepository.save(mapping);
        redisTemplate.opsForValue().set(shortCode, longUrl); // Ensure cache is populated

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound()) // 302 Found for sendRedirect
                .andExpect(redirectedUrl(longUrl));

        // Verify click count incremented in DB
        UrlMapping updatedMapping = urlMappingRepository.findById(shortCode).orElseThrow();
        assertEquals(1, updatedMapping.getClicks());
    }

    @Test
    @DisplayName("Should return 404 for non-existent short code during redirect")
    void shouldReturn404ForNonExistentShortCode() throws Exception {
        mockMvc.perform(get("/nonExistent"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Short URL not found")));
    }

    @Test
    @DisplayName("Should return 404 for expired short code during redirect")
    void shouldReturn404ForExpiredShortCode() throws Exception {
        String longUrl = "https://www.expired-page.com";
        String shortCode = "expirD";

        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode(shortCode);
        mapping.setLongUrl(longUrl);
        mapping.setCreatedAt(LocalDateTime.now().minusDays(2));
        mapping.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired
        urlMappingRepository.save(mapping);

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Short URL has expired")));
    }
}
