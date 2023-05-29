package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.CamelRoutesTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;

import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_TEAMS_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.camel.teams.TeamsRouteBuilder.TEAMS_ROUTE;

@QuarkusTest
public class TeamsRoutesTest extends CamelRoutesTest {

    @BeforeEach
    void beforeTest() {
        mockPath = "/camel/teams";
        mockPathKo = "/camel/teams_ko";
        mockPathRetries = "/camel/teams_retries";
        routeEndpoint = "https://foo.com";
        mockRouteEndpoint = "mock:https:foo.com";
        camelIncomingRouteName = TEAMS_ROUTE;
        retryCounterName = CAMEL_TEAMS_RETRY_COUNTER;
    }
}
