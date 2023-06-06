package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ConnectorConfig;

public class SlackConnectorConfig implements ConnectorConfig {

    public static final String CONNECTOR_NAME = "slack";
    public static final String KAFKA_GROUP_ID = "notifications-connector-slack";
    public static final String RETRY_COUNTER_NAME = "camel.slack.retry.counter";

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
