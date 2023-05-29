package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.camel.CamelNotification;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;

import javax.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class SlackProcessor extends CamelProcessor {

    @Override
    protected String getIntegrationName() {
        return "Slack";
    }

    @Override
    protected String getIntegrationType() {
        return "slack";
    }

    @Override
    protected CamelNotification getCamelNotification(Event event, Endpoint endpoint) {
        String message = buildNotificationMessage(event);
        CamelProperties properties = endpoint.getProperties(CamelProperties.class);

        SlackNotification notification = new SlackNotification();
        notification.orgId = endpoint.getOrgId();
        notification.webhookUrl = properties.getUrl();
        notification.channel = properties.getExtras().get("channel");
        notification.message = message;
        return notification;
    }
}
