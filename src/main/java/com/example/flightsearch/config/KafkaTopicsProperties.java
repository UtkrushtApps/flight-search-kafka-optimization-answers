package com.example.flightsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralized configuration for Kafka topics used by the flight search service.
 * <p>
 * These properties should be configured in {@code application.yml} using the
 * prefix {@code flightsearch.kafka}.
 *
 * <pre>
 * flightsearch:
 *   kafka:
 *     topics:
 *       flight-updates: flight-updates
 *       flight-updates-dlt: flight-updates-dlt
 *     partitions: 12
 *     replication-factor: 3
 *     consumer-concurrency: 6
 * </pre>
 */
@ConfigurationProperties(prefix = "flightsearch.kafka")
public class KafkaTopicsProperties {

    /**
     * Main topic for flight update events that keep the search index in sync.
     */
    private String flightUpdatesTopic = "flight-updates";

    /**
     * Dead-letter topic for flight update events that cannot be processed
     * successfully even after retries.
     */
    private String flightUpdatesDltTopic = "flight-updates-dlt";

    /**
     * Number of partitions for the flight update topics.
     * <p>
     * This should be chosen based on expected maximum throughput and the
     * number of consumers you plan to scale out to.
     */
    private int partitions = 12;

    /**
     * Replication factor for topics.
     */
    private short replicationFactor = 3;

    /**
     * Default concurrency for the flight update consumer container.
     * <p>
     * Typically this should be less than or equal to the number of partitions
     * for the corresponding topic.
     */
    private int consumerConcurrency = 6;

    public String getFlightUpdatesTopic() {
        return flightUpdatesTopic;
    }

    public void setFlightUpdatesTopic(String flightUpdatesTopic) {
        this.flightUpdatesTopic = flightUpdatesTopic;
    }

    public String getFlightUpdatesDltTopic() {
        return flightUpdatesDltTopic;
    }

    public void setFlightUpdatesDltTopic(String flightUpdatesDltTopic) {
        this.flightUpdatesDltTopic = flightUpdatesDltTopic;
    }

    public int getPartitions() {
        return partitions;
    }

    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }

    public short getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(short replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    public int getConsumerConcurrency() {
        return consumerConcurrency;
    }

    public void setConsumerConcurrency(int consumerConcurrency) {
        this.consumerConcurrency = consumerConcurrency;
    }
}
