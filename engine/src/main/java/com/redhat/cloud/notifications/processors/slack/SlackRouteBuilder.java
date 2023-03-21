package com.redhat.cloud.notifications.processors.slack;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.io.IOException;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class SlackRouteBuilder extends RouteBuilder {

    public static final String REST_PATH = API_INTERNAL + "/slack";
    public static final String SLACK_OUTGOING_ROUTE = "slack-outgoing";

    private static final String SLACK_DIRECT_ENDPOINT = "direct:slack";
    private static final String EXCEPTION_DIRECT_ENDPOINT = "direct:exception";
    private static final String ERROR_MSG = "Slack message sending failed [orgId=${exchangeProperty.orgId}, " +
            "historyId=${exchangeProperty.historyId}, webhookUrl=${exchangeProperty.webhookUrl}, " +
            "channel=${exchangeProperty.channel}]\n${exception.stacktrace}";

    @ConfigProperty(name = "notifications.slack.camel.maximum-redeliveries", defaultValue = "2")
    int maxRedeliveries;

    @ConfigProperty(name = "notifications.slack.camel.redelivery-delay", defaultValue = "1000")
    long redeliveryDelay;

    @ConfigProperty(name = "notifications.slack.camel.max-endpoint-cache-size", defaultValue = "100")
    int maxEndpointCacheSize;

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Override
    public void configure() {

        onException(IOException.class)
                .maximumRedeliveries(maxRedeliveries)
                .redeliveryDelay(redeliveryDelay)
                .retryAttemptedLogLevel(INFO)
                .to(EXCEPTION_DIRECT_ENDPOINT)
                .handled(true);

        onException(CamelExchangeException.class)
                .to(EXCEPTION_DIRECT_ENDPOINT);

        from(EXCEPTION_DIRECT_ENDPOINT)
                .routeId("exception-logging")
                .log(ERROR, ERROR_MSG);

        rest(REST_PATH)
                .post()
                .consumes("application/json")
                .routeId("slack-incoming")
                .to(SLACK_DIRECT_ENDPOINT);

        from(SLACK_DIRECT_ENDPOINT)
                .routeId(SLACK_OUTGOING_ROUTE)
                .process(slackNotificationProcessor)
                .toD("slack:${exchangeProperty.channel}?webhookUrl=${exchangeProperty.webhookUrl}", maxEndpointCacheSize);
    }
}
