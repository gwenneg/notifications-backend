# Integration Guidelines

## Architecture Overview

This repo is a multi-module Quarkus application with an event-driven architecture. The primary flow is: **ingress Kafka topic -> engine -> connector Kafka topics -> connectors -> external services -> response Kafka topic -> engine**.

There are two connector frameworks coexisting: **v1 (Apache Camel-based)** in `connector-common` and **v2 (SmallRye Reactive Messaging-based)** in `connector-common-v2`. New connectors should use v2.

## Kafka Messaging

### Topic Naming Convention
- Ingress: `platform.notifications.ingress` (engine consumes events from producing applications)
- Engine-to-connectors: `platform.notifications.tocamel` (engine sends to connectors)
- Connectors-to-engine: `platform.notifications.fromcamel` (connectors report results back)
- High-volume: `platform.notifications.connector.email.high.volume` (dedicated topic for high-traffic email)

### Channel Constants
- Define channel names as `public static final String` constants (e.g., `INGRESS_CHANNEL = "ingress"`, `TOCAMEL_CHANNEL = "tocamel"`).
- Reference these constants in `@Incoming`, `@Channel`, and test overrides.
- Channel names in `application.properties` must match the annotation values exactly.

### SmallRye Reactive Messaging Patterns (v2 connectors)
- Use `@Incoming("incomingmessages")` for consuming from Kafka.
- Use `@Channel("outgoingmessages")` with `Emitter<String>` for producing to Kafka.
- Annotate consumers with `@Blocking("connector-thread-pool")` and `@RunOnVirtualThread`.
- Consumer methods return `CompletionStage<Void>` and must call `message.ack()`.
- Outgoing messages use structured Cloud Events mode: `mp.messaging.outgoing.outgoingmessages.cloud-events-mode=structured`.

### Kafka Configuration Rules
- Serialization: always `StringSerializer`/`StringDeserializer` for both key and value.
- Enable `snappy` compression for outgoing `tocamel` and `highvolume` topics (payloads can be large).
- Set `mp.messaging.outgoing.egress.merge=true` when multiple emitters share a topic.
- Use `pausable=true` for channels that support backpressure control.
- Group IDs use the pattern `notifications-connector-{name}` for connectors or `integrations` for the engine.

### Test Configuration
- Override connectors with `smallrye-in-memory` in `src/test/resources/application.properties`:
  ```
  mp.messaging.incoming.incomingmessages.connector=smallrye-in-memory
  mp.messaging.outgoing.outgoingmessages.connector=smallrye-in-memory
  ```
- Inject `InMemoryConnector` with `@Any` qualifier in tests.
- Use `InMemorySource<Message<JsonObject>>` to send and `InMemorySink<String>` to receive.

## Cloud Events

### Format
- Spec version: `1.0`.
- Engine-to-connector type: `com.redhat.console.notification.toCamel.{connector}` (e.g., `com.redhat.console.notification.toCamel.webhook`).
- Connector-to-engine type: `com.redhat.console.notifications.history`.
- Cloud Event ID = `historyId` (UUID), used to correlate the outgoing request with the return notification.
- Data content type: `application/json`.

### Building Outgoing Cloud Events (Engine)
- Attach three metadata objects to each `Message`: `OutgoingKafkaRecordMetadata` (with `x-rh-notifications-connector` header), `OutgoingCloudEventMetadata`, and `TracingMetadata`.
- The `x-rh-notifications-connector` Kafka header routes messages to the correct connector.

### Parsing Incoming Events (Engine)
- The engine tries to parse payloads as `Action` first, then as `ConsoleCloudEvent`.
- `EventWrapperAction` and `EventWrapperCloudEvent` are the two wrapper types.
- Cloud Events can be transformed to Actions via `CloudEventTransformerFactory`.

## Connector Architecture

### v2 Connectors (preferred for new work)
Located in `connector-common-v2`, `connector-common-http-v2`, and `connector-common-authentication-v2`.

**Key classes to extend/override (all use `@DefaultBean` + `@Priority`):**
1. `MessageHandler` -- override `handle(IncomingCloudEventMetadata<JsonObject>)` to implement connector logic. Return `HandledMessageDetails`.
2. `ExceptionHandler` -- override `process(Throwable, IncomingCloudEventMetadata)` to customize error handling. Return `HandledExceptionDetails`.
3. `OutgoingCloudEventBuilder` -- override `buildSuccess`/`buildFailure` to add custom metadata to responses.
4. `ConnectorConfig` -- extend to add connector-specific configuration. Use `@Priority(BASE_CONFIG_PRIORITY + 1)`.

**HTTP connectors** additionally depend on `connector-common-http-v2`:
- Use `HttpNotificationValidator` to parse and validate incoming payloads with Bean Validation.
- Use `HttpRestClient` (or define a custom `@RegisterRestClient`) for outgoing HTTP calls.
- Extend `HttpExceptionHandler` for HTTP-specific error classification (3xx/4xx/5xx, SSL, timeout, DNS).

**Message flow in v2:** `MessageConsumer.processMessage()` -> filters by `x-rh-notifications-connector` header -> calls `MessageHandler.handle()` -> `OutgoingMessageSender.sendSuccess/sendFailure()`.

### v1 Connectors (Camel-based, legacy)
Located in `connector-common`. Used by: Slack, Teams, Google Chat, PagerDuty, ServiceNow, Splunk.

