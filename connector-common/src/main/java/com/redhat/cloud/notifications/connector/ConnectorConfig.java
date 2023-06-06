package com.redhat.cloud.notifications.connector;

public interface ConnectorConfig {

    String getConnectorName();

    String getKafkaGroupId();

    String getRetryCounterName();
}
