# Database Guidelines

## Flyway Migrations

### Location and Module Structure
- All migration files live in `database/src/main/resources/db/migration/`.
- The `database` module is a shared dependency; it contains no Java code, only SQL migrations.
- Flyway runs at startup via `quarkus.flyway.migrate-at-start=true` (backend always; engine/aggregator in dev/test profiles only).

### Versioning Scheme
- Format: `V1.<sequence>.0__<JIRA-TICKET>_<description>.sql`
- The major version is always `1`. The minor version is a monotonically increasing integer (currently up to 129). The patch is always `0` except for rare hotfix corrections (e.g., `V1.55.1`, `V1.55.2`).
- To add a new migration, find the highest existing minor version and increment by 1. Example: if `V1.129.0` exists, create `V1.130.0__RHCLOUD-XXXXX_description.sql`.
- Use double underscores `__` between the version and the description.
- Description uses snake_case with a leading Jira ticket ID (e.g., `RHCLOUD-43290_event_deduplication`).

### Migration Content Conventions
- Use plain SQL, not Java-based migrations.
- Always use `snake_case` for table names, column names, index names, and constraint names.
- Name constraints explicitly with prefixes: `pk_` (primary key), `fk_` (foreign key), `ix_` (index), `uq_` (unique).
- Foreign keys should specify `ON DELETE CASCADE` where child rows are owned by the parent.
- PostgreSQL-specific features are allowed (see PostgreSQL section below).
- Never modify data and schema in ways that break backward compatibility with the running application. Migrations run before new code deploys.

## Database and Connection Configuration

- Database: **PostgreSQL 16** (Testcontainers use `postgres:16`).
- Database name: `notifications`.
- Connection: `jdbc:postgresql://127.0.0.1:5432/notifications` (overridden by Clowder in production).
- The `pgcrypto` extension is required and installed in test setup. The initial migration (`V1.0.0`) enables it for `gen_random_uuid()`.
- Hibernate physical naming strategy: `SnakeCasePhysicalNamingStrategy` -- automatically converts camelCase Java field names to snake_case column names. You do NOT need `@Column(name=...)` for simple fields.

## JPA Entity Conventions

### Entity Structure
- Entities live in `common/src/main/java/com/redhat/cloud/notifications/models/`.
- Module-specific entities (rare) can live in their own module (e.g., `backend/.../models/`, `aggregator/.../models/`, `engine/.../models/`).
- Always annotate with `@Entity` and `@Table(name = "table_name")` with an explicit table name.
- Use `jakarta.persistence` annotations (Jakarta EE, not javax).

### ID Generation
- Primary keys are `UUID` type in nearly all entities.
- Two patterns exist:
  1. **`@GeneratedValue`** on UUID `@Id` -- Hibernate generates the UUID (used by `Event`, `BehaviorGroup`, `EventType`, `Application`, `Bundle`).
  2. **Application-generated UUID** -- The entity sets `id = UUID.randomUUID()` in a `@PrePersist` or `additionalPrePersist()` hook (used by `Endpoint`, `NotificationHistory`).
- For join/association tables, use `@EmbeddedId` with a composite key class implementing `Serializable` (e.g., `BehaviorGroupActionId`, `EventTypeBehaviorId`, `DrawerNotificationId`).

### Audit Timestamps
- Entities that need created/updated timestamps extend `CreationUpdateTimestamped` (which extends `CreationTimestamped`).
- `CreationTimestamped`: sets `created` via `@PrePersist` using `LocalDateTime.now(UTC)`.
- `CreationUpdateTimestamped`: sets `updated` via `@PreUpdate` using `LocalDateTime.now(UTC)`.
- Some entities (e.g., `Event`) manage their own `created` timestamp via a local `@PrePersist`.

### Multi-Tenancy Fields
- Most tenant-scoped entities have both `accountId` (legacy, nullable `VARCHAR(50)`) and `orgId` (`VARCHAR(50)`) fields.
- Always filter queries by `orgId`. The `accountId` field is legacy and being phased out.
- Default behavior groups have `orgId = NULL` (shared across all orgs).

### Relationship Mapping
- Use `FetchType.LAZY` for `@ManyToOne` and `@OneToOne` relationships (explicitly specified as `fetch = LAZY`).
- `@OneToMany` defaults to lazy (no explicit annotation needed, but the codebase sometimes states it).
- Use `CascadeType.REMOVE` on `@OneToMany` for owned children (e.g., `Event.historyEntries`, `BehaviorGroup.actions`).
- The `EndpointProperties` hierarchy uses `@MapsId` + `@OneToOne` to share the same UUID primary key as the parent `Endpoint`.
- Properties are loaded in a separate step via `loadProperties()` because they are `@Transient` on `Endpoint` and loaded by type-specific batch queries.

