package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.CamelProcessor;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TeamsProcessor extends CamelProcessor {

    @Override
    protected String getIntegrationName() {
        return "Teams";
    }

    @Override
    protected String getIntegrationType() {
        return "teams";
    }
}
