package com.afsar.url.shortener.service;

import com.afsar.url.shortener.exception.ShortCodeAlreadyExistsException;
import com.afsar.url.shortener.exception.UrlNotFoundException;
import com.afsar.url.shortener.model.UrlMapping;
import com.afsar.url.shortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlShortenerService Unit Tests")
class UrlShortenerServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations; // Mock Redis ValueOperations

    @InjectMocks
    private UrlShortenerService urlShortenerService;

    @BeforeEach
    void setUp() {
        // Mock RedisTemplate's opsForValue() to return our mock valueOperations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Inject shortCodeLength value using ReflectionTestUtils
        ReflectionTestUtils.setField(urlShortenerService, "shortCodeLength", 6);
    }

    @Test
    @DisplayName("Should shorten a new valid URL successfully")
    void shouldShortenValidUrlSuccessfully() {
        String longUrl = "https://www.google.com/search?q=junit+mockito+cucumber";
        String expectedShortCode = "abcDEF"; // Assuming Base62Encoder logic or random
        UrlMapping newMapping = new UrlMapping();
        newMapping.setLongUrl(longUrl);
        newMapping.setShortCode(expectedShortCode);
        newMapping.setCreatedAt(LocalDateTime.now());
        newMapping.setClicks(0);

        when(urlMappingRepository.findByLongUrl(longUrl)).thenReturn(Optional.empty());
        when(urlMappingRepository.count()).thenReturn(0L); // Simulate initial count for ID generation
        when(urlMappingRepository.existsByShortCode(anyString())).thenReturn(false); // No collision
        when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(newMapping);

        UrlMapping result = urlShortenerService.shortenUrl(longUrl, null, null);

        assertNotNull(result);
        assertEquals(expectedShortCode, result.getShortCode());
        assertEquals(longUrl, result.getLongUrl());
        verify(urlMappingRepository, times(1)).save(any(UrlMapping.class));
        verify(valueOperations, times(1)).set(expectedShortCode, longUrl); // Verify cache update
    }

    @Test
    @DisplayName("Should return existing short code if URL already exists")
    void shouldReturnExistingShortCodeIfUrlAlreadyExists() {
        String longUrl = "https://www.existing-site.com";
        String existingShortCode = "existS";
        UrlMapping existingMapping = new UrlMapping();
        existingMapping.setLongUrl(longUrl);
        existingMapping.setShortCode(existingShortCode);

        when(urlMappingRepository.findByLongUrl(longUrl)).thenReturn(Optional.of(existingMapping));

        UrlMapping result = urlShortenerService.shortenUrl(longUrl, null, null);

        assertNotNull(result);
        assertEquals(existingShortCode, result.getShortCode());
        assertEquals(longUrl, result.getLongUrl());
        verify(urlMappingRepository, never()).save(any(UrlMapping.class)); // Should not save new entry
        verify(valueOperations, never()).set(anyString(), anyString()); // Should not touch cache
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid long URL")
    void shouldThrowExceptionForInvalidLongUrl() {
        String invalidUrl = "invalid-url-format";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            urlShortenerService.shortenUrl(invalidUrl, null, null);
        });

        assertTrue(exception.getMessage().contains("Invalid URL format"));
        verify(urlMappingRepository, never()).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should use custom short code if provided and unique")
    void shouldUseCustomShortCodeIfUnique() {
        String longUrl = "https://www.example.org/custom";
        String customShortCode = "myCustom";
        UrlMapping newMapping = new UrlMapping();
        newMapping.setLongUrl(longUrl);
        newMapping.setShortCode(customShortCode);
        newMapping.setCreatedAt(LocalDateTime.now());

        when(urlMappingRepository.findByLongUrl(longUrl)).thenReturn(Optional.empty());
        when(urlMappingRepository.existsByShortCode(customShortCode)).thenReturn(false);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(newMapping);

        UrlMapping result = urlShortenerService.shortenUrl(longUrl, customShortCode, null);

        assertNotNull(result);
        assertEquals(customShortCode, result.getShortCode());
        verify(urlMappingRepository, times(1)).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should throw ShortCodeAlreadyExistsException if custom short code exists")
    void shouldThrowExceptionIfCustomShortCodeExists() {
        String longUrl = "https://www.example.org/another";
        String customShortCode = "existing";

        when(urlMappingRepository.findByLongUrl(longUrl)).thenReturn(Optional.empty());
        when(urlMappingRepository.existsByShortCode(customShortCode)).thenReturn(true);

        Exception exception = assertThrows(ShortCodeAlreadyExistsException.class, () -> {
            urlShortenerService.shortenUrl(longUrl, customShortCode, null);
        });

        assertTrue(exception.getMessage().contains("already exists"));
        verify(urlMappingRepository, never()).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should retrieve long URL and increment clicks")
    void shouldRetrieveLongUrlAndIncrementClicks() {
        String shortCode = "myCode";
        String longUrl = "https://www.destination.com";
        UrlMapping existingMapping = new UrlMapping();
        existingMapping.setShortCode(shortCode);
        existingMapping.setLongUrl(longUrl);
        existingMapping.setClicks(5); // Initial clicks
        existingMapping.setCreatedAt(LocalDateTime.now());

        when(urlMappingRepository.findById(shortCode)).thenReturn(Optional.of(existingMapping));
        // Mock the save operation after click increment
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String result = urlShortenerService.getLongUrl(shortCode);

        assertEquals(longUrl, result);
        assertEquals(6, existingMapping.getClicks()); // Verify clicks incremented
        verify(urlMappingRepository, times(1)).save(existingMapping); // Verify save was called with updated object
    }

    @Test
    @DisplayName("Should throw UrlNotFoundException if short code not found")
    void shouldThrowExceptionIfShortCodeNotFound() {
        String nonExistentShortCode = "noExist";

        when(urlMappingRepository.findById(nonExistentShortCode)).thenReturn(Optional.empty());

        Exception exception = assertThrows(UrlNotFoundException.class, () -> {
            urlShortenerService.getLongUrl(nonExistentShortCode);
        });

        assertTrue(exception.getMessage().contains("Short URL not found"));
        verify(urlMappingRepository, never()).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should throw UrlNotFoundException if URL is expired")
    void shouldThrowExceptionIfUrlIsExpired() {
        String shortCode = "expiredC";
        String longUrl = "https://www.expired.com";
        UrlMapping expiredMapping = new UrlMapping();
        expiredMapping.setShortCode(shortCode);
        expiredMapping.setLongUrl(longUrl);
        expiredMapping.setCreatedAt(LocalDateTime.now().minusDays(2));
        expiredMapping.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired yesterday

        when(urlMappingRepository.findById(shortCode)).thenReturn(Optional.of(expiredMapping));

        Exception exception = assertThrows(UrlNotFoundException.class, () -> {
            urlShortenerService.getLongUrl(shortCode);
        });

        assertTrue(exception.getMessage().contains("Short URL has expired"));
        verify(urlMappingRepository, never()).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should set expiration time if provided")
    void shouldSetExpirationTime() {
        String longUrl = "https://www.temp-link.com";
        Integer expirationMinutes = 10;
        String expectedShortCode = "tempLN";
        UrlMapping newMapping = new UrlMapping();
        newMapping.setLongUrl(longUrl);
        newMapping.setShortCode(expectedShortCode);
        newMapping.setCreatedAt(LocalDateTime.now());

        when(urlMappingRepository.findByLongUrl(longUrl)).thenReturn(Optional.empty());
        when(urlMappingRepository.count()).thenReturn(0L);
        when(urlMappingRepository.existsByShortCode(anyString())).thenReturn(false);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(newMapping);

        UrlMapping result = urlShortenerService.shortenUrl(longUrl, null, expirationMinutes);

        assertNotNull(result.getExpiresAt());
        assertTrue(result.getExpiresAt().isAfter(LocalDateTime.now()));
        // Verify that it's roughly 10 minutes from now (allowing for test execution time)
        assertTrue(result.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(11)));
        verify(urlMappingRepository, times(1)).save(any(UrlMapping.class));
    }
}
