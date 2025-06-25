package com.afsar.url.shortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShortenRequest {
    @NotBlank(message = "Long URL is required")
    private String longUrl;
    private String customShortCode;
    private Integer expirationMinutes;
}
