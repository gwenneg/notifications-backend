package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.processors.camel.CamelRouteBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.processors.camel.ReturnRouteBuilder.RETURN_ROUTE_NAME;

@ApplicationScoped
public class SlackRouteBuilder extends CamelRouteBuilder {

    public static final String SLACK_ROUTE = "slack";

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Override
    public void configure() {

        configureCommonExceptionHandler();

        from(kafka(toCamelTopic).groupId(groupId))
                .routeId(SLACK_ROUTE)
                .filter(incomingCloudEventFilter)
                .process(slackNotificationProcessor)
                .toD(slack("${exchangeProperty.channel}").webhookUrl("${exchangeProperty.webhookUrl}"), maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty.historyId} sent successfully"))
                .to(direct(RETURN_ROUTE_NAME));
    }
}
