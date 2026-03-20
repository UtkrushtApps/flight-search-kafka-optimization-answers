package com.example.flightsearch.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka topic administration for the flight search service.
 * <p>
 * Automatically creates (or validates) the main flight updates topic and its
 * dead-letter topic with a partitioning scheme suitable for horizontal scale.
 */
@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaTopicConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        log.info("Configured KafkaAdmin with bootstrap servers: {}", bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic flightUpdatesTopic(KafkaTopicsProperties kafkaTopicsProperties) {
        log.info("Ensuring Kafka topic '{}' exists with {} partitions and replication factor {}",
                kafkaTopicsProperties.getFlightUpdatesTopic(),
                kafkaTopicsProperties.getPartitions(),
                kafkaTopicsProperties.getReplicationFactor());

        return new NewTopic(
                kafkaTopicsProperties.getFlightUpdatesTopic(),
                kafkaTopicsProperties.getPartitions(),
                kafkaTopicsProperties.getReplicationFactor()
        );
    }

    @Bean
    public NewTopic flightUpdatesDltTopic(KafkaTopicsProperties kafkaTopicsProperties) {
        log.info("Ensuring Kafka DLT topic '{}' exists with {} partitions and replication factor {}",
                kafkaTopicsProperties.getFlightUpdatesDltTopic(),
                kafkaTopicsProperties.getPartitions(),
                kafkaTopicsProperties.getReplicationFactor());

        return new NewTopic(
                kafkaTopicsProperties.getFlightUpdatesDltTopic(),
                kafkaTopicsProperties.getPartitions(),
                kafkaTopicsProperties.getReplicationFactor()
        );
    }
}
