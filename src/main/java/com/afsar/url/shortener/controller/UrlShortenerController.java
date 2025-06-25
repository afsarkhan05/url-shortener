package com.afsar.url.shortener.controller;

import com.afsar.url.shortener.dto.ShortenRequest;
import com.afsar.url.shortener.exception.ShortCodeAlreadyExistsException;
import com.afsar.url.shortener.exception.UrlNotFoundException;
import com.afsar.urlshortener.model.UrlMapping;
import com.afsar.url.shortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.net.URI;

@RestController
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;

    @Value("${url-shortener.base-url}")
    private String baseUrl;

    public UrlShortenerController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @PostMapping("/shorten")
    public ResponseEntity<?> shortenUrl(@Valid @RequestBody ShortenRequest request) {
        try {
            UrlMapping urlMapping = urlShortenerService.shortenUrl(
                    request.getLongUrl(),
                    request.getCustomShortCode(),
                    request.getExpirationMinutes()
            );
            String shortUrl = baseUrl + urlMapping.getShortCode();
            return ResponseEntity.status(HttpStatus.CREATED).body(shortUrl);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ShortCodeAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @GetMapping("/{shortCode}")
    public void redirectToLongUrl(@PathVariable String shortCode, HttpServletResponse response) throws IOException {
        try {
            String longUrl = urlShortenerService.getLongUrl(shortCode);
            response.sendRedirect(longUrl);
        } catch (UrlNotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error redirecting: " + e.getMessage());
        }
    }
}
