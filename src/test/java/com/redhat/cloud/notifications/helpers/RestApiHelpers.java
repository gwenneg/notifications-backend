package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Attributes;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.restassured.http.Header;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Optional;

import static com.redhat.cloud.notifications.TestConstants.API_INTEGRATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestConstants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.helpers.EntityHelpers.buildApp;
import static com.redhat.cloud.notifications.helpers.EntityHelpers.buildBehaviorGroup;
import static com.redhat.cloud.notifications.helpers.EntityHelpers.buildBundle;
import static com.redhat.cloud.notifications.helpers.EntityHelpers.buildEndpoint;
import static com.redhat.cloud.notifications.helpers.EntityHelpers.buildEventType;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.Family.familyOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class RestApiHelpers {

    /*
     * In the methods below, JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    public static final int OK = 200;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int INTERNAL_SERVER_ERROR = 500;

    private static final String INTERNAL_BASE_PATH = "/";

    public static Optional<String> createBundle(String name, String displayName, int expectedStatusCode) {
        Bundle bundle = buildBundle(name, displayName);
        return createBundle(bundle, expectedStatusCode);
    }

    public static Optional<String> createBundle(Bundle bundle, int expectedStatusCode) {
        String responseBody = given()
                .basePath(INTERNAL_BASE_PATH)
                .contentType(JSON)
                .body(Json.encode(bundle))
                .when().post("/internal/bundles")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonBundle = new JsonObject(responseBody);
            jsonBundle.mapTo(Bundle.class);
            assertNotNull(jsonBundle.getString("id"));
            assertEquals(bundle.getName(), jsonBundle.getString("name"));
            assertEquals(bundle.getDisplayName(), jsonBundle.getString("display_name"));
            assertNotNull(jsonBundle.getString("created"));

            getBundle(jsonBundle.getString("id"), bundle.getName(), bundle.getDisplayName(), OK);

            return Optional.of(jsonBundle.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void getBundle(String bundleId, String expectedName, String expectedDisplayName, int expectedStatusCode) {
        String responseBody = given()
                .basePath(INTERNAL_BASE_PATH)
                .pathParam("bundleId", bundleId)
                .when().get("/internal/bundles/{bundleId}")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonBundle = new JsonObject(responseBody);
            jsonBundle.mapTo(Bundle.class);
            assertEquals(bundleId, jsonBundle.getString("id"));
            assertEquals(expectedName, jsonBundle.getString("name"));
            assertEquals(expectedDisplayName, jsonBundle.getString("display_name"));
        }
    }

    public static void updateBundle(String bundleId, String name, String displayName, int expectedStatusCode) {
        Bundle bundle = buildBundle(name, displayName);
        updateBundle(bundleId, bundle, expectedStatusCode);
    }

    public static void updateBundle(String bundleId, Bundle bundle, int expectedStatusCode) {
        given()
                .basePath(INTERNAL_BASE_PATH)
                .contentType(JSON)
                .pathParam("bundleId", bundleId)
                .body(Json.encode(bundle))
                .when().put("/internal/bundles/{bundleId}")
                .then().statusCode(expectedStatusCode);

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            getBundle(bundleId, bundle.getName(), bundle.getDisplayName(), OK);
        }
    }

    public static void deleteBundle(String bundleId, boolean expectedResult) {
        Boolean result = given()
                .basePath(INTERNAL_BASE_PATH)
                .pathParam("bundleId", bundleId)
                .when().delete("/internal/bundles/{bundleId}")
                .then().statusCode(OK)
                .extract().body().as(Boolean.class);

        assertEquals(expectedResult, result);
    }

    public static Optional<String> createApp(String bundleId, String name, String displayName, int expectedStatusCode) {
        Application app = buildApp(bundleId, name, displayName);
        return createApp(app, expectedStatusCode);
    }

    public static Optional<String> createApp(Application app, int expectedStatusCode) {
        String responseBody = given()
                .basePath(INTERNAL_BASE_PATH)
                .contentType(JSON)
                .body(Json.encode(app))
                .when().post("/internal/applications")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonApp = new JsonObject(responseBody);
            jsonApp.mapTo(Application.class);
            assertNotNull(jsonApp.getString("id"));
            assertEquals(app.getName(), jsonApp.getString("name"));
            assertEquals(app.getDisplayName(), jsonApp.getString("display_name"));
            assertEquals(app.getBundleId().toString(), jsonApp.getString("bundle_id"));
            assertNotNull(jsonApp.getString("created"));

            getApp(jsonApp.getString("id"), app.getName(), app.getDisplayName(), OK);

            return Optional.of(jsonApp.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void getApp(String appId, String expectedName, String expectedDisplayName, int expectedStatusCode) {
        String responseBody = given()
                .basePath(INTERNAL_BASE_PATH)
                .pathParam("appId", appId)
                .when().get("/internal/applications/{appId}")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonApp = new JsonObject(responseBody);
            jsonApp.mapTo(Application.class);
            assertEquals(appId, jsonApp.getString("id"));
            assertEquals(expectedName, jsonApp.getString("name"));
            assertEquals(expectedDisplayName, jsonApp.getString("display_name"));
        }
    }

    public static void getApps(String bundleId, int expectedStatusCode, int expectedAppsCount) {
        String responseBody = given()
                .basePath(INTERNAL_BASE_PATH)
                .pathParam("bundleId", bundleId)
                .when().get("/internal/bundles/{bundleId}/applications")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonArray jsonApps = new JsonArray(responseBody);
            assertEquals(expectedAppsCount, jsonApps.size());
            if (expectedAppsCount > 0) {
                for (int i = 0; i < expectedAppsCount; i++) {
                    jsonApps.getJsonObject(i).mapTo(Application.class);
                }
            }
        }
    }

    public static void updateApp(String bundleId, String appId, String name, String displayName, int expectedStatusCode) {
        Application app = buildApp(bundleId, name, displayName);
        updateApp(appId, app, expectedStatusCode);
    }

    public static void updateApp(String appId, Application app, int expectedStatusCode) {
        given()
                .basePath(INTERNAL_BASE_PATH)
                .contentType(JSON)
                .pathParam("appId", appId)
                .body(Json.encode(app))
                .when().put("/internal/applications/{appId}")
                .then().statusCode(expectedStatusCode);

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            getApp(appId, app.getName(), app.getDisplayName(), OK);
        }
    }

    public static void deleteApp(String appId, boolean expectedResult) {
        Boolean result = given()
                .basePath(INTERNAL_BASE_PATH)
                .pathParam("appId", appId)
                .when().delete("/internal/applications/{appId}")
                .then().statusCode(OK)
                .extract().body().as(Boolean.class);

        assertEquals(expectedResult, result);
    }

    public static Optional<String> createBehaviorGroup(Header identityHeader, String bundleId, String displayName, int expectedStatusCode) {
        BehaviorGroup behaviorGroup = buildBehaviorGroup(bundleId, displayName);
        return createBehaviorGroup(identityHeader, behaviorGroup, expectedStatusCode);
    }

    public static Optional<String> createBehaviorGroup(Header identityHeader, BehaviorGroup behaviorGroup, int expectedStatusCode) {
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(behaviorGroup))
                .when().post("/notifications/behaviorGroups")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonBehaviorGroup = new JsonObject(responseBody);
            jsonBehaviorGroup.mapTo(BehaviorGroup.class);
            assertNotNull(jsonBehaviorGroup.getString("id"));
            assertNull(jsonBehaviorGroup.getString("account_id"));
            assertEquals(behaviorGroup.getDisplayName(), jsonBehaviorGroup.getString("display_name"));
            assertEquals(behaviorGroup.getBundleId().toString(), jsonBehaviorGroup.getString("bundle_id"));
            assertNotNull(jsonBehaviorGroup.getString("created"));

            return Optional.of(jsonBehaviorGroup.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void updateBehaviorGroup(Header identityHeader, String behaviorGroupId, String bundleId, String displayName, int expectedStatusCode) {
        BehaviorGroup behaviorGroup = buildBehaviorGroup(bundleId, displayName);
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroup, expectedStatusCode);
    }

    public static void updateBehaviorGroup(Header identityHeader, String behaviorGroupId, BehaviorGroup behaviorGroup, int expectedStatusCode) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(behaviorGroup))
                .pathParam("id", behaviorGroupId)
                .when().put("/notifications/behaviorGroups/{id}")
                .then().statusCode(expectedStatusCode);
    }

    public static void updateBehaviorGroupActions(Header identityHeader, String behaviorGroupId, String... endpointIds) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .body(Json.encode(Arrays.asList(endpointIds)))
                .when().put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
                .then().statusCode(200);
    }

    public static void setDefaultBehaviorGroup(String bundleId, String behaviorGroupId, int expectedStatusCode) {
        given()
                .basePath(INTERNAL_BASE_PATH)
                .pathParam("bundleId", bundleId)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .when().put("/internal/bundles/{bundleId}/behaviorGroups/{behaviorGroupId}/default")
                .then().statusCode(expectedStatusCode);
    }

    public static void getBehaviorGroups(Header identityHeader, String bundleId, int expectedStatusCode, int expectedBehaviorGroupsCount) {
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("bundleId", bundleId)
                .when().get("/notifications/bundles/{bundleId}/behaviorGroups")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonArray jsonBehaviorGroups = new JsonArray(responseBody);
            assertEquals(expectedBehaviorGroupsCount, jsonBehaviorGroups.size());
            if (expectedBehaviorGroupsCount > 0) {
                for (int i = 0; i < expectedBehaviorGroupsCount; i++) {
                    jsonBehaviorGroups.getJsonObject(i).mapTo(BehaviorGroup.class);
                }
            }
        }
    }

    public static void deleteBehaviorGroup(Header identityHeader, String behaviorGroupId, boolean expectedResult) {
        Boolean result = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("id", behaviorGroupId)
                .when().delete("/notifications/behaviorGroups/{id}")
                .then().statusCode(OK)
                .extract().body().as(Boolean.class);

        assertEquals(expectedResult, result);
    }

    public static Optional<String> createEventType(String name, String displayName, String description, String appId, int expectedStatusCode) {
        EventType eventType = buildEventType(name, displayName, description, appId);
        return createEventType(eventType, expectedStatusCode);
    }

    public static Optional<String> createEventType(EventType eventType, int expectedStatusCode) {
        String responseBody = given()
                .basePath(INTERNAL_BASE_PATH)
                .contentType(JSON)
                .body(Json.encode(eventType))
                .when().post("/internal/eventTypes")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonEventType = new JsonObject(responseBody);
            jsonEventType.mapTo(EventType.class);
            assertNotNull(jsonEventType.getString("id"));
            assertEquals(eventType.getName(), jsonEventType.getString("name"));
            assertEquals(eventType.getDisplayName(), jsonEventType.getString("display_name"));
            assertEquals(eventType.getDescription(), jsonEventType.getString("description"));
            assertEquals(eventType.getApplicationId().toString(), jsonEventType.getString("application_id"));

            return Optional.of(jsonEventType.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void getEventTypes(String appId, int expectedStatusCode, int expectedEventTypesCount) {
        String responseBody = given()
                .basePath(INTERNAL_BASE_PATH)
                .pathParam("appId", appId)
                .when().get("/internal/applications/{appId}/eventTypes")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonArray jsonEventTypes = new JsonArray(responseBody);
            assertEquals(expectedEventTypesCount, jsonEventTypes.size());
            if (expectedEventTypesCount > 0) {
                for (int i = 0; i < expectedEventTypesCount; i++) {
                    jsonEventTypes.getJsonObject(0).mapTo(EventType.class);
                }
            }
        }
    }

    public static void addEventTypeBehaviorGroup(Header identityHeader, String eventTypeId, String behaviorGroupId) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .when().put("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
                .then().statusCode(200);
    }

    public static void checkEventTypeBehaviorGroups(Header identityHeader, String eventTypeId, String... expectedBehaviorGroupIds) {
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .when().get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then().statusCode(200)
                .extract().asString();

        JsonArray jsonBehaviorGroups = new JsonArray(responseBody);
        assertEquals(expectedBehaviorGroupIds.length, jsonBehaviorGroups.size());

        for (String behaviorGroupId : expectedBehaviorGroupIds) {
            if (!responseBody.contains(behaviorGroupId)) {
                fail("One of the expected behavior groups is not linked to the event type");
            }
        }
    }

    public static void muteEventType(Header identityHeader, String eventTypeId) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .when().delete("/notifications/eventTypes/{eventTypeId}/mute")
                .then().statusCode(200);

        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .when().get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then().statusCode(200)
                .extract().asString();

        JsonArray jsonBehaviorGroups = new JsonArray(responseBody);
        assertEquals(0, jsonBehaviorGroups.size());
    }

    public static void deleteEventType(String eventTypeId, boolean expectedResult) {
        Boolean result = given()
                .basePath(INTERNAL_BASE_PATH)
                .pathParam("eventTypeId", eventTypeId)
                .when().delete("/internal/eventTypes/{eventTypeId}")
                .then().statusCode(OK)
                .extract().body().as(Boolean.class);

        assertEquals(expectedResult, result);
    }

    public static Optional<String> createEndpoint(Header identityHeader, EndpointType type, String name, String description, boolean enabled, Attributes properties, int expectedStatusCode) {
        Endpoint endpoint = buildEndpoint(type, name, description, enabled, properties);
        return createEndpoint(identityHeader, endpoint, expectedStatusCode);
    }

    public static Optional<String> createEndpoint(Header identityHeader, Endpoint endpoint, int expectedStatusCode) {
        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .when().post("/endpoints")
                .then().statusCode(expectedStatusCode)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonEndpoint = new JsonObject(responseBody);
            jsonEndpoint.mapTo(Endpoint.class);
            assertNotNull(jsonEndpoint.getString("id"));
            assertNull(jsonEndpoint.getString("account_id"));
            assertEquals(endpoint.getName(), jsonEndpoint.getString("name"));
            assertEquals(endpoint.getDescription(), jsonEndpoint.getString("description"));
            assertEquals(endpoint.isEnabled(), jsonEndpoint.getBoolean("enabled"));
            assertEquals(endpoint.getType().name().toLowerCase(), jsonEndpoint.getString("type"));

            if (endpoint.getProperties() instanceof WebhookAttributes) {
                WebhookAttributes properties = (WebhookAttributes) endpoint.getProperties();

                JsonObject jsonWebhookAttributes = jsonEndpoint.getJsonObject("properties");
                jsonWebhookAttributes.mapTo(WebhookAttributes.class);
                assertEquals(properties.getMethod().name(), jsonWebhookAttributes.getString("method"));
                assertEquals(properties.isDisableSSLVerification(), jsonWebhookAttributes.getBoolean("disable_ssl_verification"));
                assertEquals(properties.getSecretToken(), jsonWebhookAttributes.getString("secret_token"));
                assertEquals(properties.getUrl(), jsonWebhookAttributes.getString("url"));
            }

            return Optional.of(jsonEndpoint.getString("id"));
        } else {
            return Optional.empty();
        }
    }
}
