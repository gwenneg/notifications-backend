package com.redhat.cloud.notifications.connector;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Inject;

import static org.apache.camel.LoggingLevel.INFO;

public abstract class EngineToConnectorRouteBuilder extends EndpointRouteBuilder {

    public static final String ENGINE_TO_CONNECTOR = "engine-to-connector";

    @ConfigProperty(name = "notifications.connector.max-endpoint-cache-size", defaultValue = "100")
    protected int maxEndpointCacheSize;

    @ConfigProperty(name = "mp.messaging.tocamel.topic")
    String toCamelTopic;

    @ConfigProperty(name = "notifications.camel.maximum-redeliveries", defaultValue = "2")
    int maxRedeliveries;

    @ConfigProperty(name = "notifications.camel.redelivery-delay", defaultValue = "1000")
    long redeliveryDelay;

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    IncomingCloudEventFilter incomingCloudEventFilter;

    @Inject
    IncomingCloudEventMetadataProcessor incomingCloudEventMetadataProcessor;

    @Inject
    IncomingCloudEventDataProcessor incomingCloudEventDataProcessor;

    @Inject
    RedeliveryCounterProcessor redeliveryCounterProcessor;

    @Inject
    ExceptionProcessor exceptionProcessor;

    @Inject
    RedeliveryPredicate redeliveryPredicate;

    @Override
    public void configure() {

        from(kafka(toCamelTopic).groupId(connectorConfig.getKafkaGroupId()))
                .routeId(ENGINE_TO_CONNECTOR)
                .filter(incomingCloudEventFilter)
                .log(INFO, "Received ${body}")
                .process(incomingCloudEventMetadataProcessor)
                .process(incomingCloudEventDataProcessor)
                .to(direct(ENGINE_TO_CONNECTOR));

        onException(Throwable.class)
                .onWhen(redeliveryPredicate::matches)
                .handled(true)
                .maximumRedeliveries(maxRedeliveries)
                .redeliveryDelay(redeliveryDelay)
                .retryAttemptedLogLevel(INFO)
                .onRedelivery(redeliveryCounterProcessor)
                .process(exceptionProcessor);

        onException(Throwable.class)
                .onWhen(redeliveryPredicate::doesNotMatch)
                .handled(true)
                .process(exceptionProcessor);

        configureRoute();
    }

    public abstract void configureRoute();
}
