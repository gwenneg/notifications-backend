package com.redhat.cloud.notifications.connector;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TYPE;

/*
 * This processor transforms an incoming notification, initially received as JSON data,
 * into a data structure that can be used by the Camel component to send a message.
 */
@ApplicationScoped
public class IncomingCloudEventMetadataProcessor implements Processor {

    public static final String CLOUD_EVENT_ID = "id";
    public static final String CLOUD_EVENT_TYPE = "type";
    public static final String CLOUD_EVENT_DATA = "data";

    @Inject
    ConnectorConfig connectorConfig;

    @Override
    public void process(Exchange exchange) throws Exception {

        // The incoming JSON data is parsed and its fields are used to build the outgoing data.
        Message in = exchange.getIn();

        JsonObject cloudEvent = new JsonObject(in.getBody(String.class));

        exchange.setProperty(ID, cloudEvent.getString(CLOUD_EVENT_ID));
        exchange.setProperty(TYPE, cloudEvent.getString(CLOUD_EVENT_TYPE));


        // This property will be used later to determine the invocation time.
        exchange.setProperty(START_TIME, System.currentTimeMillis());

        // Source of the Cloud Event returned to the Notifications engine.
        exchange.setProperty(RETURN_SOURCE, connectorConfig.getConnectorName());

        JsonObject data = cloudEvent.getJsonObject(CLOUD_EVENT_DATA);
        in.setBody(data);


        Log.debugf("Processing %s [connector=%s, historyId=%s]",
                cloudEvent, connectorConfig.getConnectorName(), exchange.getProperty(ID, String.class));

    }
}