**Key classes to extend:**
1. `EngineToConnectorRouteBuilder` -- override `configureRoutes()` to define the Camel route from `seda(ENGINE_TO_CONNECTOR)` to external service, ending with `to(direct(SUCCESS))`.
2. `CloudEventDataExtractor` -- override `extract(Exchange, JsonObject)` to extract data from Cloud Event payload into Camel exchange properties.
3. `ConnectorConfig` / `HttpConnectorConfig` -- extend for connector-specific settings.

**Camel route structure:**
- Kafka consumer -> `IncomingCloudEventFilter` (header check) -> `IncomingCloudEventProcessor` (extracts CE fields) -> SEDA queue -> connector route -> `ConnectorToEngineRouteBuilder` (sends result back).
- Error handling uses Camel's `onException` with configurable redelivery (default: 2 retries, 1s delay).
- Camel supports message reinjection back to Kafka with async delay for retry scenarios.

### Required Configuration Per Connector
In `application.properties`, every connector must set:
```
notifications.connector.name={name}
notifications.connector.supported-connector-headers={comma-separated-headers}
notifications.connector.kafka.incoming.topic=${mp.messaging.tocamel.topic}
notifications.connector.kafka.outgoing.topic=${mp.messaging.fromcamel.topic}
```

### Connector Header Routing
- The engine sets `x-rh-notifications-connector` Kafka header to the connector name.
- Both v1 and v2 connectors filter messages by checking this header against `supportedConnectorHeaders`.
- A connector can handle multiple header values (e.g., webhook handles both `ansible` and `webhook`).

## Authentication

### Sources API Integration
- Use `AuthenticationLoader.fetchAuthenticationData(orgId, authenticationData)` to retrieve secrets.
- Supports two auth types: `BEARER` and `SECRET_TOKEN` (via `AuthenticationType` enum).
- Secrets are fetched from the Sources API, with a transition from PSK to OIDC authentication controlled by the `sources-oidc-auth` Unleash toggle.
- REST clients: `SourcesPskClient` (configKey `sources`) and `SourcesOidcClient` (configKey `sources-oidc`).

### Webhook Authentication Pattern
```java
Optional<AuthenticationResult> auth = authenticationLoader.fetchAuthenticationData(orgId, notification.getAuthentication());
if (auth.isPresent() && BEARER == auth.get().authenticationType) {
    response = client.postWithBearer("Bearer " + auth.get().password, url, body);
}
```

## Event Deduplication

### Kafka-Level (deprecated)
- `KafkaMessageDeduplicator` validates the `rh-message-id` Kafka header (must be UUID v4).
- Being replaced by the `id` field in the Action/CloudEvent payload.

### Application-Level
- `EventDeduplicator.isNew(Event)` uses DB-backed deduplication with `event_deduplication` table.
- Deduplication is configurable per bundle/application via `EventDeduplicationConfig` implementations.
- Uses `INSERT ... ON CONFLICT DO NOTHING` for atomic duplicate detection.
- Events without a deduplication key are always treated as new.

## REST Client Patterns
- All external service clients use `@RegisterRestClient(configKey = "...")`.
- Configure URL, timeouts, and TLS in `application.properties` under `quarkus.rest-client.{configKey}.*`.
- Use `@Retry` from MicroProfile Fault Tolerance for retries (e.g., webhook: 1s delay, 2 retries).
- Dynamic URLs use `@Url String url` parameter with `quarkus-rest-client-reactive`.

## Metrics and Observability
- Use Micrometer (`MeterRegistry`) for all metrics -- never raw Prometheus.
- Name counters/timers with dot-separated names (e.g., `input.consumed`, `camel.messages.processed`).
- Tag metrics with `bundle`, `application`, `event-type`, and `connector` where applicable.
- Initialize counters in `@PostConstruct` methods for counters used in hot paths.
- Use `Timer.Sample` pattern: `Timer.start(registry)` at beginning, `sample.stop(registry.timer(...))` at end.
- Track payload sizes with `DistributionSummary`.

## Feature Flags
- Feature toggles use Unleash via `ToggleRegistry.register(toggleName, defaultValue)`.
- Build `UnleashContext` with `UnleashContextBuilder.buildUnleashContextWithOrgId(orgId)` for org-scoped toggles.
- Config classes expose toggle state via methods like `isFeatureEnabled(String orgId)`.

## High Volume Traffic
- Events from `errata-notifications` application are routed to a dedicated `highvolume` Kafka topic.
- Only the email connector (`email_subscription`) is compatible with the high-volume topic.
- Controlled by `engineConfig.isOutgoingKafkaHighVolumeTopicEnabled()`.
- Large email payloads exceeding `kafkaToCamelMaximumRequestSize` are stored in the database (`PayloadDetails`) and referenced by ID in the Kafka message.

## Testing Conventions
- **v2 connector tests**: Use `@QuarkusTest` and extend `BaseConnectorIntegrationTest` (or `BaseHttpConnectorIntegrationTest` for HTTP connectors).
- **v1 Camel connector tests**: Extend `ConnectorRoutesTest` (which extends `CamelQuarkusTestSupport`). Do not use `@QuarkusTest`.
- **Backend/engine integration tests**: Use `@QuarkusTest` with `@QuarkusTestResource(TestLifecycleManager.class)`.
- Use WireMock (`MockServerLifecycleManager`) for external service mocking.
- Use `MicrometerAssertionHelper` for verifying metric values in tests.