### Enums
- Use `@Enumerated(EnumType.STRING)` (never ordinal).
- Custom JPA `@Converter` classes exist in `common/.../db/converters/` for types like `HttpType`, `EndpointType`, `SubscriptionType`, and for JSON/Map fields.

### Sort Fields
- Entities expose a `public static final Map<String, String> SORT_FIELDS` mapping API sort parameter names to JPQL field expressions (e.g., `"created" -> "e.created"`).

## Repository Pattern

### Structure
- Repository classes are `@ApplicationScoped` CDI beans in `<module>/src/main/java/com/redhat/cloud/notifications/db/repositories/`.
- They inject `EntityManager` directly (not Spring Data or Panache).
- Class naming: `<Entity>Repository` (e.g., `EndpointRepository`, `EventRepository`, `BehaviorGroupRepository`).
- No repository interfaces or abstract base classes -- each repository is a concrete class.

### Query Patterns
- **Primary query language: HQL/JPQL** via `entityManager.createQuery(hql, ResultType.class)`.
- **Native SQL** is used when PostgreSQL-specific features are needed (e.g., `ON CONFLICT`, `jsonb` casts, `string_agg`, stored procedure calls). Use `entityManager.createNativeQuery(sql)`.
- **QueryBuilder**: A custom fluent query builder (`com.redhat.cloud.notifications.db.builder.QueryBuilder`) is available for dynamic WHERE clause composition with `WhereBuilder`. Use it for queries with many optional filters.
- Dynamic HQL string concatenation is also common for complex filter queries (see `EventRepository`).
- Always use named parameters (`:paramName`), never positional parameters.
- For pagination, use `query.setMaxResults(limit)` and `query.setFirstResult(offset)`.

### Upserts
- Use PostgreSQL `INSERT ... ON CONFLICT ... DO UPDATE/DO NOTHING` via native queries for upsert operations (e.g., `SubscriptionRepository.updateSubscription`).

### Locking
- `PESSIMISTIC_WRITE` lock mode is used in `BehaviorGroupRepository` and engine's `EndpointRepository` for concurrent modification scenarios. Apply via `.setLockMode(PESSIMISTIC_WRITE)` on the query.

## Transaction Management

- Use `@Transactional` from `jakarta.transaction.Transactional` (JTA, not Spring).
- Place `@Transactional` on repository methods that perform writes (`persist`, `executeUpdate`, `DELETE`, etc.).
- Read-only queries do NOT need `@Transactional`.
- For batch operations requiring manual transaction control, inject `StatelessSession` from Hibernate and manage transactions explicitly (`beginTransaction()` / `commit()` / `rollback()`).

## PostgreSQL-Specific Features

- **pgcrypto extension**: Used for `gen_random_uuid()` in early migrations.
- **JSONB columns**: Used for payload data, notification details, and subscription severities. Cast with `CAST(:param AS jsonb)` in native queries. Use `@JdbcTypeCode(SqlTypes.JSON)` for Hibernate mapping.
- **`ON CONFLICT` (upsert)**: Widely used for idempotent inserts.
- **`string_agg()`**: Used for aggregating values in native queries.
- **`jsonb_each_text()`, `jsonb_object_agg()`**: Used for JSONB manipulation in native queries.
- **Stored procedures**: Defined in migrations for cleanup jobs (`cleanEventLog`, `cleanKafkaMessagesIds`, `cleanEventDeduplication`), executed by external CronJobs.
- **Custom autovacuum settings**: Configured per-table in migrations for high-churn tables.
- **Index comments**: Used to document the purpose of indexes (`COMMENT ON INDEX ...`).

## Testing

- Tests use **Testcontainers** with PostgreSQL 16 via `TestLifecycleManager`.
- The pgcrypto extension is installed manually in the test setup.
- Flyway migrations run automatically in test mode.
- Test helper classes (`ResourceHelpers`) create test data; prefer using them over raw SQL in tests.

## Key Schema Patterns

- UUIDs for all primary keys (no integer sequences in modern tables).
- Composite primary keys for join/association tables (never surrogate IDs).
- Denormalization is used strategically (e.g., `Event` stores `bundleDisplayName`, `applicationDisplayName`, `eventTypeDisplayName` to avoid joins).
- Soft references: `NotificationHistory` duplicates `endpointType`/`endpointSubType` so data survives endpoint deletion.
- The `CompositeEndpointType` `@Embeddable` pattern encapsulates type + subtype as a reusable embedded component.
- Foreign key constraints with `ON DELETE CASCADE` for owned relationships.
