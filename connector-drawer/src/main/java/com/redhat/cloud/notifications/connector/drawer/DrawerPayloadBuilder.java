package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntry;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntryPayload;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import java.time.ZonedDateTime;
import java.util.Set;

import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class DrawerPayloadBuilder implements Processor {

    public static final String CE_SPEC_VERSION = "1.0.2";
    public static final String CE_TYPE = "com.redhat.console.notifications.drawer";

    @Override
    public void process(Exchange exchange) {
        final DrawerEntryPayload entryPayloadModel = exchange.getProperty(ExchangeProperty.DRAWER_ENTRY_PAYLOAD, DrawerEntryPayload.class);
        final Set<String> recipients = exchange.getProperty(ExchangeProperty.RESOLVED_RECIPIENT_LIST, Set.class);
        JsonObject outgoingCloudEvent = buildDrawerPayload(entryPayloadModel, recipients);

        Message in = exchange.getIn();
        /*
         * The exchange may contain headers used by a connector to perform a call to an external service.
         * These headers shouldn't be leaked, especially if they contain authorization data, so we're
         * removing all of them for security purposes.
         */
        in.removeHeaders("*");
        in.setHeader("content-type", "application/cloudevents+json; charset=UTF-8");

        in.setBody(outgoingCloudEvent.encode());
    }

    public static JsonObject buildDrawerPayload(final DrawerEntryPayload entryPayloadModel, final Set<String> recipients) {
        DrawerEntry drawerEntry = new DrawerEntry();
        drawerEntry.setPayload(entryPayloadModel);
        drawerEntry.setUsernames(recipients);
        JsonObject myPayload = JsonObject.mapFrom(drawerEntry);

        JsonObject outgoingCloudEvent = new JsonObject();
        outgoingCloudEvent.put("type", CE_TYPE);
        outgoingCloudEvent.put("specversion", CE_SPEC_VERSION);
        outgoingCloudEvent.put("datacontenttype", "application/json");
        outgoingCloudEvent.put("source", "urn:redhat:source:notifications:drawer");
        outgoingCloudEvent.put("id", entryPayloadModel.getEventId());
        outgoingCloudEvent.put("time", ZonedDateTime.now(UTC).toString());
        outgoingCloudEvent.put("data", myPayload);
        return outgoingCloudEvent;
    }
}
