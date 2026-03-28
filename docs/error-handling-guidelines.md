# Error Handling Guidelines

Rules and conventions for implementing and reviewing error handling in the notifications-backend repository.

## 1. Exception Hierarchy

### Custom Exceptions in This Repo
- `DelayedException` (extends `RuntimeException`) -- Used by `DelayedThrower` to accumulate multiple exceptions from loop iterations and rethrow them all at the end. Individual exceptions are attached via `addSuppressed`.
- `ActionParsingException` (extends `RuntimeException`) -- Thrown when Kafka message payload cannot be parsed as an Action. Always provide a message; include the cause when wrapping another exception.
- `KesselTransientException` (extends `RuntimeException`) -- Marker for retryable gRPC failures from Kessel. Only wraps `StatusRuntimeException`. Used as `retryOn` target for `@Retry`.
- `TemplateNotFoundException` (extends `RuntimeException`) -- Thrown when no Qute template is found for a given integration type / bundle / app / event type combination.
- `FilterExtractionException` (checked `Exception`) -- Thrown when export filter parameters are invalid.
- `TransformationException` / `UnsupportedFormatException` (checked `Exception`) -- Used in the export pipeline for data transformation errors.
- `IllegalIdentityHeaderException` (checked `Exception`) -- Thrown during x-rh-identity header parsing.

### When to Create a New Custom Exception
- Only create one if callers need to distinguish it for retry logic, exception mapping, or conditional handling.
- Prefer standard JAX-RS exceptions (`BadRequestException`, `NotFoundException`, `WebApplicationException`) for REST endpoint validation errors.
- Extend `RuntimeException` for exceptions that propagate through framework boundaries (Kafka consumers, Camel processors). Use checked exceptions only in self-contained pipelines (exports).

## 2. REST Exception Mappers (backend module)

### Existing Mappers -- Do Not Duplicate
| Mapper | Catches | Returns |
|---|---|---|
| `JaxRsExceptionMapper` | `WebApplicationException` | Preserves status; maps `BadRequestException` and wrapped `JsonParseException` to 400 |
| `NotFoundExceptionMapper` | `NotFoundException` | Original status, `text/plain` body |
| `ConstraintViolationExceptionMapper` | `ConstraintViolationException` | 400 with JSON body (`title`, `description`, `violations[]`) |
| `JsonParseExceptionMapper` | `JsonParseException` | 400 (legacy, may be unused) |

### Rules
- Throw `BadRequestException` with a descriptive message for invalid input in REST handlers. The mapper preserves the message.
- Throw `NotFoundException` when a looked-up entity does not exist. Do NOT return `Response.status(NOT_FOUND)` from public API endpoints -- throw the exception so the mapper handles it consistently.
- For internal-only endpoints (`/internal/*`), returning `Response.status(NOT_FOUND).build()` directly is acceptable (existing pattern).
- Use `@Valid` on request body parameters; the `ConstraintViolationExceptionMapper` handles the rest.
- Never catch and swallow `WebApplicationException` in REST resource methods -- let it propagate to the mapper.

### REST Client Exception Mappers
- For `@RegisterRestClient` interfaces, use `@ClientExceptionMapper` (Quarkus style) or implement `ResponseExceptionMapper` and register it with `@RegisterProvider`.
- Pattern: convert the response status + body into a `WebApplicationException` with a formatted message including the status code and response body text. See `ExportServicePsk` and `BadRequestExceptionMapper`.

### recipients-resolver Module
- Has its own `WebApplicationExceptionMapper` with similar logic to the backend's `JaxRsExceptionMapper`. Keep them consistent if modifying either.

## 3. Retry Logic

### MicroProfile @Retry -- Conventions
- Standard pattern: `@Retry(maxRetries = 3)` for REST client calls to internal services (Sources, Export Service, Engine).
- For Kessel gRPC: `@Retry(maxRetries = 3, delay = 100, retryOn = KesselTransientException.class)` -- only retry on the marker exception, not all failures.
- For connector REST clients (v2): `@Retry(delay = 1, delayUnit = ChronoUnit.SECONDS, maxRetries = 2)` -- always add a comment clarifying total attempts (e.g., "1 initial + 2 retries = 3 attempts").
- Never use `@Retry` without specifying `maxRetries`. The default is unbounded.

