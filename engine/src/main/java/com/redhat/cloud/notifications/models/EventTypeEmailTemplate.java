package com.redhat.cloud.notifications.models;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.util.Objects;

@Entity
@Table(name = "event_type_email_template")
public class EventTypeEmailTemplate {

    @EmbeddedId
    private EventTypeEmailTemplateId id;

    @ManyToOne
    @MapsId("eventTypeId")
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    @ManyToOne
    @JoinColumn(name = "subject_template_id")
    @NotNull
    private Template subjectTemplate;

    @ManyToOne
    @JoinColumn(name = "body_template_id")
    @NotNull
    private Template bodyTemplate;

    public EventTypeEmailTemplateId getId() {
        return id;
    }

    public void setId(EventTypeEmailTemplateId id) {
        this.id = id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Template getSubjectTemplate() {
        return subjectTemplate;
    }

    public void setSubjectTemplate(Template subjectTemplate) {
        this.subjectTemplate = subjectTemplate;
    }

    public Template getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(Template bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EventTypeEmailTemplate) {
            EventTypeEmailTemplate other = (EventTypeEmailTemplate) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
