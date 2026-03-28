# Security Guidelines

Rules for AI agents implementing or reviewing code in the notifications-backend repository.

## 1. Authentication: x-rh-identity Header

- All external requests are authenticated via the `x-rh-identity` Base64-encoded header (`Constants.X_RH_IDENTITY_HEADER`).
- The header is decoded and deserialized into a `ConsoleIdentity` subtype using Jackson polymorphic deserialization in `ConsoleIdentityProvider.getRhIdentityFromString()`.
- Supported identity types: `User` (`RhUserIdentity`), `ServiceAccount` (`RhServiceAccountIdentity`), `Associate/SAML` (`TurnpikeSamlIdentity`), `X509` (`TurnpikeX509Identity`).
- Never process requests without verifying that `org_id` is present and non-blank for `RhIdentity` types. The existing code rejects null/blank `org_id` with `AuthenticationFailedException`.
- Never expose authentication failure details in response bodies. Tests explicitly assert `body(emptyString())` on 401 responses. Follow this pattern: throw `AuthenticationFailedException()` without a message that reaches the client.
- The `ConsoleAuthMechanism` whitelists unauthenticated paths (OpenAPI specs, health, metrics, validation endpoints). Do not add new unauthenticated paths without security review.

## 2. Authorization: Dual RBAC/Kessel System

### 2.1 Legacy RBAC (role-based)
- RBAC permissions are fetched from an external RBAC server via `RbacServer` REST client at authentication time and cached (`@CacheResult(cacheName = "rbac-cache")`).
- Roles are mapped to constants in `ConsoleIdentityProvider`: `RBAC_READ_NOTIFICATIONS`, `RBAC_WRITE_NOTIFICATIONS`, `RBAC_READ_INTEGRATIONS_ENDPOINTS`, `RBAC_WRITE_INTEGRATIONS_ENDPOINTS`, `RBAC_READ_NOTIFICATIONS_EVENTS`.
- Internal API endpoints use `@RolesAllowed` with `RBAC_INTERNAL_USER` (read) or `RBAC_INTERNAL_ADMIN` (write). Apply these annotations consistently to all internal endpoints.

### 2.2 Kessel (relationship-based)
- Kessel is the newer authorization backend and takes priority when enabled for an org (`backendConfig.isKesselEnabled(orgId)`).
- When Kessel is enabled, no RBAC roles are attached to the `SecurityIdentity`; authorization happens at request time via gRPC calls to Kessel Inventory.
- Use the `@Authorization` annotation on endpoint methods for declarative permission checks. Always specify both `legacyRBACRole` (for RBAC fallback) and `workspacePermissions` (for Kessel).
- Example: `@Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)`.
- The `AuthorizationInterceptor` requires a `SecurityContext` parameter on annotated methods. Omitting it causes an `IllegalStateException` at runtime.
- For Kessel, use `CheckOperation.CHECK` for read-only operations and `CheckOperation.UPDATE` (via `checkForUpdate`) for write operations. This distinction matters for side effects in the inventory system.
- Kessel `WorkspacePermission` values: `EVENTS_VIEW`, `INTEGRATIONS_VIEW`, `INTEGRATIONS_EDIT`, `NOTIFICATIONS_VIEW`, `NOTIFICATIONS_EDIT`.

### 2.3 Critical Rules
- Never bypass authorization checks. Every public-facing endpoint must have either `@Authorization` or `@RolesAllowed`.
- When both RBAC and Kessel are disabled in a non-local environment, authentication must fail. The code enforces this: `if (!this.environment.isLocal()) { throw AuthenticationFailedException }`.
- Development-mode full-privilege identity (`buildDevelopmentSecurityIdentity`) must only be reachable in local environments.

## 3. Tenant Isolation (orgId)

- Every database query for tenant-scoped data MUST filter by `orgId`. This is the primary tenant isolation mechanism.
- Extract `orgId` from the security context using `SecurityContextUtil.getOrgId(securityContext)`, which casts the principal to `RhIdPrincipal`.
- Never trust `orgId` from request parameters or path variables for data owned by the requesting user. Always derive it from the authenticated identity.
- Repository methods consistently include `WHERE ... orgId = :orgId` or `WHERE ... e.orgId = :orgId` clauses. Maintain this pattern in all new queries.
- Some resources (like default behavior groups) use `orgId IS NULL` to indicate system-wide resources. Queries that mix tenant and system resources must explicitly handle both: `(etb.behaviorGroup.orgId is NULL OR etb.behaviorGroup.orgId = :orgId)`.

## 4. Internal API Security

- Internal APIs are under `Constants.API_INTERNAL` path prefix.
- Turnpike (SAML Associate) identities are used for internal API access. The `RBAC_INTERNAL_USER` role is always added for Turnpike identities.
- The `RBAC_INTERNAL_ADMIN` role is granted only when the identity's roles include the configured `internal.admin-role`.
- Fine-grained internal access control uses `InternalRoleAccess` to restrict operations to specific applications. Use `SecurityContextUtil.hasPermissionForRole()`, `hasPermissionForApplication()`, or `hasPermissionForEventType()` for internal endpoints that need app-level scoping.
- When `internal-rbac.enabled` is false, all internal requests get full admin access. This must only be used in development/ephemeral environments.

## 5. Principal and Identity Handling

