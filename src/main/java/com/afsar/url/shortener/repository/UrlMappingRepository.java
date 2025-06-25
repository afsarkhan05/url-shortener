package com.afsar.url.shortener.repository;

import com.afsar.url.shortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, String> {
    Optional<UrlMapping> findByLongUrl(String longUrl);
    boolean existsByShortCode(String shortCode);
}
