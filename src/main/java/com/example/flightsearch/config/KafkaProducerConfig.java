package com.example.flightsearch.config;

import com.example.flightsearch.model.FlightUpdateEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Production-grade Kafka producer configuration for flight update events.
 * <p>
 * Focus areas:
 * <ul>
 *     <li>Idempotent producer for exactly-once semantics at the Kafka level.</li>
 *     <li>Safe retries with bounded in-flight requests.</li>
 *     <li>Batching and compression tuned for higher throughput.</li>
 * </ul>
 */
@Configuration
public class KafkaProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, FlightUpdateEvent> flightUpdateProducerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability / correctness
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Retries are effectively unlimited when idempotence is enabled; still
        // set a large but finite number for safety.
        config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        // Ensure ordering is preserved even with retries.
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Throughput optimizations
        // 32KB batch size is a common sweet spot; tune based on benchmarks.
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024);
        // Small linger to allow more batching while keeping latency low.
        config.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        // Compression significantly reduces network and disk usage.
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        // Observability / safety
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "flight-search-producer");

        log.info("Configured Kafka ProducerFactory for FlightUpdateEvent with bootstrap servers: {}", bootstrapServers);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, FlightUpdateEvent> flightUpdateKafkaTemplate(
            ProducerFactory<String, FlightUpdateEvent> flightUpdateProducerFactory) {

        KafkaTemplate<String, FlightUpdateEvent> template =
                new KafkaTemplate<>(flightUpdateProducerFactory);

        // Enable Micrometer observation if available (Spring Kafka 3+)
        try {
            template.setObservationEnabled(true);
        } catch (NoSuchMethodError ignored) {
            // Running on older Spring Kafka; observation not available.
        }

        return template;
    }
}
