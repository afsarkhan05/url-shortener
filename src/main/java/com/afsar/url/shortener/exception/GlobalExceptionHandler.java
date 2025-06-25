package com.afsar.url.shortener.exception;

public class GlobalExceptionHandler extends RuntimeException {
    public UrlNotFoundException(String message) {
        super(message);
    }
    public ShortCodeAlreadyExistsException(String message) {
        super(message);
    }
}
