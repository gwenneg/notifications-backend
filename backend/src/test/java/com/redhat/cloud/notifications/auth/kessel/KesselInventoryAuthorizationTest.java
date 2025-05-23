package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.KesselPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.rhid.RhServiceAccountIdentity;
import com.redhat.cloud.notifications.auth.principal.rhid.RhUserIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.ingress.Type;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta1.authz.CheckRequest;
import org.project_kessel.api.inventory.v1beta1.authz.CheckResponse;
import org.project_kessel.api.inventory.v1beta1.authz.ObjectReference;
import org.project_kessel.api.inventory.v1beta1.authz.SubjectReference;
import org.project_kessel.api.inventory.v1beta1.resources.ListNotificationsIntegrationsRequest;
import org.project_kessel.api.inventory.v1beta1.resources.ListNotificationsIntegrationsResponse;
import org.project_kessel.api.inventory.v1beta1.resources.NotificationsIntegration;
import org.project_kessel.api.inventory.v1beta1.resources.ReporterData;
import org.project_kessel.inventory.client.KesselCheckClient;
import org.project_kessel.inventory.client.NotificationsIntegrationClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.COUNTER_TAG_FAILURES;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.COUNTER_TAG_REQUEST_RESULT;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.COUNTER_TAG_SUCCESSES;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.KESSEL_IDENTITY_SUBJECT_TYPE;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
public class KesselInventoryAuthorizationTest {
    @InjectSpy
    BackendConfig backendConfig;

    @InjectMock
    KesselCheckClient checkClient;

    @InjectMock
    NotificationsIntegrationClient notificationsIntegrationClient;

    @Inject
    KesselInventoryAuthorization kesselAuthorization;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @BeforeEach
    void beforeEach() {
        // save counter values
        saveCounterValues();
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * does not raise an exception.
     */
    @Test
    void testAuthorized() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselRelationsEnabled(anyString())).thenReturn(true);
        Mockito.when(this.backendConfig.isKesselInventoryUseForPermissionsChecksEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns a positive response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_TRUE).build();
        Mockito.when(this.checkClient.Check(Mockito.any())).thenReturn(positiveCheckResponse);

