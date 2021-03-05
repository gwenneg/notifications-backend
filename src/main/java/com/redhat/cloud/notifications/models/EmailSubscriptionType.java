package com.redhat.cloud.notifications.models;

import java.time.Duration;

public enum EmailSubscriptionType {
    INSTANT(null),
    DAILY(Duration.ofDays(1));

    private Duration duration;

    EmailSubscriptionType(Duration duration) {
        this.duration = duration;
    }

    public Duration getDuration() {
        return this.duration;
    }

    public static EmailSubscriptionType fromString(String value) {
        return EmailSubscriptionType.valueOf(value.toUpperCase());
    }
}
