package com.example.flightsearch.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable representation of a flight update event consumed from Kafka.
 * <p>
 * This event is designed to be serializable with Spring Kafka's
 * {@link org.springframework.kafka.support.serializer.JsonSerializer} and
 * {@link org.springframework.kafka.support.serializer.JsonDeserializer}.
 */
public class FlightUpdateEvent {

    private String flightId;
    private String airlineCode;
    private String origin;
    private String destination;
    private Instant departureTimeUtc;
    private Instant arrivalTimeUtc;
    private String status;
    private BigDecimal price;
    private Instant lastUpdatedUtc;

    /**
     * No-args constructor required for JSON deserialization.
     */
    public FlightUpdateEvent() {
    }

    public FlightUpdateEvent(String flightId,
                             String airlineCode,
                             String origin,
                             String destination,
                             Instant departureTimeUtc,
                             Instant arrivalTimeUtc,
                             String status,
                             BigDecimal price,
                             Instant lastUpdatedUtc) {
        this.flightId = flightId;
        this.airlineCode = airlineCode;
        this.origin = origin;
        this.destination = destination;
        this.departureTimeUtc = departureTimeUtc;
        this.arrivalTimeUtc = arrivalTimeUtc;
        this.status = status;
        this.price = price;
        this.lastUpdatedUtc = lastUpdatedUtc;
    }

    public String getFlightId() {
        return flightId;
    }

    public void setFlightId(String flightId) {
        this.flightId = flightId;
    }

    public String getAirlineCode() {
        return airlineCode;
    }

    public void setAirlineCode(String airlineCode) {
        this.airlineCode = airlineCode;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Instant getDepartureTimeUtc() {
        return departureTimeUtc;
    }

    public void setDepartureTimeUtc(Instant departureTimeUtc) {
        this.departureTimeUtc = departureTimeUtc;
    }

    public Instant getArrivalTimeUtc() {
        return arrivalTimeUtc;
    }

    public void setArrivalTimeUtc(Instant arrivalTimeUtc) {
        this.arrivalTimeUtc = arrivalTimeUtc;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Instant getLastUpdatedUtc() {
        return lastUpdatedUtc;
    }

    public void setLastUpdatedUtc(Instant lastUpdatedUtc) {
        this.lastUpdatedUtc = lastUpdatedUtc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlightUpdateEvent)) return false;
        FlightUpdateEvent that = (FlightUpdateEvent) o;
        return Objects.equals(flightId, that.flightId) &&
                Objects.equals(airlineCode, that.airlineCode) &&
                Objects.equals(origin, that.origin) &&
                Objects.equals(destination, that.destination) &&
                Objects.equals(departureTimeUtc, that.departureTimeUtc) &&
                Objects.equals(arrivalTimeUtc, that.arrivalTimeUtc) &&
                Objects.equals(status, that.status) &&
                Objects.equals(price, that.price) &&
                Objects.equals(lastUpdatedUtc, that.lastUpdatedUtc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flightId, airlineCode, origin, destination, departureTimeUtc,
                arrivalTimeUtc, status, price, lastUpdatedUtc);
    }

    @Override
    public String toString() {
        return "FlightUpdateEvent{" +
                "flightId='" + flightId + '\'' +
                ", airlineCode='" + airlineCode + '\'' +
                ", origin='" + origin + '\'' +
                ", destination='" + destination + '\'' +
                ", departureTimeUtc=" + departureTimeUtc +
                ", arrivalTimeUtc=" + arrivalTimeUtc +
                ", status='" + status + '\'' +
                ", price=" + price +
                ", lastUpdatedUtc=" + lastUpdatedUtc +
                '}';
    }
}
