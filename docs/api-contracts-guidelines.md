# API Contracts Guidelines

## 1. Project Structure and Routing

### 1.1 Resource Class Location
- Public API resources live in `backend/src/main/java/com/redhat/cloud/notifications/routers/handlers/`
- Internal API resources live in `backend/src/main/java/com/redhat/cloud/notifications/routers/internal/`
- Resources are organized by domain: `endpoint/`, `notification/`, `drawer/`, `event/`, `userconfig/`, `orgconfig/`

### 1.2 Resource Class Naming
- Name classes `{Domain}Resource.java` for V1 (e.g., `EndpointResource`, `NotificationResource`)
- Name V2 classes `{Domain}ResourceV2.java` (e.g., `EndpointResourceV2`, `NotificationResourceV2`)
- Use a `{Domain}ResourceCommon.java` base class for shared logic between V1 and V2

### 1.3 Versioned Inner Class Pattern (CRITICAL)
Resources do NOT place `@Path` on the outer class. Instead, they use a **static inner class** to bind the version path:
```java
public class EndpointResource extends EndpointResourceCommon {

    @Path(API_INTEGRATIONS_V_1_0 + "/endpoints")
    static class V1 extends EndpointResource {
    }
    // ... methods use relative @Path
}
```
V2 resources follow the same pattern:
```java
public class EndpointResourceV2 extends EndpointResourceCommon {
    @Path(API_INTEGRATIONS_V_2_0 + "/endpoints")
    public static class V2 extends EndpointResourceV2 {
    }
}
```
Never put `@Path` with the base API path directly on the outer resource class. The inner class inherits all methods.

## 2. API Path Constants

### 2.1 All base paths are defined in `common/src/main/java/com/redhat/cloud/notifications/Constants.java`
```
API_INTEGRATIONS_V_1_0  = "/api/integrations/v1.0"
API_INTEGRATIONS_V_2_0  = "/api/integrations/v2.0"
API_NOTIFICATIONS_V_1_0 = "/api/notifications/v1.0"
API_NOTIFICATIONS_V_2_0 = "/api/notifications/v2.0"
API_INTERNAL            = "/internal"
```

### 2.2 Path Naming Rules
- Use lowercase, plural nouns for resource paths: `/endpoints`, `/notifications`, `/eventTypes`
- Use camelCase for composite path segments: `/eventTypes`, `/behaviorGroups` (not kebab-case)
- Use `{id}` or `{descriptiveId}` for path parameters (e.g., `{endpointId}`, `{eventTypeId}`)
- Sub-resources use nested paths: `/{id}/history`, `/{id}/history/{history_id}/details`

## 3. API Versioning Strategy

### 3.1 V1 vs V2 Differences
- **V1** returns flat lists (`List<T>`) for collection endpoints
- **V2** returns paginated `Page<T>` with `data`, `links`, and `meta` fields
- V2 methods must set an explicit `operationId` via `@Operation(operationId = "ResourceName$V2_methodName")` to avoid OpenAPI ID collisions with V1
- When adding a V2 endpoint, extract shared logic into the `*Common` base class

### 3.2 Internal API
- Internal endpoints use `@Path(API_INTERNAL)` directly on the class (not the inner-class pattern)
- Secured with `@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)` at class or method level
- Never use the `@Authorization` annotation on internal endpoints

## 4. Authorization

### 4.1 Public API Endpoints
Use the custom `@Authorization` annotation on every public endpoint method:
```java
@Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
```
- `legacyRBACRole`: RBAC role string used when Kessel is disabled
- `workspacePermissions`: Kessel workspace permission(s) used when Kessel is enabled
- Read operations use `*_VIEW` permissions; write operations use `*_EDIT` permissions
- RBAC role constants are in `ConsoleIdentityProvider` (e.g., `RBAC_READ_INTEGRATIONS_ENDPOINTS`, `RBAC_WRITE_NOTIFICATIONS`)

### 4.2 Private/Hidden Endpoints
Tag with `@Tag(name = OApiFilter.PRIVATE)` to hide from the public OpenAPI spec while keeping them accessible.

## 5. DTO Layer and MapStruct Mapping

### 5.1 DTO Organization
- DTOs live in `backend/.../models/dto/v1/` package, organized by domain (`endpoint/`, `endpoint/properties/`)
- DTO classes are suffixed with `DTO` (e.g., `EndpointDTO`, `CamelPropertiesDTO`)
- The `OApiFilter` strips the `DTO` suffix from schema names in the generated OpenAPI spec when there is no collision

### 5.2 MapStruct Mappers
- Use `@Mapper(componentModel = MappingConstants.ComponentModel.CDI)` for CDI injection
- Define `toDTO(Entity)` and `toEntity(DTO)` methods
- Use `@Named` qualified methods for polymorphic property mapping (see `EndpointMapper.mapEntityProperties`)
- Ignore internal fields in entity-to-DTO mapping: `@Mapping(target = "orgId", ignore = true)`
- Inject mappers via `@Inject` in resource classes

### 5.3 CommonMapper
`CommonMapper` handles cross-cutting mappings (Bundle, Application, EventType, NotificationHistory). Use it for shared DTO conversions rather than duplicating mapping logic.

## 6. JSON Serialization (Jackson)

