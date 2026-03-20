package com.example.flightsearch.kafka;

import com.example.flightsearch.exception.FlightUpdateProcessingException;
import com.example.flightsearch.model.FlightUpdateEvent;
import com.example.flightsearch.service.FlightSearchIndex;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that keeps the in-memory flight search index in sync with the
 * latest flight update events.
 * <p>
 * This listener is designed with production concerns in mind:
 * <ul>
 *     <li>Manual acknowledgements so that offsets are committed only after
 *     successful index updates.</li>
 *     <li>Metrics for processed and failed messages and processing latency.</li>
 *     <li>Structured logging to aid debugging and operations.</li>
 * </ul>
 */
@Component
public class FlightUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(FlightUpdateListener.class);

    private final FlightSearchIndex flightSearchIndex;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Timer processingTimer;

    public FlightUpdateListener(FlightSearchIndex flightSearchIndex,
                                MeterRegistry meterRegistry) {
        this.flightSearchIndex = flightSearchIndex;
        this.processedCounter = meterRegistry.counter("flight_updates_processed_total");
        this.failedCounter = meterRegistry.counter("flight_updates_failed_total");
        this.processingTimer = meterRegistry.timer("flight_updates_processing_duration_seconds");
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.flightUpdatesTopic}",
            containerFactory = "flightUpdateKafkaListenerContainerFactory"
    )
    public void onFlightUpdate(ConsumerRecord<String, FlightUpdateEvent> record,
                               Acknowledgment acknowledgment) {

        processingTimer.record(() -> {
            try {
                FlightUpdateEvent event = record.value();

                if (event == null) {
                    throw new FlightUpdateProcessingException("Received null FlightUpdateEvent from Kafka");
                }

                if (log.isDebugEnabled()) {
                    log.debug("Received flight update from Kafka topic={} partition={} offset={} key={} flightId={} status={} price={}",
                            record.topic(), record.partition(), record.offset(), record.key(),
                            event.getFlightId(), event.getStatus(), event.getPrice());
                }

                // Apply update to in-memory index.
                flightSearchIndex.applyUpdate(event);
                acknowledgment.acknowledge();
                processedCounter.increment();

            } catch (Exception ex) {
                failedCounter.increment();

                // Log with enough context to debug but avoid logging entire payloads at warn level.
                log.warn("Failed to process flight update from topic={} partition={} offset={} key={} - {}",
                        record.topic(), record.partition(), record.offset(), record.key(), ex.getMessage(), ex);

                // Rethrow to let Spring Kafka's error handler (with DLT and retry
                // semantics) manage the failure.
                if (ex instanceof FlightUpdateProcessingException) {
                    throw (FlightUpdateProcessingException) ex;
                }
                throw new FlightUpdateProcessingException("Unexpected error while processing flight update", ex);
            }
        });
    }
}
