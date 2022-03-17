package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.db.converters.EmailSubscriptionTypeConverter;

import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EventTypeEmailTemplateId implements Serializable {

    @NotNull
    public UUID eventTypeId;

    @NotNull
    @Size(max = 50)
    @Convert(converter = EmailSubscriptionTypeConverter.class)
    public EmailSubscriptionType subscriptionType;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EventTypeEmailTemplateId) {
            EventTypeEmailTemplateId other = (EventTypeEmailTemplateId) o;
            return Objects.equals(eventTypeId, other.eventTypeId) &&
                    Objects.equals(subscriptionType, other.subscriptionType);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTypeId, subscriptionType);
    }
}