- Use `SecurityContextUtil` static methods to extract identity details: `getOrgId()`, `getAccountId()`, `getUsername()`, `isServiceAccountAuthentication()`, `extractRhIdentity()`.
- For Kessel authorization, the `user_id` field from the identity is required (used as the subject in permission checks). The code rejects identities with missing `user_id` when Kessel is enabled.
- Never construct identity objects from untrusted input in production code. The `TestHelpers` class provides `encodeRHIdentityInfo()`, `encodeRHServiceAccountIdentityInfo()`, and `encodeTurnpikeIdentityInfo()` for test-only identity construction.
- The Kessel subject reference format is: `{kesselDomain}/{userId}` with resource type `principal` and namespace `rbac`.

## 6. Secrets Management

- Secrets (API tokens, passwords, bearer tokens) are stored in the external Sources service, not in the notifications database. Endpoint properties store only Sources secret IDs (e.g., `secretTokenSourcesId`, `bearerAuthenticationSourcesId`).
- Use `SecretUtils` to create, read, update, and delete secrets in Sources. It supports both PSK and OIDC authentication to Sources.
- The Sources PSK is injected via `@ConfigProperty(name = "sources.psk")`. Never log or expose PSK values. The `RbacClientResponseFilter` explicitly redacts headers containing "psk".
- OIDC client secrets use placeholder values in `application.properties` (e.g., `REPLACE_ME_FROM_ENV_VAR`) and must be overridden via environment variables or Clowder config.
- Never hardcode real secrets in `application.properties`. Use `${clowder.endpoints.*}` property references for production values.

## 7. Input Validation

- Use Jakarta Bean Validation annotations on DTOs and request parameters: `@NotNull`, `@NotBlank`, `@Valid`, `@Size`, `@Min`.
- Apply `@Valid` on method parameters that are request bodies to trigger nested validation (e.g., `@NotNull @Valid EventType eventType`).
- Use `@JsonIgnoreProperties(ignoreUnknown = true)` on deserialized request models to prevent unexpected fields from being processed.
- Mark server-controlled fields with `@JsonProperty(access = JsonProperty.Access.READ_ONLY)` to prevent client-side manipulation (e.g., `created`, `updated` timestamps on `EndpointDTO`).
- Use `@JsonIgnore` on internal/computed fields that should not be serialized to API responses (e.g., `InternalRoleAccess.getInternalRole()`).

## 8. TLS Configuration

- All inter-service REST client communication uses TLS trust stores provided by Clowder: `trust-store`, `trust-store-password`, `trust-store-type` properties.
- The Kessel gRPC client supports both secure (OAuth2 + TLS) and insecure modes. Insecure mode (`notifications.kessel.insecure-client.enabled`) disables OAuth2 and TLS verification. It must only be enabled in development. The code logs a warning when insecure mode is active.
- gRPC connections to Kessel use configurable timeouts (`notifications.kessel.timeout-ms`) with `withDeadlineAfter()`.

## 9. Error Handling and Logging

- Never log the decoded contents of `x-rh-identity` headers at INFO level or above. Use DEBUG/TRACE for identity details.
- The `RbacClientResponseFilter` logs RBAC failures at WARN level and full request/response details only at TRACE level, with PSK headers redacted.
- Throw `ForbiddenException` (403) for authorization failures, `AuthenticationFailedException` (401) for authentication failures. Never return detailed error messages that reveal the authorization backend in use.
- RBAC server call failures are retried with exponential backoff (`rbac.retry.max-attempts`, `rbac.retry.back-off.*`). After exhausting retries, they result in `AuthenticationFailedException`.
- Kessel gRPC errors are classified as transient (UNAVAILABLE, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED, ABORTED) and retried via `@Retry(maxRetries = 3)`, or non-transient and propagated immediately. UNAUTHENTICATED triggers channel recreation with fresh OAuth2 credentials.

## 10. Testing Security

- Use `MockServerConfig.addMockRbacAccess()` with WireMock to simulate RBAC responses in tests. Available access levels: `FULL_ACCESS`, `NOTIFICATIONS_READ_ACCESS_ONLY`, `NOTIFICATIONS_ACCESS_ONLY`, `READ_ACCESS`, `NO_ACCESS`.
- Always test both authorized and unauthorized scenarios. Verify that 401/403 responses have empty bodies.
- Use `TestHelpers.encodeRHIdentityInfo()` and related methods to create test identity headers. Never reuse identity headers across tests that require different org isolation.
- For Kessel tests, use `KesselTestHelper` and mock `BackendConfig.isKesselEnabled()` via `@InjectSpy`.
- Test Turnpike identity handling separately, verifying that roles are correctly mapped through `InternalRoleAccess.getInternalRole()`.
- The `AuthorizationInterceptorTest` validates the interceptor behavior. When adding new `@Authorization`-annotated endpoints, add corresponding interceptor tests.

## 11. Feature Toggles (Unleash)

- Security-affecting features (Kessel enablement, drawer, Sources OIDC auth) are controlled via Unleash feature toggles with org-level context.
- `isKesselEnabled(orgId)` checks both the config property and the Unleash toggle, using orgId as context. This allows gradual rollout per organization.
- Never assume a feature toggle state. Always check via `BackendConfig` methods, which encapsulate the Unleash logic.
