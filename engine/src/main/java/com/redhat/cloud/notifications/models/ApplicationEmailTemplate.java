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
@Table(name = "application_email_template")
public class ApplicationEmailTemplate {

    @EmbeddedId
    private ApplicationEmailTemplateId id;

    @ManyToOne
    @MapsId("applicationId")
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne
    @JoinColumn(name = "subject_template_id")
    @NotNull
    private Template subjectTemplate;

    @ManyToOne
    @JoinColumn(name = "body_template_id")
    @NotNull
    private Template bodyTemplate;

    public ApplicationEmailTemplateId getId() {
        return id;
    }

    public void setId(ApplicationEmailTemplateId id) {
        this.id = id;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
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
        if (o instanceof ApplicationEmailTemplate) {
            ApplicationEmailTemplate other = (ApplicationEmailTemplate) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
