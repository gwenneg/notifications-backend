package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.CounterAssertionHelper;
import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.helpers.RestApiHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess;
import static com.redhat.cloud.notifications.TestConstants.API_INTEGRATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestConstants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_ENDPOINTS_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_MESSAGES_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.helpers.EntityHelpers.buildWebhookAttributes;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.OK;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.addEventTypeBehaviorGroup;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.checkEventTypeBehaviorGroups;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.createApp;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.createBehaviorGroup;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.createBundle;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.createEndpoint;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.createEventType;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.deleteBundle;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.muteEventType;
import static com.redhat.cloud.notifications.helpers.RestApiHelpers.updateBehaviorGroupActions;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpResponse.response;

// TODO [BG Phase 2] Remove '_BG_' from the class name
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class Lifecycle_BG_ITest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    private static final String APP_NAME = "policies-lifecycle-test";
    private static final String BUNDLE_NAME = "my-bundle";
    private static final String EVENT_TYPE_NAME = "all";
    private static final String INTERNAL_BASE_PATH = "/";
    private static final String WEBHOOK_MOCK_PATH = "/test/lifecycle";
    private static final String SECRET_TOKEN = "super-secret-token";

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Inject
    CounterAssertionHelper counterAssertionHelper;

    private Header initRbacMock(String tenant, String username, MockServerClientConfig.RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createIdentityHeader(identityHeaderValue);
    }

    @Test
    void test() throws IOException, InterruptedException {
        // Identity header used for all public APIs calls. Internal APIs calls don't need that.
        Header identityHeader = initRbacMock("tenant", "user", RbacAccess.FULL_ACCESS);

        // First, we need a bundle, an app and an event type. Let's create them!
        String bundleId = createBundle(BUNDLE_NAME, "A bundle", OK).get();
        String appId = createApp(bundleId, APP_NAME, "The best app in the life", OK).get();
        String eventTypeId = createEventType(appId, EVENT_TYPE_NAME, "Policies will take care of the rules", "Policies is super cool, you should use it", OK).get();
        checkAllEventTypes(identityHeader);

        /*
         * Then, we'll create two behavior groups which will be part of the bundle created above. One will be the
         * default behavior group, the other one will be a custom behavior group.
         */
        String defaultBehaviorGroupId = createBehaviorGroup(identityHeader, bundleId, "Default behavior group", OK).get();
        String customBehaviorGroupId = createBehaviorGroup(identityHeader, bundleId, "Custom behavior group", OK).get();
        setDefaultBehaviorGroup(identityHeader, bundleId, defaultBehaviorGroupId);

        // We need actions for our behavior groups.
        String endpointId1 = createWebhookEndpoint(identityHeader, "positive feeling", "needle in the haystack", SECRET_TOKEN);
        String endpointId2 = createWebhookEndpoint(identityHeader, "negative feeling", "I feel like dying", "wrong-secret-token");
        checkEndpoints(identityHeader, endpointId1, endpointId2);

        // The actions must be added to the behavior groups.
        updateBehaviorGroupActions(identityHeader, defaultBehaviorGroupId, endpointId1);
        // Adding the same action twice must not raise an exception.
        updateBehaviorGroupActions(identityHeader, defaultBehaviorGroupId, endpointId1, endpointId2);

        /*
         * Let's push a first message! It should not trigger any webhook call since we didn't link the event type with
         * any behavior group.
         */
        pushMessage(0);

        /*
         * Now we'll link the event type with the default behavior group.
         * Pushing a new message should trigger one webhook call.
         */
        addEventTypeBehaviorGroup(identityHeader, eventTypeId, defaultBehaviorGroupId);
        // Adding the same behavior group twice must not raise an exception.
        addEventTypeBehaviorGroup(identityHeader, eventTypeId, defaultBehaviorGroupId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, defaultBehaviorGroupId);
        // pushMessage(1); TODO [BG Phase 2] Uncomment (it does not work here because EndpointProcessor does not use behavior groups)

        /*
         * Let's also link the same event type with the custom behavior group.
         * Pushing a new message should trigger two webhook calls.
         */
        addEventTypeBehaviorGroup(identityHeader, eventTypeId, customBehaviorGroupId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, defaultBehaviorGroupId, customBehaviorGroupId);
        // pushMessage(2); TODO [BG Phase 2] Uncomment (it does not work here because EndpointProcessor does not use behavior groups)

        /*
         * What happens if we mute the event type?
         * Pushing a new message should not trigger any webhook call.
         */
        muteEventType(identityHeader, eventTypeId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId);
        // pushMessage(0); TODO [BG Phase 2] Uncomment (it does not work here because EndpointProcessor does not use behavior groups)

        // Enough messages! Now let's check the history of both endpoints.
        /*
        TODO [BG Phase 2] Uncomment (it does not work here because EndpointProcessor does not use behavior groups)
        checkEndpointHistory(identityHeader, endpointId1, 2, true, 200);
        checkEndpointHistory(identityHeader, endpointId2, 1, false, 400);
         */

        /*
         * We'll finish with a bundle removal.
         * Why would we do that here? I don't really know, it was there before the big test refactor... :)
         */
        deleteBundle(bundleId, true);
    }

    private void checkAllEventTypes(Header identityHeader) {
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonArray jsonEventTypes = new JsonArray(responseBody);
        assertEquals(2, jsonEventTypes.size()); // One from the current test, one from the default DB records.
    }

    private void setDefaultBehaviorGroup(Header identityHeader, String bundleId, String behaviorGroupId) {
        RestApiHelpers.setDefaultBehaviorGroup(bundleId, behaviorGroupId, OK);

        // Now let's verify which behavior group is marked as default in the database.
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("bundleId", bundleId)
                .when()
                .get("/notifications/bundles/{bundleId}/behaviorGroups")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonArray jsonBehaviorGroups = new JsonArray(responseBody);
        assertEquals(2, jsonBehaviorGroups.size());
        for (int i = 0; i < jsonBehaviorGroups.size(); i++) {
            JsonObject jsonBehaviorGroup = jsonBehaviorGroups.getJsonObject(i);
            jsonBehaviorGroup.mapTo(BehaviorGroup.class);
            if (jsonBehaviorGroup.getString("id").equals(behaviorGroupId)) {
                assertTrue(jsonBehaviorGroup.getBoolean("default_behavior"));
            } else {
                assertFalse(jsonBehaviorGroup.getBoolean("default_behavior"));
            }
        }
    }

    private String createWebhookEndpoint(Header identityHeader, String name, String description, String secretToken) {
        WebhookAttributes webAttr = buildWebhookAttributes(HttpType.POST, true, secretToken, "http://" + mockServerConfig.getRunningAddress() +  WEBHOOK_MOCK_PATH);
        return createEndpoint(identityHeader, EndpointType.WEBHOOK, name, description, true, webAttr, OK).get();
    }

    private void checkEndpoints(Header identityHeader, String... expectedEndpointIds) {
        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .get("/endpoints")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonArray jsonEndpoints = new JsonObject(responseBody).getJsonArray("data");
        assertEquals(expectedEndpointIds.length, jsonEndpoints.size());
        jsonEndpoints.getJsonObject(0).mapTo(Endpoint.class);
        jsonEndpoints.getJsonObject(0).getJsonObject("properties").mapTo(WebhookAttributes.class);

        for (String endpointId : expectedEndpointIds) {
            if (!responseBody.contains(endpointId)) {
                fail("One of the expected endpoint could not be found in the database");
            }
        }
    }

    /*
     * Pushes a single message to the 'ingress' channel.
     * Depending on the event type, behavior groups and endpoints configuration, it will trigger zero or more webhook calls.
     */
    private void pushMessage(int expectedWebhookCalls) throws IOException, InterruptedException {
        counterAssertionHelper.saveCounterValuesBeforeTest(REJECTED_COUNTER_NAME, PROCESSED_MESSAGES_COUNTER_NAME, PROCESSED_ENDPOINTS_COUNTER_NAME);

        CountDownLatch requestsCounter = new CountDownLatch(expectedWebhookCalls);
        HttpRequest expectedRequestPattern = null;

        if (expectedWebhookCalls > 0) {
            expectedRequestPattern = setupWebhookMock(requestsCounter);
        }

        emitMockedIngressAction();

        if (expectedWebhookCalls > 0) {
            if (!requestsCounter.await(5, TimeUnit.SECONDS)) {
                fail("HttpServer never received the requests");
                HttpRequest[] httpRequests = mockServerConfig.getMockServerClient().retrieveRecordedRequests(expectedRequestPattern);
                assertEquals(2, httpRequests.length);
                // Verify calls were correct, sort first?
            }
            mockServerConfig.getMockServerClient().clear(expectedRequestPattern);
        }

        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 0);
        counterAssertionHelper.assertIncrement(PROCESSED_MESSAGES_COUNTER_NAME, 1);
        counterAssertionHelper.assertIncrement(PROCESSED_ENDPOINTS_COUNTER_NAME, expectedWebhookCalls);
        counterAssertionHelper.clear();
    }

    private void emitMockedIngressAction() throws IOException {
        Action action = new Action();
        action.setAccountId("tenant");
        action.setBundle(BUNDLE_NAME);
        action.setApplication(APP_NAME);
        action.setEventType(EVENT_TYPE_NAME);
        action.setTimestamp(LocalDateTime.now());
        action.setContext(Map.of());
        action.setEvents(List.of(
                Event.newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of())
                        .build()
        ));

        String serializedAction = serializeAction(action);
        inMemoryConnector.source("ingress").send(serializedAction);
    }

    private HttpRequest setupWebhookMock(CountDownLatch requestsCounter) {
        HttpRequest expectedRequestPattern = new HttpRequest()
                .withPath(WEBHOOK_MOCK_PATH)
                .withMethod("POST");

        mockServerConfig.getMockServerClient()
                .withSecure(false)
                .when(expectedRequestPattern)
                .respond(request -> {
                    requestsCounter.countDown();
                    List<String> header = request.getHeader("X-Insight-Token");
                    if (header != null && header.size() == 1 && SECRET_TOKEN.equals(header.get(0))) {
                        return response().withStatusCode(200)
                                .withBody("Success");
                    } else {
                        return response().withStatusCode(400)
                                .withBody("{ \"message\": \"Time is running out\" }");
                    }
                });

        return expectedRequestPattern;
    }

    private void checkEndpointHistory(Header identityHeader, String endpointId, int expectedHistoryEntries, boolean expectedInvocationResult, int expectedHttpStatus) {
        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("endpointId", endpointId)
                .when()
                .get("/endpoints/{endpointId}/history")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonArray jsonEndpointHistory = new JsonArray(responseBody);
        assertEquals(expectedHistoryEntries, jsonEndpointHistory.size());

        for (int i = 0; i < jsonEndpointHistory.size(); i++) {
            JsonObject jsonNotificationHistory = jsonEndpointHistory.getJsonObject(i);
            jsonNotificationHistory.mapTo(NotificationHistory.class);
            assertEquals(expectedInvocationResult, jsonNotificationHistory.getBoolean("invocationResult"));

            if (!expectedInvocationResult) {
                responseBody = given()
                        .basePath(API_INTEGRATIONS_V_1_0)
                        .header(identityHeader)
                        .pathParam("endpointId", endpointId)
                        .pathParam("historyId", jsonNotificationHistory.getString("id"))
                        .when()
                        .get("/endpoints/{endpointId}/history/{historyId}/details")
                        .then()
                        .statusCode(200)
                        .extract().body().asString();

                JsonObject jsonDetails = new JsonObject(responseBody);
                assertFalse(jsonDetails.isEmpty());
                assertEquals(expectedHttpStatus, jsonDetails.getInteger("code"));
                assertNotNull(jsonDetails.getString("url"));
                assertNotNull(jsonDetails.getString("method"));
            }
        }
    }
}
