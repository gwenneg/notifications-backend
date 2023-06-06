package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.WEBHOOK_URL;

@DefaultBean
@ApplicationScoped
public class IncomingCloudEventDataProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();

        JsonObject cloudEventData = in.getBody(JsonObject.class);

        exchange.setProperty(ORG_ID, cloudEventData.getString("orgId"));
        exchange.setProperty(WEBHOOK_URL, cloudEventData.getString("webhookUrl"));

        in.setBody(cloudEventData.getString("message"));
    }
}
