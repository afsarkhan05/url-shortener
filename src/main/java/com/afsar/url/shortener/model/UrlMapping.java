package com.afsar.url.shortener.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "url_mappings")
@Data
@NoArgsConstructor
public class UrlMapping {

    @Id
    @Column(name = "short_code", length = 10, unique = true, nullable = false)
    private String shortCode;

    @NotBlank(message = "Long URL cannot be blank")
    @Column(name = "long_url", nullable = false, length = 2048) // Increased length for long URLs
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "clicks", nullable = false)
    private long clicks;

    // Optional: user_id if you have user management
    // @Column(name = "user_id")
    // private Long userId;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.clicks = 0;
    }
}
