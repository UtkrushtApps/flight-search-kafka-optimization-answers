package com.example.flightsearch.service;

import com.example.flightsearch.exception.FlightUpdateProcessingException;
import com.example.flightsearch.model.FlightUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory index of flights used by the search REST API.
 * <p>
 * This index is updated exclusively via Kafka flight update events and is
 * optimized for fast in-memory lookups.
 */
@Service
public class FlightSearchIndex {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchIndex.class);

    /**
     * Primary index: flightId -&gt; latest event for that flight.
     */
    private final Map<String, FlightUpdateEvent> flightsById = new ConcurrentHashMap<>();

    /**
     * Apply a single flight update event to the in-memory index.
     * <p>
     * This method is intentionally simple, but contains hooks for validating
     * the event and discarding stale or inconsistent updates.
     *
     * @param event the event to apply
     */
    public void applyUpdate(FlightUpdateEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.getFlightId() == null || event.getFlightId().isEmpty()) {
            throw new FlightUpdateProcessingException("flightId must be present in FlightUpdateEvent");
        }

        // Optional: basic domain validation.
        if (event.getDepartureTimeUtc() != null && event.getArrivalTimeUtc() != null
                && event.getArrivalTimeUtc().isBefore(event.getDepartureTimeUtc())) {
            throw new FlightUpdateProcessingException("arrivalTimeUtc cannot be before departureTimeUtc");
        }

        flightsById.compute(event.getFlightId(), (flightId, existing) -> {
            if (existing == null) {
                return event;
            }

            Instant existingUpdated = Optional.ofNullable(existing.getLastUpdatedUtc())
                    .orElse(Instant.EPOCH);
            Instant incomingUpdated = Optional.ofNullable(event.getLastUpdatedUtc())
                    .orElse(Instant.EPOCH);

            // Only accept newer updates; drop duplicates or out-of-order events.
            if (incomingUpdated.isBefore(existingUpdated)) {
                log.debug("Ignoring stale flight update for flightId={} existingLastUpdated={} incomingLastUpdated={}",
                        flightId, existingUpdated, incomingUpdated);
                return existing;
            }

            return event;
        });

        if (log.isDebugEnabled()) {
            log.debug("Applied flight update for flightId={} status={} price={}",
                    event.getFlightId(), event.getStatus(), event.getPrice());
        }
    }

    /**
     * Lookup a single flight by its identifier.
     */
    public FlightUpdateEvent findByFlightId(String flightId) {
        return flightsById.get(flightId);
    }

    /**
     * Return a snapshot view of all flights in the index.
     * <p>
     * Intended for diagnostics and is not optimized for very large datasets.
     */
    public Collection<FlightUpdateEvent> findAll() {
        return Collections.unmodifiableCollection(flightsById.values());
    }

    /**
     * Clear the index; primarily intended for testing.
     */
    public void clear() {
        flightsById.clear();
    }
}
