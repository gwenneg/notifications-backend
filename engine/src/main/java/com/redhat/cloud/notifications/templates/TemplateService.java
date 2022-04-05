package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateInstance;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class TemplateService {

    public static final String USE_TEMPLATES_FROM_DB_KEY = "notifications.use-templates-from-db";

    @ConfigProperty(name = USE_TEMPLATES_FROM_DB_KEY)
    boolean useTemplatesFromDb;

    @Inject
    Engine engine;

    @Inject
    Environment environment;

    @Inject
    DbTemplateLocator dbTemplateLocator;

    Engine dbEngine;

    @PostConstruct
    void postConstruct() {
        dbEngine = Engine.builder()
                .addDefaults()
                .addLocator(dbTemplateLocator)
                .build();
    }

    public TemplateInstance compileTemplate(String template, String name) {
        return getEngine().parse(template, null, name).instance();
    }

    private Engine getEngine() {
        return useTemplatesFromDb ? dbEngine : engine;
    }

    public String renderTemplate(User user, Action action, TemplateInstance templateInstance) {
        return templateInstance
                .data("action", action)
                .data("user", user)
                .data("environment", environment)
                .render();
    }
}
