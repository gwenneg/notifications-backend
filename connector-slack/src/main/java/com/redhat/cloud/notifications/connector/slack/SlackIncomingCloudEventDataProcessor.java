package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.IncomingCloudEventDataProcessor;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.WEBHOOK_URL;

@ApplicationScoped
public class SlackIncomingCloudEventDataProcessor extends IncomingCloudEventDataProcessor {

    @Override
    public void process(Exchange exchange) {

        Message in = exchange.getIn();

        JsonObject cloudEventData = in.getBody(JsonObject.class);

        exchange.setProperty(ORG_ID, cloudEventData.getString("orgId"));
        exchange.setProperty(WEBHOOK_URL, cloudEventData.getString("webhookUrl"));
        exchange.setProperty("channel", cloudEventData.getString("channel"));

        in.setBody(cloudEventData.getString("message"));
    }
}
