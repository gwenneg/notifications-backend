package com.redhat.cloud.notifications.connector.microsoft.teams;

import com.redhat.cloud.notifications.connector.ConnectorConfig;

public class TeamsConnectorConfig implements ConnectorConfig {

    public static final String CONNECTOR_NAME = "teams";
    public static final String KAFKA_GROUP_ID = "notifications-connector-microsoft-teams";
    public static final String RETRY_COUNTER_NAME = "camel.teams.retry.counter";

    @Override
    public String getConnectorName() {
        return CONNECTOR_NAME;
    }

    @Override
    public String getKafkaGroupId() {
        return KAFKA_GROUP_ID;
    }

    @Override
    public String getRetryCounterName() {
        return RETRY_COUNTER_NAME;
    }
}
