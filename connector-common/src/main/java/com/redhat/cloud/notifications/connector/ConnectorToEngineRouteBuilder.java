package com.redhat.cloud.notifications.connector;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static org.apache.camel.LoggingLevel.DEBUG;

@ApplicationScoped
public class ConnectorToEngineRouteBuilder extends EndpointRouteBuilder {

    public static final String CONNECTOR_TO_ENGINE = "connector-to-engine";

    @ConfigProperty(name = "mp.messaging.fromcamel.topic")
    String kafkaTopic;

    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    @Override
    public void configure() {
        from(direct(CONNECTOR_TO_ENGINE))
                .routeId(CONNECTOR_TO_ENGINE)
                .process(outgoingCloudEventBuilder)
                .log(DEBUG, "") // TODO
                .to(kafka(kafkaTopic));
    }
}