### 6.1 Snake Case Convention
All DTOs and request/response models MUST use `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)`. This applies to:
- DTO classes (`EndpointDTO`, `ApplicationDTO`, etc.)
- Request models (`CreateBehaviorGroupRequest`, `UpdateBehaviorGroupRequest`)
- Response models (`CreateBehaviorGroupResponse`, `EventLogEntry`)
- Properties DTOs (via the abstract `EndpointPropertiesDTO` base class)

### 6.2 Jackson Annotations
- Use `@JsonProperty(access = READ_ONLY)` for server-generated fields (e.g., `created`, `updated`)
- Use `@JsonInclude(NON_NULL)` for optional fields that should be omitted when null
- Use `@JsonIgnore` for validation-only methods (e.g., `@AssertTrue` validators on DTOs)
- Use `@JsonFormat(shape = Shape.STRING)` for `LocalDateTime` fields
- Use `@JsonSubTypes` + `@JsonTypeInfo` for polymorphic deserialization (see `EndpointDTO.properties`)

## 7. Request/Response Patterns

### 7.1 Content Types
- Produce and consume `APPLICATION_JSON` by default
- Some update/enable endpoints produce `TEXT_PLAIN` for simple status responses
- Always use static imports: `import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON`

### 7.2 Request Bodies
- Annotate with `@NotNull @Valid @RequestBody` for required bodies
- Use `@RequestBody(required = true)` for OpenAPI documentation
- Use Jakarta Validation annotations (`@NotNull`, `@Size`, `@Min`, `@Max`) on DTO fields

### 7.3 Response Patterns
- **200**: Return the DTO directly (Jackson serializes it)
- **201**: Not used; creation returns 200 with the created object
- **204**: Use `Response.noContent().build()` for delete/disable operations
- **400**: Throw `BadRequestException` with a descriptive message
- **404**: Throw `NotFoundException` (with or without a message)
- Return `Response` only when you need custom status codes; otherwise return the DTO/entity type directly

## 8. Pagination

### 8.1 V1 Pagination (Legacy)
V1 list endpoints return a flat `List<T>` or a specialized page class (`EndpointPage`). Pagination parameters are still accepted via `@BeanParam Query`.

### 8.2 V2 Pagination (Standard)
V2 returns `Page<T>` with three fields:
- `data`: `List<T>` of results
- `links`: `Map<String, String>` with `first`, `last`, `prev`, `next` URLs
- `meta`: `Meta` object with `count` (total element count)

### 8.3 Query Bean (`@BeanParam Query`)
- `limit` (default 20, min 1, max 200): items per page
- `pageNumber` (min 1): page number, converted to offset internally
- `offset` (min 0): direct offset, takes precedence over `pageNumber`
- `sort_by` / `sortBy`: sort field with optional `:asc`/`:desc` suffix
- Use `PageLinksBuilder.build(uriInfo, count, query)` to generate pagination links (preserves query params)

### 8.4 OpenAPI Pagination Documentation
Document pagination parameters explicitly with `@Parameters` and `@Parameter` annotations on each list endpoint. Reference `DEFAULT_RESULTS_PER_PAGE` in descriptions.

## 9. OpenAPI Annotations

### 9.1 Required Annotations
- `@Operation(summary = "...", description = "...")` on every public endpoint
- `@APIResponse` / `@APIResponses` for non-200 responses, especially 400 and 404
- `@Parameter` for query parameters with descriptions
- `@Produces` and `@Consumes` on every endpoint

### 9.2 OpenAPI Filtering
The `OApiFilter` class splits the generated OpenAPI spec into four views: `notifications`, `integrations`, `private`, and `internal`. Endpoints are filtered by their base path. Private-tagged endpoints are excluded from the public specs but included in the `private` spec.

### 9.3 Schema References
Use `@Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = DtoClass.class))` in `@APIResponse` to explicitly reference the response DTO type.

## 10. Backward Compatibility Rules

### 10.1 Adding New Endpoints
- Add new endpoints to V1 only if they follow V1 conventions (flat list returns)
- Prefer adding to both V1 and V2, with V2 using `Page<T>` for collections
- Extract shared logic into the `*Common` base class

### 10.2 Deprecation Pattern
- Use `@Deprecated(forRemoval = true)` on methods slated for removal
- Both `sortBy` (camelCase) and `sort_by` (snake_case) query params are supported; `sortBy` is deprecated

### 10.3 Non-Breaking Changes Only
- Never remove or rename existing fields in DTOs
- Never change the type of existing fields
- New optional fields must use `@JsonInclude(NON_NULL)` to avoid breaking clients
- Never change response status codes for existing endpoints

## 11. Error Handling

- Throw JAX-RS exceptions: `BadRequestException`, `NotFoundException`, `ForbiddenException`
- Use descriptive string messages in exceptions: `throw new BadRequestException("Properties is required")`
- Define error message constants as `public static final String` in the resource class
- Never return raw error maps; let the framework exception mappers handle error responses

## 12. Transaction Management

- Use `@Transactional` on methods that modify data (create, update, delete)
- For operations requiring cleanup on failure (e.g., secrets), wrap in try/catch and clean up explicitly
- Use `@TransactionConfiguration` only for extended timeout scenarios
