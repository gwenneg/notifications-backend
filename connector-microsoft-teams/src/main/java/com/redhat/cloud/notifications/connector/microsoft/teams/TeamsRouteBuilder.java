package com.redhat.cloud.notifications.connector.microsoft.teams;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.WEBHOOK_URL;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;

@ApplicationScoped
public class TeamsRouteBuilder extends EngineToConnectorRouteBuilder {

    public static final String TEAMS_ROUTE = "microsoft-teams";

    @Override
    public void configureRoute() {

        from(direct(ENGINE_TO_CONNECTOR))
                .routeId(TEAMS_ROUTE)
                .removeHeaders(CAMEL_HTTP_HEADERS_PATTERN)
                .setHeader(HTTP_METHOD, constant(POST))
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
                .toD("${exchangeProperty." + WEBHOOK_URL + "}", maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty." + ID + "} sent successfully"))
                .to(direct(CONNECTOR_TO_ENGINE));
    }
}
