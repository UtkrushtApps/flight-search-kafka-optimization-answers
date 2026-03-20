package com.example.flightsearch.exception;

/**
 * Domain-specific exception raised when a flight update event is syntactically
 * valid but cannot be applied to the in-memory search index due to business
 * rules or data issues.
 * <p>
 * This exception is configured as non-retryable in the Kafka consumer error
 * handler so that problematic messages are moved directly to the dead-letter
 * topic instead of blocking the entire consumer.
 */
public class FlightUpdateProcessingException extends RuntimeException {

    public FlightUpdateProcessingException(String message) {
        super(message);
    }

    public FlightUpdateProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
