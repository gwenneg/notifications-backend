# Performance Guidelines

Rules for AI agents implementing or reviewing code in the notifications-backend repository.

## 1. Caching with Quarkus Cache (Caffeine)

### 1.1 Use `@CacheResult` for repetitive external lookups
This repo caches DB lookups (event types, templates, bundles), RBAC calls, and recipients-resolver results using `io.quarkus.cache.CacheResult`. When adding a new cacheable method, follow these conventions:
- The method **must be on an `@ApplicationScoped` CDI bean** (not `@Dependent`), and calls must go through the CDI proxy (no `this.method()` calls to cached methods).
- Define the TTL in the relevant module's `application.properties` using `quarkus.cache.caffeine.<cache-name>.expire-after-write`.
- Always set `quarkus.cache.caffeine.<cache-name>.metrics-enabled=true` so the cache is observable.
- Use short TTLs for volatile data (e.g., `PT1M` for recipients-resolver results) and longer TTLs for stable reference data (e.g., `PT15M` for bundles/apps).

### 1.2 Existing cache TTL conventions
| Cache | TTL | Module |
|---|---|---|
| `event-types-from-baet` / `event-types-from-fqn` | 5 min | engine |
| `recipients-resolver-results` | 1 min | engine |
| `recipients-users-provider-get-users` | 10 min | recipients-resolver |
| `find-recipients` | 10 min | recipients-resolver |
| `rbac-cache` | 2 min | backend |
| `kessel-oauth2-client-credentials` | 7 days | backend |
| `get-bundle-by-id` / `get-app-by-name` | 15 min | engine |

### 1.3 Provide cache invalidation
When a cache wraps credentials or tokens (see `OAuth2ClientCredentialsCache`), provide a `@CacheInvalidateAll` method so the cache can be cleared on auth failures.

### 1.4 Test with cache disabled
Engine tests set `quarkus.cache.enabled=false`. Ensure new cached methods work correctly in both cached and uncached modes.

## 2. Kafka Consumer and Producer Patterns

### 2.1 Consumer method signature
Kafka consumers use `@Incoming("channel-name")` with `@Blocking` annotation. They return `CompletionStage<Void>` and call `message.ack()`. Follow the pattern in `EventConsumer` and `ConnectorReceiver`.

### 2.2 Use `@ActivateRequestContext` on message processors
Methods that access JPA entities from Kafka consumer threads **must** be annotated with `@ActivateRequestContext` to ensure a CDI request context is active. See `EventConsumer.process()` and `ConnectorReceiver.processAsync()`.

### 2.3 Enable Snappy compression for large outgoing messages
Outgoing Kafka topics carrying potentially large payloads (e.g., rendered emails) must set `mp.messaging.outgoing.<channel>.compression.type=snappy`. Both `tocamel` and `highvolume` channels do this.

### 2.4 High-volume traffic separation
Route high-volume applications (currently `errata-notifications`) to a dedicated `highvolume` Kafka channel. Check `ConnectorSender.isEventFromHighVolumeApplication()` before adding new routing rules.

### 2.5 Large payload offloading
When Kafka message payload exceeds `mp.messaging.outgoing.tocamel.max.request.size` (default 10MB), the payload is stored in the database and a reference ID is sent instead. Follow the pattern in `ConnectorSender.send()`.

### 2.6 Pausable channels
Mark Kafka incoming channels that need runtime control as `pausable=true` in `application.properties`. Use `KafkaChannelManager` and Unleash feature flags to pause/resume channels per host.

### 2.7 Connector message filtering
In connector-common-v2, `MessageConsumer` filters messages by the `x-rh-notifications-connector` Kafka header. Ack and skip messages not matching the connector's supported headers -- never process them.

### 2.8 Virtual threads in connectors
Connector-v2 `MessageConsumer` uses `@RunOnVirtualThread` with `@Blocking("connector-thread-pool")`. The pool concurrency is configured via `smallrye.messaging.worker.connector-thread-pool.max-concurrency` (default 20 for drawer).

## 3. Concurrency and Thread Safety

### 3.1 EventConsumer thread pool
`EventConsumer` uses a custom `ThreadPoolExecutor` with a blocking `LinkedBlockingQueue` that overrides `offer()` to call `put()`, creating **back-pressure** from the thread pool to the Kafka consumer. Configurable via:
- `notifications.event-consumer.core-thread-pool-size` (default 10)
- `notifications.event-consumer.max-thread-pool-size` (default 10)
- `notifications.event-consumer.queue-capacity` (default 1)

Do not increase queue capacity without understanding the back-pressure implications.

### 3.2 Volatile fields for shared mutable state
Use `volatile` for fields written during initialization and read by multiple request threads. See `KesselCheckClient.grpcClient` and `grpcChannel`. Do not use `volatile` for immutable or CDI-injected fields.

### 3.3 ConcurrentHashMap for dynamic registrations
Use `ConcurrentHashMap` with `computeIfAbsent` for lazily-initialized, thread-safe registries (e.g., `FetchUsersFromExternalServices.rbacUsers` gauge map).

