package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.ConnectorConfig;

public class GoogleChatConnectorConfig implements ConnectorConfig {

    public static final String CONNECTOR_NAME = "google_chat";
    public static final String KAFKA_GROUP_ID = "notifications-connector-google-chat";
    public static final String RETRY_COUNTER_NAME = "camel.google.chat.retry.counter";

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
