package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.WEBHOOK_URL;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;

@ApplicationScoped
public class SlackRouteBuilder extends EngineToConnectorRouteBuilder {

    public static final String SLACK_ROUTE = "slack";

    @Override
    public void configureRoute() {

        from(direct(ENGINE_TO_CONNECTOR))
                .routeId(SLACK_ROUTE)
                .toD(slack("${exchangeProperty.channel}").webhookUrl("${exchangeProperty." + WEBHOOK_URL + "}"), maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty." + ID + "} sent successfully"))
                .to(direct(CONNECTOR_TO_ENGINE));
    }
}