### 3.4 Pessimistic locking for atomic DB operations
Use `PESSIMISTIC_WRITE` (`SELECT FOR UPDATE`) when an operation must be atomic across pods/threads. See `EndpointRepository.lockEndpoint()` for the endpoint server-errors counter pattern. Always keep the locked transaction short.

## 4. gRPC Channel Management (Kessel)

### 4.1 Channel lifecycle
- Initialize in `@PostConstruct`, shut down in `@PreDestroy` with `awaitTermination`.
- On `UNAUTHENTICATED` errors, recreate the channel with fresh OAuth2 credentials.
- On `SHUTDOWN` state, recreate the channel (terminal state, cannot recover).
- When replacing a channel, call `shutdown()` on the old channel without waiting.

### 4.2 Timeouts and retries
- Set `withDeadlineAfter` on every gRPC call using a configurable timeout (`notifications.kessel.timeout-ms`).
- Use `@Retry(maxRetries = 3, delay = 100, retryOn = KesselTransientException.class)` for transient gRPC errors (UNAVAILABLE, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED, ABORTED).

### 4.3 Error classification
Distinguish transient errors (retry) from permanent errors (propagate immediately). Track all gRPC errors with Micrometer counters tagged by error code.

## 5. Database Query Optimization

### 5.1 Use JOIN FETCH for entity graphs
Always use `JOIN FETCH` in JPQL when you need associated entities to avoid N+1 queries. See `EventTypeRepository.getEventType()` which fetches application and bundle in one query.

### 5.2 Batch-load properties by type
`EndpointRepository.loadProperties()` groups endpoints by type and loads all properties per type in a single `IN` query. Follow this pattern instead of loading properties one-by-one.

### 5.3 Pagination for external service calls
Use offset-based pagination with configurable page size (`recipientsResolverConfig.getMaxResultsPerPage()`). Loop until the returned page is smaller than the max page size. See `FetchUsersFromExternalServices.getWithPagination()`.

### 5.4 Flyway migrations
Never run Flyway migrations in production (`%prod` profile). Migrations are the responsibility of `notifications-backend`. Only `%dev` and `%test` profiles auto-migrate.

## 6. Retry and Resilience Patterns

### 6.1 Failsafe retry policy
Use `dev.failsafe.RetryPolicy` with exponential backoff for external service calls. Configure via properties:
- `max-attempts` (default 3)
- `initial-backoff` / `max-backoff`
- Handle only `IOException` (or specific transient exceptions)
- Log on `onRetriesExceeded`, not on every retry

### 6.2 MicroProfile Fault Tolerance
Use `@Retry` from MicroProfile for CDI-proxied methods (e.g., `KesselCheckClient`). Specify `retryOn` with a custom transient exception class to avoid retrying permanent failures.

### 6.3 RBAC call resilience
RBAC calls in `ConsoleIdentityProvider` use Mutiny `.retry().withBackOff().atMost()` chaining. Retry only on `IOException` and `ConnectTimeoutException`.

## 7. Metrics and Monitoring

### 7.1 Counter initialization
Initialize `Counter` instances in `@PostConstruct` and store as fields when the counter has static tags. See `EventConsumer.init()`.

### 7.2 Dynamic tags
For counters/timers with dynamic tags (bundle, application, event-type), create them inline with `registry.counter(name, tagKey, tagValue, ...)`. Use `tags.getOrDefault(key, "")` -- Micrometer throws NPE on null tag values.

### 7.3 Timer.Sample for elapsed time
Use `Timer.start(registry)` at the beginning and `sample.stop(registry.timer(...))` at the end. Do not use `System.currentTimeMillis()` for metrics (only for non-metric timing).

### 7.4 Distribution summaries for payload sizes
Use `DistributionSummary` with `baseUnit("bytes")` for measuring payload sizes. See `ConnectorSender.recordMetrics()`.

### 7.5 Prometheus PushGateway
The aggregator job uses `PushGateway` for batch job metrics. Only push in non-dev/test environments.

## 8. Feature Flags (Unleash)

### 8.1 Toggle registration pattern
Register toggles in `@PostConstruct` via `ToggleRegistry.register()` with a default value. Check with `unleash.isEnabled(toggleName, context, fallback)`. Always provide an env-var fallback for non-Unleash environments.

### 8.2 Blacklisting via Unleash context
Endpoint and event-type blacklisting uses `UnleashContext` with custom properties (`endpointId`, `eventTypeId`). New feature flags that target specific entities should follow this pattern.

## 9. Reactive Patterns (Mutiny)

Mutiny (`Uni`/`Multi`) is used sparingly and only in the backend module for authentication flows. The engine and connectors use blocking/imperative style with `@Blocking`. Do not introduce Mutiny in modules that currently use imperative patterns.

## 10. Log-Level Guards

Use `Log.isDebugEnabled()` before constructing expensive debug log messages (e.g., joining user lists). See `FetchUsersFromExternalServices.getUsers()`. Prefer `Log.infof`/`Log.debugf` with format strings over string concatenation.
