package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.redhat.cloud.notifications.helpers.RestApiHelpers.BAD_REQUEST;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.INTERNAL_SERVER_ERROR;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.NOT_FOUND;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.OK;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.createApp;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.createBundle;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.createEventType;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.deleteApp;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.deleteBundle;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.deleteEventType;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.getApp;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.getApps;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.getBundle;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.getEventTypes;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.setDefaultBehaviorGroup;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.updateApp;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.updateBundle;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class InternalServiceTest extends DbIsolatedTest {

    private static final String NOT_USED = "not-used-in-assertions";

    @Test
    void testCreateNullBundle() {
        createBundle(null, BAD_REQUEST);
    }

    @Test
    void testCreateNullApp() {
        createApp(null, BAD_REQUEST);
    }

    @Test
    void testCreateNullEventType() {
        createEventType(null, BAD_REQUEST);
    }

    @Test
    void testCreateInvalidBundle() {
        createBundle(null, "I am valid", BAD_REQUEST);
        createBundle("i-am-valid", null, BAD_REQUEST);
        createBundle("I violate the @Pattern constraint", "I am valid", BAD_REQUEST);
    }

    @Test
    void testCreateInvalidApp() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        createApp(null, "i-am-valid", "I am valid", BAD_REQUEST);
        createApp(bundleId, null, "I am valid", BAD_REQUEST);
        createApp(bundleId, "i-am-valid", null, BAD_REQUEST);
        createApp(bundleId, "I violate the @Pattern constraint", "I am valid", BAD_REQUEST);
    }

    @Test
    void testCreateInvalidEventType() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String appId = createApp(bundleId, "app-name", "App", OK).get();
        createEventType(null, "i-am-valid", "I am valid", NOT_USED, BAD_REQUEST);
        createEventType(appId, null, "I am valid", NOT_USED, BAD_REQUEST);
        createEventType(appId, "i-am-valid", null, NOT_USED, BAD_REQUEST);
        createEventType(appId, "I violate the @Pattern constraint", "I am valid", NOT_USED, BAD_REQUEST);
    }

    @Test
    void testBundleNameUniqueSqlConstraint() {
        // Double bundle creation with the same name.
        String nonUniqueBundleName = "bundle-1-name";
        createBundle(nonUniqueBundleName, NOT_USED, OK);
        createBundle(nonUniqueBundleName, NOT_USED, INTERNAL_SERVER_ERROR);
        // We create a bundle with an available name and then rename it to an unavailable name.
        String bundleId = createBundle("bundle-2-name", NOT_USED, OK).get();
        updateBundle(bundleId, nonUniqueBundleName, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testAppNameUniqueSqlConstraint() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        // Double app creation with the same name.
        String nonUniqueAppName = "app-1-name";
        createApp(bundleId, nonUniqueAppName, NOT_USED, OK);
        createApp(bundleId, nonUniqueAppName, NOT_USED, INTERNAL_SERVER_ERROR);
        // We create an app with an available name and then rename it to an unavailable name.
        String appId = createApp(bundleId, "app-2-name", NOT_USED, OK).get();
        updateApp(bundleId, appId, nonUniqueAppName, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testEventTypeNameUniqueSqlConstraint() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String appId = createApp(bundleId, "app-name", "App", OK).get();
        // Double event type creation with the same name.
        String nonUniqueEventTypeName = "event-type-name";
        createEventType(appId, nonUniqueEventTypeName, NOT_USED, NOT_USED, OK);
        createEventType(appId, nonUniqueEventTypeName, NOT_USED, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testGetUnknownBundle() {
        String unknownBundleId = UUID.randomUUID().toString();
        getBundle(unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testGetUnknownApp() {
        String unknownAppId = UUID.randomUUID().toString();
        getApp(unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testUpdateNullBundle() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        updateBundle(bundleId, null, BAD_REQUEST);
    }

    @Test
    void testUpdateNullApp() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String appId = createApp(bundleId, "app-name", "App", OK).get();
        updateApp(appId, null, BAD_REQUEST);
    }

    @Test
    void testUpdateUnknownBundle() {
        String unknownBundleId = UUID.randomUUID().toString();
        updateBundle(unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testUpdateUnknownApp() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String unknownAppId = UUID.randomUUID().toString();
        updateApp(bundleId, unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testAddAppToUnknownBundle() {
        String unknownBundleId = UUID.randomUUID().toString();
        createApp(unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testAddEventTypeToUnknownApp() {
        String unknownAppId = UUID.randomUUID().toString();
        createEventType(unknownAppId, NOT_USED, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testGetAppsFromUnknownBundle() {
        String unknownBundleId = UUID.randomUUID().toString();
        getApps(unknownBundleId, NOT_FOUND, 0);
    }

    @Test
    void testGetEventTypesFromUnknownApp() {
        String unknownAppId = UUID.randomUUID().toString();
        getEventTypes(unknownAppId, NOT_FOUND, 0);
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteBundle() {
        // First, we create two bundles with different names. Only the second one will be used after that.
        createBundle("bundle-1-name", "Bundle 1", OK).get();
        String bundleId = createBundle("bundle-2-name", "Bundle 2", OK).get();

        // Then the bundle update API is tested.
        updateBundle(bundleId, "bundle-2-new-name", "Bundle 2 new display name", OK);

        // Same for the bundle delete API.
        deleteBundle(bundleId, true);

        // Now that we deleted the bundle, all the following APIs calls should "fail".
        deleteBundle(bundleId, false);
        getBundle(bundleId, NOT_USED, NOT_USED, NOT_FOUND);
        updateBundle(bundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteApp() {
        // We need to persist a bundle for this test.
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();

        // First, we create two apps with different names. Only the second one will be used after that.
        createApp(bundleId, "app-1-name", "App 1", OK);
        String appId = createApp(bundleId, "app-2-name", "App 2", OK).get();

        // The bundle should contain two apps.
        getApps(bundleId, OK, 2);

        // Let's test the app update API.
        updateApp(bundleId, appId, "app-2-new-name", "App 2 new display name", OK);

        // Same for the app delete API.
        deleteApp(appId, true);

        // Now that we deleted the app, all the following APIs calls should "fail".
        deleteApp(appId, false);
        getApp(appId, NOT_USED, NOT_USED, NOT_FOUND);
        updateApp(bundleId, appId, NOT_USED, NOT_USED, NOT_FOUND);

        // The bundle should also contain one app now.
        getApps(bundleId, OK, 1);
    }

    @Test
    void testCreateAndGetAndDeleteEventType() {
        // We need to persist a bundle and an app for this test.
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String appId = createApp(bundleId, "app-name", "App", OK).get();

        // First, we create two event types with different names. Only the second one will be used after that.
        createEventType(appId, "event-type-1-name", "Event type 1", "Description 1", OK);
        String eventTypeId = createEventType(appId, "event-type-2-name", "Event type 2", "Description 2", OK).get();

        // The app should contain two event types.
        getEventTypes(appId, OK, 2);

        // Let's test the event type delete API.
        deleteEventType(eventTypeId, true);

        // Deleting the event type again should not work.
        deleteEventType(eventTypeId, false);

        // The app should also contain one event type now.
        getEventTypes(appId, OK, 1);
    }

    @Test
    void testSetDefaultBehaviorGroup() {
        // This test only verifies the SQL query execution. The real API test is in LifecycleITest.
        String notUsed = UUID.randomUUID().toString();
        setDefaultBehaviorGroup(notUsed, notUsed, NOT_FOUND);
    }
}
