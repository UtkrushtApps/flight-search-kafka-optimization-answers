# Solution Steps

1. Introduce a dedicated configuration properties class `KafkaTopicsProperties` under `com.example.flightsearch.config` annotated with `@ConfigurationProperties(prefix = "flightsearch.kafka")` to centralize topic names, partition count, replication factor, and consumer concurrency so they can be tuned per environment via `application.yml`.

2. Create a production-focused Kafka producer configuration class `KafkaProducerConfig` that wires a `ProducerFactory<String, FlightUpdateEvent>` and `KafkaTemplate<String, FlightUpdateEvent>` using Spring Kafka, enabling idempotence (`enable.idempotence=true`), `acks=all`, high retry count, bounded `max.in.flight.requests.per.connection`, and throughput optimizations like batch size, linger, and compression type (e.g. `lz4`).

3. Implement a Kafka consumer configuration class `KafkaConsumerConfig` that defines a `ConsumerFactory<String, FlightUpdateEvent>` using `ErrorHandlingDeserializer2` delegating to `StringDeserializer` for keys and `JsonDeserializer` for values, trusting the `com.example.flightsearch.*` package and disabling `auto-commit` to allow manual offset management.

4. In `KafkaConsumerConfig`, tune consumer properties for production workloads: set `max.poll.records`, `fetch.max.bytes`, `fetch.min.bytes`, `fetch.max.wait.ms`, `session.timeout.ms`, `heartbeat.interval.ms`, and `max.poll.interval.ms`, and set a stable `group.id` (e.g. `flight-search-indexer`) suited for scaling with consumer groups.

5. Still in `KafkaConsumerConfig`, configure a `DefaultErrorHandler` bean that uses a `DeadLetterPublishingRecoverer` wired to the `flightUpdateKafkaTemplate` and routes failed messages to a dedicated DLT topic from `KafkaTopicsProperties`, applying an `ExponentialBackOffWithMaxRetries` for transient errors and marking deserialization and business exceptions (like `FlightUpdateProcessingException`) as non-retryable so they go straight to the DLT.

6. Define a `ConcurrentKafkaListenerContainerFactory<String, FlightUpdateEvent>` bean named `flightUpdateKafkaListenerContainerFactory` that uses the custom consumer factory and error handler, sets the concurrency to `KafkaTopicsProperties.consumerConcurrency`, enables manual acknowledgment (`AckMode.MANUAL`), and, if available on the Spring Kafka version, enables Micrometer observation for built-in Kafka metrics.

7. Add a `KafkaTopicConfig` configuration class that exposes a `KafkaAdmin` bean (configured with `spring.kafka.bootstrap-servers`) and `NewTopic` beans for the main flight updates topic and its DLT using the partition and replication settings from `KafkaTopicsProperties`, so the service can automatically create or validate the required topics on startup.

8. Create a domain-specific exception `FlightUpdateProcessingException` in `com.example.flightsearch.exception` to signify business-level issues when applying an event to the in-memory index; this exception will be treated as non-retryable by the Kafka error handler to avoid endlessly retrying bad records.

9. Model the Kafka payload as a POJO `FlightUpdateEvent` in `com.example.flightsearch.model` with fields like `flightId`, `airlineCode`, `origin`, `destination`, `departureTimeUtc`, `arrivalTimeUtc`, `status`, `price`, and `lastUpdatedUtc`, including a no-args constructor, full-args constructor, getters/setters, and proper `equals`, `hashCode`, and `toString` implementations for safe JSON (de)serialization and logging.

10. Implement a thread-safe in-memory index service `FlightSearchIndex` in `com.example.flightsearch.service` using a `ConcurrentHashMap<String, FlightUpdateEvent>` keyed by `flightId`, with an `applyUpdate(FlightUpdateEvent event)` method that validates the event, rejects obviously invalid data (e.g., arrival before departure), and uses `compute` to only accept updates whose `lastUpdatedUtc` is newer than the currently stored version, ensuring idempotency and resilience to out-of-order messages.

11. Expose additional read-side methods in `FlightSearchIndex` such as `findByFlightId`, `findAll`, and `clear` so the existing REST controllers can perform fast lookups against the in-memory index while keeping mutation logic centralized and easily unit-testable.

12. Create a Kafka listener component `FlightUpdateListener` in `com.example.flightsearch.kafka` annotated with `@Component` that injects `FlightSearchIndex` and a `MeterRegistry`, defines counters for processed and failed messages and a timer for processing duration, and uses a `@KafkaListener` referencing the `flightUpdatesTopic` from `KafkaTopicsProperties` and the custom `flightUpdateKafkaListenerContainerFactory`.

13. Inside `FlightUpdateListener.onFlightUpdate`, wrap processing logic in `processingTimer.record(...)`, perform null and basic sanity checks on the `FlightUpdateEvent`, log key details at debug level, delegate to `flightSearchIndex.applyUpdate(event)`, manually acknowledge the record only after successful processing, and increment the processed counter; on exceptions, increment the failed counter, log a warning with topic/partition/offset/key context, and rethrow as (or wrap in) `FlightUpdateProcessingException` so the configured `DefaultErrorHandler` can apply retry and DLT logic.

14. Wire everything together by ensuring Spring Boot is configured to bind `KafkaTopicsProperties` (via `@EnableConfigurationProperties` in the Kafka config classes) and by setting appropriate values for `spring.kafka.bootstrap-servers`, `spring.kafka.consumer.group-id`, and the `flightsearch.kafka.*` properties in `application.yml`, then verify under load that consumers scale with concurrency and partitions, offsets advance without lag under normal throughput, retries and DLT behavior work as expected for bad messages, and Micrometer metrics/logs expose processing rate, failures, and latency.

