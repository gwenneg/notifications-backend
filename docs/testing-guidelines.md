# Testing Guidelines

## 1. Framework and Dependencies

- **Java 21**, **Quarkus 3.x**, **JUnit 5** (Jupiter). JUnit 4 imports are banned by checkstyle (`org.junit.Test`, `org.junit.Assert`, etc.).
- Key test dependencies: `quarkus-junit5`, `rest-assured`, WireMock (`3.13.x`), Testcontainers (`2.0.x`), Mockito (via Quarkus), Awaitility, SmallRye InMemoryConnector.
- Camel-based connectors (v1) use `CamelQuarkusTestSupport` as base class instead of `@QuarkusTest`.

## 2. Test Class Structure

### Integration Tests (most common pattern)
```java
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class MyResourceTest extends DbIsolatedTest {
    // ...
}
```

- Always annotate with `@QuarkusTest` for integration tests.
- Always add `@QuarkusTestResource(TestLifecycleManager.class)` -- each module has its own `TestLifecycleManager`.
- For tests touching the database (backend, engine, aggregator), extend `DbIsolatedTest` to get automatic before/after cleanup.

### Unit Tests (no Quarkus context)
```java
class BaseTransformerTest {
    private final BaseTransformer baseTransformer = new BaseTransformer();
}
```
- Pure unit tests do NOT use `@QuarkusTest`. Instantiate the class under test directly.
- Use `package-private` visibility for test classes and methods (no `public` modifier). Checkstyle enforces `RedundantModifier`.

## 3. Test Naming Conventions

- Test classes: `<ClassUnderTest>Test.java` (e.g., `EmailActorsResolverTest`, `ConnectorSenderTest`).
- Integration tests for routes/resources: `<ResourceName>ResourceTest.java` or `<Feature>IntegrationTest.java`.
- Test methods: descriptive camelCase, often prefixed with `test` (e.g., `testDefaultEmailSenderHCC`, `testOpenshiftClusterManagerStageEmailSender`).
- No `@author` javadoc tags (banned by checkstyle).

## 4. TestLifecycleManager (QuarkusTestResourceLifecycleManager)

Each module has its own `TestLifecycleManager` implementing `QuarkusTestResourceLifecycleManager`:

| Module | What it starts |
|---|---|
| `backend` | PostgreSQL (Testcontainers) + WireMock |
| `engine` | PostgreSQL (Testcontainers) + WireMock + InMemoryConnector cleanup |
| `aggregator` | PostgreSQL (Testcontainers) + WireMock |
| `connector-common` | WireMock only (no database) |
| `connector-common-v2` | WireMock only (no database) |
| `connector-email`, `connector-drawer` | Module-specific WireMock setup |

- PostgreSQL version is pinned via `TestConstants.POSTGRES_MAJOR_VERSION` (currently `"16"`).
- The `pgcrypto` extension is installed during test setup.
- WireMock is managed via `MockServerLifecycleManager` in the `common` test module.

## 5. Database Test Isolation

- **Extend `DbIsolatedTest`** for any test that reads/writes database records. It injects `DbCleaner` and calls `clean()` in both `@BeforeEach` and `@AfterEach`.
- `DbCleaner` deletes all entity records in dependency order, then re-creates a default Bundle ("rhel"), Application ("policies"), and EventType ("policy-triggered").
- Do NOT manually truncate tables or rely on test ordering.

## 6. Test Data Setup

- **`ResourceHelpers`**: an `@ApplicationScoped` CDI bean (in `backend/src/test`) providing transactional methods to create Bundles, Applications, EventTypes, Endpoints, Events, etc. Inject it with `@Inject ResourceHelpers`.
- The `common` module has an abstract `ResourceHelpers` base class with shared entity creation methods (`createBundle`, `createApp`, `createEventType`, `createEvent`).
- **`TestHelpers`**: static utility methods for encoding `x-rh-identity` headers (User, ServiceAccount, Turnpike/Associate). Each module (backend, engine) has its own version.
- **`CrudTestHelpers`**: static methods in `backend` for CRUD operations via REST-Assured against internal API endpoints.
- Use `TestConstants.DEFAULT_ACCOUNT_ID`, `DEFAULT_ORG_ID`, `DEFAULT_USER` for standard test identities.

## 7. Identity Headers for REST Tests

```java
String identity = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
Header header = TestHelpers.createRHIdentityHeader(identity);
```

- For internal/Turnpike endpoints: `TestHelpers.createTurnpikeIdentityHeader("user@example.com", "role1")`.
- For service accounts: `TestHelpers.encodeRHServiceAccountIdentityInfo(orgId, username, uuid)`.

## 8. WireMock Usage

- `MockServerLifecycleManager` (in `common/src/test`) wraps a `WireMockServer` with dynamic HTTP and HTTPS ports.
- Access the server via `MockServerLifecycleManager.getClient()` to stub endpoints.
- `MockServerConfig` in `backend` provides helper methods for common RBAC stubs:
  ```java
  MockServerConfig.addMockRbacAccess(xRhIdentity, RbacAccess.FULL_ACCESS);
  ```
- RBAC mock payloads are stored as JSON files in `backend/src/test/resources/rbac-examples/`.
- For OIDC mocking, use `OidcServerMockResource` (implements `QuarkusTestResourceLifecycleManager`) which stubs discovery and token endpoints.
- `SourcesServerMockResource` exists for mocking Sources API. Combine multiple resources:
  ```java
  @QuarkusTestResource(OidcServerMockResource.class)
  @QuarkusTestResource(SourcesServerMockResource.class)
  ```

