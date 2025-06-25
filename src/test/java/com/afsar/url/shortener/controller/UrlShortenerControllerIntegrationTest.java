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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = UrlShortenerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers // Enable Testcontainers for this test class
@DisplayName("UrlShortenerController Integration Tests")
class UrlShortenerControllerIntegrationTest {

    // PostgreSQL Container
    @Container
    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpassword");

    // Redis Container
    @Container
    public static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // Dynamic properties to connect Spring Boot to Testcontainers-managed services
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379).toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        urlMappingRepository.deleteAll(); // Clear DB before each test
        // RedisTemplate's connection factory is managed by Spring,
        // and Testcontainers ensures a fresh Redis container for each test run (or class for static).
        // No need to manually flush in this setup.
    }

    // ... (Rest of the test methods remain the same as provided previously)
    // For example:
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
    // ...
}
