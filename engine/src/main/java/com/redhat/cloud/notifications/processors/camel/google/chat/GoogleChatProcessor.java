package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GoogleChatProcessor extends CamelProcessor {

    protected String getIntegrationName() {
        return "Google Chat";
    }

    protected String getIntegrationType() {
        return "google_chat";
    }
}
