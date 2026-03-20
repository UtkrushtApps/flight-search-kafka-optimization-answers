package com.example.flightsearch.config;

import com.example.flightsearch.config.KafkaTopicsProperties;
import com.example.flightsearch.exception.FlightUpdateProcessingException;
import com.example.flightsearch.model.FlightUpdateEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer2;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Production-focused Kafka consumer configuration for flight update events.
 * <p>
 * Key improvements:
 * <ul>
 *     <li>Dedicated consumer factory and listener container factory for
 *     {@link FlightUpdateEvent}.</li>
 *     <li>Explicit error handling with retries and dead-letter topic.</li>
 *     <li>Manual acks so offsets are only committed after successful
 *     index updates.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:flight-search-indexer}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, FlightUpdateEvent> flightUpdateConsumerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer2.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer2.class);

        // Configure ErrorHandlingDeserializer2 to delegate to real deserializers
        props.put(ErrorHandlingDeserializer2.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer2.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.flightsearch.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, FlightUpdateEvent.class.getName());

        // Disable auto-commit; we commit manually after successful processing.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Start from latest by default, assuming this is an indexer that can be
        // replayed from scratch as needed; tune per environment.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        // Tune fetch/poll parameters for higher throughput.
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 5 * 1024 * 1024); // 5MB
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1 * 1024);         // 1KB
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // Session / heartbeat tuning
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);

        // Allow enough time to process large batches.
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, (int) Duration.ofMinutes(5).toMillis());

        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "flight-search-consumer");

        log.info("Configured Kafka ConsumerFactory for FlightUpdateEvent with bootstrap servers: {} and groupId: {}",
                bootstrapServers, groupId);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public DefaultErrorHandler flightUpdateErrorHandler(
            KafkaTemplate<String, FlightUpdateEvent> flightUpdateKafkaTemplate,
            KafkaTopicsProperties kafkaTopicsProperties) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                flightUpdateKafkaTemplate,
                (record, exception) -> {
                    // Route all failures to the dedicated DLT, preserving the original partition.
                    return new TopicPartition(kafkaTopicsProperties.getFlightUpdatesDltTopic(), record.partition());
                });

        // Exponential backoff with max retries; tune as needed.
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Do NOT retry on deserialization errors or business validation issues.
        errorHandler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                IllegalArgumentException.class,
                FlightUpdateProcessingException.class
        );

        // Optional: log each failure with enough context.
        errorHandler.setLogLevel(org.apache.commons.logging.LogFactory.getLog(getClass()).isDebugEnabled()
                ? org.apache.commons.logging.LogFactory.getLog(getClass()).getLevel()
                : org.apache.commons.logging.LogFactory.getLog(getClass()).getLevel());

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FlightUpdateEvent>
    flightUpdateKafkaListenerContainerFactory(
            ConsumerFactory<String, FlightUpdateEvent> flightUpdateConsumerFactory,
            DefaultErrorHandler flightUpdateErrorHandler,
            KafkaTopicsProperties kafkaTopicsProperties) {

        ConcurrentKafkaListenerContainerFactory<String, FlightUpdateEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(flightUpdateConsumerFactory);

        // Concurrency controls how many threads (and thus partitions) this
        // consumer group can process in parallel.
        factory.setConcurrency(kafkaTopicsProperties.getConsumerConcurrency());

        // Manual ack so we commit offsets only after we've updated the
        // in-memory index successfully.
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL
        );

        // Attach the error handler with retries + DLT.
        factory.setCommonErrorHandler(flightUpdateErrorHandler);

        // Enable Micrometer observation if available.
        try {
            factory.getContainerProperties().setObservationEnabled(true);
        } catch (NoSuchMethodError ignored) {
            // Running against older Spring Kafka; ignore.
        }

        return factory;
    }
}
