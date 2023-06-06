package com.redhat.cloud.notifications.processors.camel;

public class SlackNotification extends CamelNotification {

    public String channel;

    @Override
    public String toString() {
        return "SlackNotification [orgId=" + orgId + ", webhookUrl=" + webhookUrl + ", channel=" + channel + "]";
    }
}