### Mutiny Retry (ConsoleIdentityProvider)
- Used for RBAC calls: `.onFailure(IOException | ConnectTimeoutException).retry().withBackOff(initial, max).atMost(n)`.
- After retries exhausted, transform the failure into `AuthenticationFailedException`.
- This pattern is specific to the authentication path; do not use Mutiny retry elsewhere unless the call is already reactive.

### Camel Redelivery (connector-common, v1 connectors)
- Configured in `EngineToConnectorRouteBuilder` via `onException(Throwable.class)`.
- Two `onException` clauses: one with redelivery (when `RedeliveryPredicate.matches`), one without.
- Default `RedeliveryPredicate`: retries on `IOException`. HTTP connectors extend this to also retry on 5xx and 429.
- Config: `redelivery.max-attempts` (default 2), `redelivery.delay` (default 1000ms).
- After redelivery exhaustion, the `ExceptionProcessor` handles the failure.

### When to Add Retry
- Add retry ONLY for transient, idempotent failures (network timeouts, 5xx, gRPC UNAVAILABLE/DEADLINE_EXCEEDED).
- Never retry on 4xx (except 429), validation errors, or permission errors.
- Always set a bounded `maxRetries`.

## 4. Kafka Error Handling

### Kafka Reinjection (v1 Camel Connectors)
- When a connector fails to deliver to an external service, `ExceptionProcessor` decides: if `reinjectionCount < kafkaMaximumReinjections` (default 3), reinject the message back to the incoming Kafka topic; otherwise, report failure to the engine.
- Reinjection delay uses exponential backoff: 10s, 30s, 1min, then half of `max.poll.interval.ms`.
- The reinjection count is tracked in the Kafka header `x-rh-notifications-connector-reinjections-count`.
- Email connector sets `kafka.maximum-reinjections=0` (no reinjection).

### Kafka Consumer Error Handling (v2 Connectors, Engine)
- v2 `MessageConsumer`: catches all exceptions, delegates to `ExceptionHandler.processException()`, sends failure response to engine via `OutgoingMessageSender.sendFailure()`, then ACKs the message. Messages are NEVER nacked.
- Engine `EventConsumer`: catches all exceptions in a top-level try/catch, logs at INFO level with the payload, increments `input.processing.exception` counter. The message is always ACKed (`message.ack()` is called unconditionally).
- Engine `ConnectorReceiver`: uses `@Acknowledgment(POST_PROCESSING)` -- the message is ACKed after `processAsync` returns. Errors in processing are caught, logged, and counted but do not prevent ACK.
- No dead-letter-queue configuration exists; all Kafka failure strategies are handled in application code.

### Rules for Kafka Consumers
- Always ACK messages. Never let exceptions propagate past the `@Incoming` method unhandled -- this would trigger SmallRye's default failure strategy and potentially block the consumer.
- Increment a counter on failure for observability.
- Include `orgId` and `historyId` (or message ID) in error logs for traceability.

## 5. Camel Connector Exception Handling

### ExceptionProcessor (v1) / ExceptionHandler (v2)
- Both are `@DefaultBean @ApplicationScoped` -- override by creating a subclass in the connector module.
- `ExceptionProcessor` (v1): implements Camel `Processor`, sets `SUCCESSFUL=false` and `OUTCOME=exception message`, then decides between reinjection and reporting to engine.
- `ExceptionHandler` (v2): returns `HandledExceptionDetails` with outcome message and optional connector-specific details.

### HTTP Connector Exception Classification
- `HttpExceptionProcessor` / `HttpExceptionHandler` classify exceptions by type and set `HTTP_ERROR_TYPE`:
  - `HttpOperationFailedException` / `ClientWebApplicationException` -> classified by status code (3xx, 4xx, 5xx)
  - `ConnectTimeoutException`, `SocketTimeoutException`, `HttpHostConnectException`, `SSLHandshakeException`, `UnknownHostException`, `SSLException` -> each gets a specific error type
  - Anything else -> falls through to `logDefault`
- HTTP 429 is grouped with 5xx (retryable), NOT with 4xx.
- Log levels for HTTP errors are configurable: `serverErrorLogLevel` for 3xx/5xx/429, `clientErrorLogLevel` for 4xx.

