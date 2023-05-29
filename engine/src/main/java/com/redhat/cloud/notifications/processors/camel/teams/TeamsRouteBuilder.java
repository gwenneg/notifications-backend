package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.CamelRouteBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.processors.camel.ReturnRouteBuilder.RETURN_ROUTE_NAME;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;

@ApplicationScoped
public class TeamsRouteBuilder extends CamelRouteBuilder {

    public static final String TEAMS_ROUTE = "teams";

    @Inject
    TeamsNotificationProcessor teamsNotificationProcessor;

    @Override
    public void configure() {

        configureCommonExceptionHandler();

        from(kafka(toCamelTopic).groupId(groupId))
                .routeId(TEAMS_ROUTE)
                .filter(incomingCloudEventFilter)
                .process(teamsNotificationProcessor)
                .removeHeaders(CAMEL_HTTP_HEADERS_PATTERN)
                .setHeader(HTTP_METHOD, constant(POST))
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
                .toD("${exchangeProperty.webhookUrl}", maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty.historyId} sent successfully"))
                .to(direct(RETURN_ROUTE_NAME));
    }
}