        // Call the function under test.
        this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            WorkspacePermission.EVENT_LOG_VIEW,
            KesselInventoryResourceType.WORKSPACE,
            "workspace-uuid"
        );

        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).Check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(1, 0, 0, 0);
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * returns true.
     */
    @Test
    void testAuthorizedCriterion() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselRelationsEnabled(anyString())).thenReturn(true);
        Mockito.when(this.backendConfig.isKesselInventoryUseForPermissionsChecksEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns a positive response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_TRUE).build();
        Mockito.when(this.checkClient.Check(Mockito.any())).thenReturn(positiveCheckResponse);
        RecipientsAuthorizationCriterion authorizationCriterion = new RecipientsAuthorizationCriterion();
        authorizationCriterion.setId("workspace-uuid");
        authorizationCriterion.setRelation(WorkspacePermission.EVENT_LOG_VIEW.getKesselPermissionName());
        Type t = new Type();
        t.setNamespace("rbac");
        t.setName("workspace");
        authorizationCriterion.setType(t);

        // Call the function under test.
        boolean isAuthorized = this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            authorizationCriterion
        );

        assertTrue(isAuthorized);
        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).Check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(1, 0, 0, 0);
    }

    /**
     * Tests failure counter increments in case of exception.
     */
    @Test
    void testFailureCounterIncrements() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Simulate that Kessel returns an exception
        Mockito.when(this.checkClient.Check(Mockito.any())).thenThrow(RuntimeException.class);

        // Call the function under test.
        Assertions.assertThrows(
            RuntimeException.class,
            () -> this.kesselAuthorization.hasPermissionOnResource(
                    mockedSecurityContext,
                    WorkspacePermission.EVENT_LOG_VIEW,
                    KesselInventoryResourceType.WORKSPACE,
                    "workspace-uuid"
            )
        );

        // Assert counter values
        assertCounterIncrements(0, 1, 0, 0);

        // Return the exception to simulate a Kessel error.
        Mockito.when(this.notificationsIntegrationClient.listNotificationsIntegrations(Mockito.any())).thenThrow(RuntimeException.class);

        // Call the function under test.
        Assertions.assertThrows(
            RuntimeException.class,
            () -> this.kesselAuthorization.lookupAuthorizedIntegrations(mockedSecurityContext, UUID.randomUUID(), IntegrationPermission.VIEW)
        );

        // Assert counter values
        assertCounterIncrements(0, 1, 0, 1);
    }

    /**
     * Tests that when the principal is authorized for the given permission on
     * the given integration, the function under test does not throw an
     * exception.
     */
    @Test
    void testHasPermissionOnIntegration() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Simulate an "authorized" response from Kessel.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_TRUE).build();
        Mockito.when(this.checkClient.Check(Mockito.any())).thenReturn(positiveCheckResponse);

        // Call the function under test.
        this.kesselAuthorization.hasPermissionOnIntegration(mockedSecurityContext, IntegrationPermission.VIEW, UUID.randomUUID());
    }

    /**
     * Tests that when the principal is not authorized for the given permission
     * on the given integration, the function under test throws a "not found"
     * exception.
     */
    @Test
    void testHasPermissionOnIntegrationThrowsNotFoundException() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Simulate an "unauthorized" response coming from Kessel.
        final CheckResponse negativeCheckResponse = CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_FALSE).build();
        Mockito.when(this.checkClient.Check(Mockito.any())).thenReturn(negativeCheckResponse);

        // Call the function under test.
        Assertions.assertThrows(
            NotFoundException.class,
            () -> this.kesselAuthorization.hasPermissionOnIntegration(mockedSecurityContext, IntegrationPermission.VIEW, UUID.randomUUID())
        );

        // Assert counter values
        assertCounterIncrements(1, 0, 0, 0);
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * throws an exception.
     */
    @Test
    void testUnauthorized() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselRelationsEnabled(anyString())).thenReturn(true);
        Mockito.when(this.backendConfig.isKesselInventoryUseForPermissionsChecksEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns a negative response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_FALSE).build();
        Mockito.when(this.checkClient.Check(Mockito.any())).thenReturn(positiveCheckResponse);

        // Call the function under test and expect that it throws a "Forbidden"
        // exception.
        Assertions.assertThrows(
            ForbiddenException.class,
            () -> this.kesselAuthorization.hasPermissionOnResource(
                mockedSecurityContext,
                WorkspacePermission.EVENT_LOG_VIEW,
                KesselInventoryResourceType.WORKSPACE,
                "workspace-uuid"
            ),
            "unexpected exception thrown, as with a negative response from Kessel it should throw a \"Forbidden exception\""
        );

        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).Check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(1, 0, 0, 0);
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * returns false.
     */
    @Test
    void testUnauthorizedCriterion() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselRelationsEnabled(anyString())).thenReturn(true);
        Mockito.when(this.backendConfig.isKesselInventoryUseForPermissionsChecksEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns a negative response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_FALSE).build();
        Mockito.when(this.checkClient.Check(Mockito.any())).thenReturn(positiveCheckResponse);

        RecipientsAuthorizationCriterion authorizationCriterion = new RecipientsAuthorizationCriterion();
        authorizationCriterion.setId("workspace-uuid");
        authorizationCriterion.setRelation(WorkspacePermission.EVENT_LOG_VIEW.getKesselPermissionName());
        Type t = new Type();
        t.setNamespace("rbac");
        t.setName("workspace");
        authorizationCriterion.setType(t);

        // Call the function under test.
        boolean isAuthorized = this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            authorizationCriterion
        );

        assertFalse(isAuthorized);
        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).Check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(1, 0, 0, 0);
    }

    /**
     * Tests that when Kessel check fails, the function under test
     * returns false.
     */
    @Test
    void testUnauthorizedCriterionBecauseOfException() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselRelationsEnabled(anyString())).thenReturn(true);
        Mockito.when(this.backendConfig.isKesselInventoryUseForPermissionsChecksEnabled(anyString())).thenReturn(true);

        // Simulate that Kessel returns an exception
        Mockito.when(this.checkClient.Check(Mockito.any())).thenThrow(RuntimeException.class);

        RecipientsAuthorizationCriterion authorizationCriterion = new RecipientsAuthorizationCriterion();
        authorizationCriterion.setId("workspace-uuid");
        authorizationCriterion.setRelation(WorkspacePermission.EVENT_LOG_VIEW.getKesselPermissionName());
        Type t = new Type();
        t.setNamespace("rbac");
        t.setName("workspace");
        authorizationCriterion.setType(t);

        // Call the function under test.
        boolean isAuthorized = this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            authorizationCriterion
        );

        assertFalse(isAuthorized);
        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).Check(Mockito.any());

        // Assert counter values
        assertCounterIncrements(0, 1, 0, 0);
    }

    /**
     * Tests that the incoming integration UUID's are correctly parsed and
     * returned in a set.
     */
    @Test
    void testLookupAuthorizedIntegrations() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = initMockedSecurityContextWithRhIdentity();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselRelationsEnabled(anyString())).thenReturn(true);
        Mockito.when(this.backendConfig.isKesselInventoryUseForPermissionsChecksEnabled(anyString())).thenReturn(true);

        Set<UUID> expectedUuids = Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        Set<ListNotificationsIntegrationsResponse> setList = new HashSet<>();
        for (UUID uuid : expectedUuids) {
            setList.add(
                ListNotificationsIntegrationsResponse.newBuilder().setIntegrations(
                    NotificationsIntegration.newBuilder().setReporterData(
                        ReporterData.newBuilder().setLocalResourceId(uuid.toString()).build()
                    ).build()
                ).build()
            );
        }

        Multi<ListNotificationsIntegrationsResponse> mockedResponse =  Multi.createFrom().items(setList.stream());

        Mockito.when(this.notificationsIntegrationClient.listNotificationsIntegrations(Mockito.any())).thenReturn(mockedResponse);

        // Call the function under test.
        final Set<UUID> result = this.kesselAuthorization.lookupAuthorizedIntegrations(mockedSecurityContext, UUID.randomUUID(), IntegrationPermission.VIEW);

        // Asser that oen call was made to Kessel
        Mockito.verify(this.notificationsIntegrationClient, Mockito.times(1)).listNotificationsIntegrations(Mockito.any());

        // Assert counter values
        assertCounterIncrements(0, 0, 1, 0);

        result.forEach(r -> Assertions.assertTrue(expectedUuids.contains(r), String.format("UUID \"%s\" not present in the expected UUIDs", r)));
    }

    /**
     * Mock the security context.
     */
    private static @NotNull SecurityContext initMockedSecurityContextWithRhIdentity() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Create a RhIdentity principal and assign it to the mocked security
        // context.
        final RhIdentity identity = Mockito.mock(RhIdentity.class);
        Mockito.when(identity.getName()).thenReturn("Red Hat user");

        final ConsolePrincipal<?> principal = new RhIdPrincipal(identity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(principal);
        return mockedSecurityContext;
    }

    /**
     * Test that permission check requests are properly built for both service
     * accounts and users.
     */
    @Test
    void testBuildCheckRequest() {
        record TestCase(RhIdentity identity, KesselInventoryResourceType resourceType, KesselPermission permission, String resourceId) {
            @Override
            public String toString() {
                return "TestCase{" +
                    "identity='" + this.identity + '\'' +
                    ", resourceType='" + this.resourceType + '\'' +
                    ", kesselPermission='" + this.permission + '\'' +
                    ", resourceId='" + this.resourceId + '\'' +
                    '}';
            }
        }

        // Create a user identity object.
        final String username = "Red Hat user";
        final RhIdentity userIdentity = Mockito.mock(RhUserIdentity.class);
        Mockito.when(userIdentity.getUserId()).thenReturn(username);

        // Create a service account identity object.
        final String serviceAccountName = String.format("service-account-%s", UUID.randomUUID());
        final RhIdentity serviceAccountIdentity = Mockito.mock(RhServiceAccountIdentity.class);
        Mockito.when(serviceAccountIdentity.getUserId()).thenReturn(serviceAccountName);

        // Loop through the supported identities.
        final List<TestCase> testCases = List.of(
            new TestCase(userIdentity, KesselInventoryResourceType.INTEGRATION, IntegrationPermission.VIEW, "12345"),
            new TestCase(serviceAccountIdentity, KesselInventoryResourceType.INTEGRATION, IntegrationPermission.EDIT, "54321"),
            new TestCase(userIdentity, KesselInventoryResourceType.WORKSPACE, WorkspacePermission.CREATE_DRAWER_INTEGRATION, "workspace-a"),
            new TestCase(serviceAccountIdentity, KesselInventoryResourceType.WORKSPACE, WorkspacePermission.EVENT_LOG_VIEW, "workspace-b")
        );

        for (final TestCase tc : testCases) {
            // Call the function under test.
            final CheckRequest checkRequest = this.kesselAuthorization.buildCheckRequest(tc.identity(), tc.permission(), tc.resourceType(), tc.resourceId());

            // Make sure the request was built appropriately.
            final ObjectReference objectReference = checkRequest.getParent();
            Assertions.assertEquals(tc.resourceType().getKesselObjectType(), objectReference.getType(), String.format("unexpected resource type obtained for the object's reference on test case: %s", tc));
            Assertions.assertEquals(tc.resourceId(), objectReference.getId(), String.format("unexpected resource ID obtained for the object's reference on test case: %s", tc));

            Assertions.assertEquals(tc.permission().getKesselPermissionName(), checkRequest.getRelation(), String.format("unexpected relation obtained on test case: %s", tc));

            final SubjectReference subjectReference = checkRequest.getSubject();
            Assertions.assertEquals(KESSEL_IDENTITY_SUBJECT_TYPE, subjectReference.getSubject().getType().getName(), String.format("unexpected resource type obtained for the subject's reference on test case: %s", tc));
            Assertions.assertEquals(backendConfig.getKesselDomain() + "/" + tc.identity().getUserId(), subjectReference.getSubject().getId(), String.format("unexpected resource ID obtained for the subject's reference on test case: %s", tc));
        }
    }

    /**
     * Tests that the function under test removes the integrations for which
     * the user does not have authorization to view.
     */
    @Test
    void testFilterUnauthorizedIntegrations() {
        // Create a user identity object.
        final SecurityContext securityContext = initMockedSecurityContextWithRhIdentity();

        // Define a set of authorized integrations for the principal.
        final Set<UUID> authorizedIntegrations = Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // Create a mixed list of authorized and unauthorized endpoints.
        final List<Endpoint> endpoints = new ArrayList<>();
        for (final UUID id : authorizedIntegrations) {
            // Create the endpoint the principal will have the authorization
            // for, and mock the Kessel response.
            final Endpoint authorizedEndpoint = new Endpoint();
            authorizedEndpoint.setId(id);
            endpoints.add(authorizedEndpoint);

            final CheckRequest authorizedCheckRequest = this.kesselAuthorization.buildCheckRequest(((RhIdPrincipal) securityContext.getUserPrincipal()).getIdentity(), IntegrationPermission.VIEW, KesselInventoryResourceType.INTEGRATION, authorizedEndpoint.getId().toString());
            Mockito.when(this.checkClient.Check(authorizedCheckRequest)).thenReturn(CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_TRUE).build());

            // Create the endpoint the principal will not have the
            // authorization for, and mock the Kessel response.
            final Endpoint unauthorizedEndpoint = new Endpoint();
            unauthorizedEndpoint.setId(UUID.randomUUID());
            endpoints.add(unauthorizedEndpoint);

            final CheckRequest unauthorizedCheckRequest = this.kesselAuthorization.buildCheckRequest(((RhIdPrincipal) securityContext.getUserPrincipal()).getIdentity(), IntegrationPermission.VIEW, KesselInventoryResourceType.INTEGRATION, unauthorizedEndpoint.getId().toString());
            Mockito.when(this.checkClient.Check(unauthorizedCheckRequest)).thenReturn(CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_FALSE).build());
        }

        // Call the function under test.
        final List<Endpoint> resultingEndpoints = this.kesselAuthorization.filterUnauthorizedIntegrations(securityContext, endpoints);

        // Assert that the only integrations left in the list are the ones the
        // principal has authorization for.
        resultingEndpoints.forEach(endpoint -> Assertions.assertTrue(authorizedIntegrations.contains(endpoint.getId()), String.format("Unauthorized endpoint \"%s\"found when it should have been removed from the set \"%s\"", endpoint.getId(), authorizedIntegrations)));
    }

    /**
     * Tests that the "lookup resources" requests are properly built both for
     * service accounts and users.
     */
    @Test
    void testBuildLookupResourcesRequest() {
        record TestCase(RhIdentity identity, IntegrationPermission permission) {
            @Override
            public String toString() {
                return "TestCase{" +
                    "identity='" + this.identity + '\'' +
                    ", integrationPermission='" + this.permission + '\'' +
                    '}';
            }
        }

        // Create a user identity object.
        final String username = "Red Hat user";
        final RhIdentity userIdentity = Mockito.mock(RhUserIdentity.class);
        Mockito.when(userIdentity.getName()).thenReturn(username);

        // Create a service account identity object.
        final String serviceAccountName = String.format("service-account-%s", UUID.randomUUID());
        final RhIdentity serviceAccountIdentity = Mockito.mock(RhServiceAccountIdentity.class);
        Mockito.when(serviceAccountIdentity.getName()).thenReturn(serviceAccountName);

        // Loop through the supported identities.
        final List<TestCase> testCases = List.of(
            new TestCase(userIdentity, IntegrationPermission.VIEW),
            new TestCase(serviceAccountIdentity, IntegrationPermission.VIEW)
        );

        for (final TestCase tc : testCases) {
            // Call the function under test.
            final ListNotificationsIntegrationsRequest lookupResourcesRequest = this.kesselAuthorization.buildListIntegrationRequest(tc.identity(), UUID.randomUUID(), tc.permission());

            // Make sure the request was built appropriately.
            final SubjectReference subjectReference = lookupResourcesRequest.getSubject();
            Assertions.assertEquals(KESSEL_IDENTITY_SUBJECT_TYPE, subjectReference.getSubject().getType().getName(), String.format("unexpected resource type obtained for the subject's reference on test case: %s", tc));
            Assertions.assertEquals(backendConfig.getKesselDomain() + "/" + tc.identity().getUserId(), subjectReference.getSubject().getId(), String.format("unexpected resource ID obtained for the subject's reference on test case: %s", tc));

            Assertions.assertEquals(tc.permission().getKesselPermissionName(), lookupResourcesRequest.getRelation(), String.format("unexpected relation obtained on test case: %s", tc));

            Assertions.assertEquals(KesselInventoryResourceType.INTEGRATION.getKesselObjectType(), lookupResourcesRequest.getResourceType(), String.format("unexpected resource type obtained on test case: %s", tc));
        }
    }

    private void saveCounterValues() {
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KesselInventoryAuthorization.KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES);
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES);
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES);
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES);
    }

    private void assertCounterIncrements(final int expectedPermissionCheckSuccesses, final int expectedPermissionCheckFailures, final int expectedLookupResourcesSuccesses, int expectedLookupResourcesFailures) {
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, expectedPermissionCheckSuccesses);
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, expectedPermissionCheckFailures);
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, expectedLookupResourcesSuccesses);
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, expectedLookupResourcesFailures);
    }
}