### Rules for New Connectors
- Extend `ExceptionProcessor` (v1) or `ExceptionHandler` (v2) if the connector needs custom error classification.
- Always call `logDefault(t, exchange)` as the fallback for unrecognized exceptions.
- For HTTP-based connectors, extend `HttpExceptionProcessor` / `HttpExceptionHandler` rather than the base class.

## 6. gRPC Error Handling (Kessel)

### Status Code Classification
- Transient (retryable): `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESOURCE_EXHAUSTED`, `ABORTED` -> wrap in `KesselTransientException`, log at WARN.
- Auth failure: `UNAUTHENTICATED` -> also transient, but additionally triggers channel recreation with fresh OAuth2 credentials.
- Non-transient: `PERMISSION_DENIED`, `NOT_FOUND`, `INVALID_ARGUMENT`, etc. -> rethrow original `StatusRuntimeException`, log at ERROR.
- All gRPC errors increment `notifications.kessel.grpc.error` counter with `error_type` tag.

### Rules
- Never catch `StatusRuntimeException` and return a default value. Always rethrow (directly or wrapped).
- In REST-facing code that calls Kessel, catch `StatusRuntimeException` and convert to `WebApplicationException` with a descriptive message (see `RecipientsResolverResource`).

## 7. DelayedThrower Pattern

- Use `DelayedThrower.throwEventually(message, accumulator -> { ... })` when processing multiple items in a loop where one failure should NOT stop processing of the others.
- Inside the loop, catch exceptions and call `accumulator.add(e)`.
- After the loop, if any exceptions were accumulated, a single `DelayedException` is thrown with all failures as suppressed exceptions.
- Used in `EndpointProcessor.process()` to ensure all endpoint types are attempted even if some fail.
- Nested `DelayedException` instances are automatically flattened.

## 8. Logging Conventions

### Logger
- Use `io.quarkus.logging.Log` (static import) everywhere. This is a compile-time generated logger -- no need to declare a `Logger` field. There are 120+ files using this pattern.
- `org.jboss.logging.Logger` is used only in 2 connector files for level-parameterized logging (`Log.logf(level, ...)`). Use it only when the log level must be dynamic.

### Log Levels for Errors
- `Log.errorf(exception, formatString, args)` -- Non-transient, unexpected failures. Always include the exception as first argument.
- `Log.warnf(formatString, args)` -- Transient failures that will be retried, or degraded-but-recoverable situations.
- `Log.infof(exception, formatString, args)` -- Used in `EventConsumer` for payload processing failures (high volume, expected to happen).
- Never use `Log.error(exception)` without context (message string). Always include identifying information.

### Required Context in Error Logs
- Always include: `orgId`, `historyId` (or event ID), and the operation being performed.
- For connector errors, also include: `targetUrl`, `routeId`.
- For HTTP errors, also include: `statusCode`, `responseBody`.
- Format: `"Message sending failed [orgId=%s, historyId=%s, targetUrl=%s, statusCode=%d]"`.

## 9. Metrics for Errors

- Increment counters on all error paths. Existing counter naming conventions:
  - `input.rejected` -- unparseable or unknown event types
  - `input.processing.error` -- endpoint processing failures
  - `input.processing.exception` -- any exception during Kafka message handling
  - `camel.messages.error` -- connector return processing failures
  - `notifications.kessel.grpc.error` (with `error_type` tag) -- gRPC failures
- Use Micrometer `Counter` and `Timer` via injected `MeterRegistry`.

## 10. Anti-Patterns to Avoid

1. **Swallowing exceptions silently** -- Always log or count. Never write empty catch blocks.
2. **Logging and rethrowing** -- Pick one. Log at the handling boundary, not at every layer.
3. **Catching `Exception` too broadly** -- Prefer specific exception types when the handling differs.
4. **Returning null on error** -- Throw an appropriate exception instead. The exception mappers will convert it to a proper HTTP response.
5. **Using `e.printStackTrace()`** -- Use `Log.errorf(e, ...)` instead.
6. **Interrupting threads without restoring the flag** -- When catching `InterruptedException`, always call `Thread.currentThread().interrupt()` (see `KesselCheckClient.preDestroy`).
