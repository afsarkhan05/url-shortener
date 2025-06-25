package com.afsar.url.shortener.service;

import com.afsar.url.shortener.exception.ShortCodeAlreadyExistsException;
import com.afsar.url.shortener.exception.UrlNotFoundException;
import com.afsar.url.shortener.model.UrlMapping;
import com.afsar.url.shortener.repository.UrlMappingRepository;
import com.afsar.url.shortener.util.Base62Encoder;
import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UrlShortenerService {

    private final UrlMappingRepository urlMappingRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final UrlValidator urlValidator;

    @Value("${url-shortener.short-code-length}")
    private int shortCodeLength;

    public UrlShortenerService(UrlMappingRepository urlMappingRepository, RedisTemplate<String, String> redisTemplate) {
        this.urlMappingRepository = urlMappingRepository;
        this.redisTemplate = redisTemplate;
        this.urlValidator = new UrlValidator(new String[]{"http", "https"}); // Allow http and https schemes
    }

    @Transactional
    @CacheEvict(value = "longUrl", key = "#result.shortCode", condition = "#result != null")
    public UrlMapping shortenUrl(String longUrl, String customShortCode, Integer expirationMinutes) {
        if (!urlValidator.isValid(longUrl)) {
            throw new IllegalArgumentException("Invalid URL format: " + longUrl);
        }

        // Check if the long URL already has a short code
        Optional<UrlMapping> existingMapping = urlMappingRepository.findByLongUrl(longUrl);
        if (existingMapping.isPresent()) {
            return existingMapping.get();
        }

        String shortCode;
        if (customShortCode != null && !customShortCode.isEmpty()) {
            if (urlMappingRepository.existsByShortCode(customShortCode)) {
                throw new ShortCodeAlreadyExistsException("Custom short code '" + customShortCode + "' already exists.");
            }
            shortCode = customShortCode;
        } else {
            // Generate a unique short code based on a sequential ID or random string
            // For true uniqueness and distribution in microservices, consider
            // a dedicated ID generation service (e.g., Snowflake, or a database sequence).
            // For this example, we'll use a simple approach:
            // 1. Try to generate from an auto-incrementing ID (simulated here)
            // 2. Fallback to random if auto-incrementing is not feasible/unique enough.

            // Simulate auto-incrementing ID by getting max ID and incrementing
            // In a real distributed system, this needs a robust solution like a distributed sequence or UUIDs.
            long nextId = (urlMappingRepository.count() + 1 + ThreadLocalRandom.current().nextInt(100)); // Add randomness to avoid predictable sequential IDs if exposed

            int retryCount = 0;
            do {
                shortCode = Base62Encoder.encode(nextId + retryCount);
                if (shortCode.length() > shortCodeLength) { // Ensure short code doesn't exceed desired length
                    shortCode = generateRandomShortCode(); // Fallback to random if base62 code gets too long
                }
                retryCount++;
            } while (urlMappingRepository.existsByShortCode(shortCode) && retryCount < 10); // Limit retries

            if (urlMappingRepository.existsByShortCode(shortCode)) {
                // If after retries, still a collision, generate a purely random one
                shortCode = generateRandomShortCode();
                while (urlMappingRepository.existsByShortCode(shortCode)) {
                    shortCode = generateRandomShortCode(); // Keep trying
                }
            }
        }

        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setShortCode(shortCode);
        urlMapping.setLongUrl(longUrl);
        if (expirationMinutes != null && expirationMinutes > 0) {
            urlMapping.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        }

        UrlMapping savedMapping = urlMappingRepository.save(urlMapping);
        redisTemplate.opsForValue().set(savedMapping.getShortCode(), savedMapping.getLongUrl()); // Cache the mapping
        return savedMapping;
    }

    @Cacheable(value = "longUrl", key = "#shortCode")
    @Transactional
    public String getLongUrl(String shortCode) {
        UrlMapping urlMapping = urlMappingRepository.findById(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        if (urlMapping.getExpiresAt() != null && urlMapping.getExpiresAt().isBefore(LocalDateTime.now())) {
            // URL has expired, consider deleting it (asynchronously or as part of a cleanup job)
            throw new UrlNotFoundException("Short URL has expired: " + shortCode);
        }

        urlMapping.setClicks(urlMapping.getClicks() + 1); // Increment click count
        urlMappingRepository.save(urlMapping); // Save updated clicks

        return urlMapping.getLongUrl();
    }

    // Helper for truly random short code generation (fallback)
    private String generateRandomShortCode() {
        String chars = Base62Encoder.BASE62_CHARS; // Use the same Base62 character set
        StringBuilder sb = new StringBuilder(shortCodeLength);
        for (int i = 0; i < shortCodeLength; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }
}