## 9. Mocking Patterns

### `@InjectMock` (Quarkus Mockito)
- Replaces a CDI bean entirely with a Mockito mock for the test class. Use for external service clients:
  ```java
  @InjectMock
  @RestClient
  SourcesPskClient sourcesClient;
  ```

### `@InjectSpy` (Quarkus Mockito)
- Wraps a real CDI bean with a Mockito spy (partial mock). Use when you need the real behavior but want to verify calls or stub specific methods:
  ```java
  @InjectSpy
  DailyEmailAggregationJob dailyEmailAggregationJob;
  ```

### Kessel Authorization Mocking
- Use `KesselTestHelper` (`@ApplicationScoped`) to build Kessel check requests/responses:
  ```java
  @Inject KesselTestHelper kesselTestHelper;
  @InjectMock KesselCheckClient kesselCheckClient;
  // then: when(kesselCheckClient.check(...)).thenReturn(...)
  ```

## 10. Kafka/Messaging in Tests

- Use SmallRye `InMemoryConnector` to replace Kafka in tests. Configure in `src/test/resources/application.properties`:
  ```properties
  mp.messaging.outgoing.tocamel.connector=smallrye-in-memory
  mp.messaging.incoming.fromcamel.connector=smallrye-in-memory
  ```
- Inject and use:
  ```java
  @Inject @Any InMemoryConnector connector;
  InMemorySink<String> sink = connector.sink("channelName");
  InMemorySource<Message<JsonObject>> source = connector.source("channelName");
  ```
- Call `sink.clear()` in `@BeforeEach` and `InMemoryConnector.clear()` on stop.

## 11. Connector Tests (v1 Camel-based)

- Extend `ConnectorRoutesTest` (which extends `CamelQuarkusTestSupport`).
- Override abstract methods: `getMockEndpointPattern()`, `getMockEndpointUri()`, `buildIncomingPayload()`, `checkOutgoingPayload()`.
- Uses Camel `AdviceWith` and `MockEndpoint` for route testing.

## 12. Connector Tests (v2)

- Extend `BaseConnectorIntegrationTest` (annotated with `@QuarkusTest`).
- Override: `buildIncomingPayload(String targetUrl)`, `getConnectorSpecificTargetUrl()`.
- Uses `InMemoryConnector` for messaging and WireMock for HTTP targets.
- Uses `Awaitility` for async assertions on outgoing messages.

## 13. Metrics Testing

- Use `MicrometerAssertionHelper` (`@ApplicationScoped` in `common/src/test`):
  ```java
  @Inject MicrometerAssertionHelper micrometerAssertionHelper;

  @BeforeEach
  void setup() {
      micrometerAssertionHelper.saveCounterValuesBeforeTest("counter.name");
  }

  // In test:
  micrometerAssertionHelper.assertCounterIncrement("counter.name", 1.0);
  micrometerAssertionHelper.awaitAndAssertCounterIncrement("counter.name", 1.0); // async
  ```
- Do NOT call `MeterRegistry.clear()` between tests -- it breaks `@ApplicationScoped` bean references.

## 14. REST API Testing

- Use REST-Assured (statically imported `given()`).
- Set identity headers via `TestHelpers.createRHIdentityHeader()`.
- Use API path constants from `TestConstants` (`API_INTEGRATIONS_V_1_0`, `API_NOTIFICATIONS_V_1_0`, etc.) and `Constants.API_INTERNAL`.
- Always assert response status and content type.

## 15. Test Profiles

- `ProdTestProfile` activates the `prod` Quarkus config profile. Apply with `@TestProfile(ProdTestProfile.class)`.
- Custom profiles can be created as inner classes implementing `QuarkusTestProfile` for test-specific configuration overrides.

## 16. Coverage and Quality

- **JaCoCo** (`0.8.x`) via `quarkus-jacoco` extension. Reports go to `target/jacoco-report/jacoco.xml`.
- **SonarQube** reads from `sonar.coverage.jacoco.xmlReportPaths`.
- **Checkstyle** rules enforced at build time:
  - No tabs, no trailing whitespace, newline at end of file.
  - No star imports (except static).
  - No JUnit 4 imports.
  - No `@author` tags.
  - Braces required, EOL style.
  - 4-space indentation.

## 17. Running Tests

```bash
# Run all tests for a specific module
./mvnw test -pl backend

# Run a single test class
./mvnw test -pl backend -Dtest=EndpointResourceTest

# Run all tests
./mvnw verify
```

## 18. Common Pitfalls

- Do NOT create a new `TestLifecycleManager` -- reuse the one in the module you are testing.
- Do NOT use `@Mock` from plain Mockito in `@QuarkusTest` classes -- use `@InjectMock` or `@InjectSpy` instead.
- Do NOT forget `@QuarkusTestResource(TestLifecycleManager.class)` on `@QuarkusTest` classes that need database or WireMock.
- Do NOT skip extending `DbIsolatedTest` for database tests -- test pollution will cause intermittent failures.
- When testing async message processing, always use `Awaitility.await()` with a timeout rather than `Thread.sleep()`.
- Clean WireMock stubs between tests using `getClient().resetAll()` in `@BeforeEach` when tests configure different stubs.
