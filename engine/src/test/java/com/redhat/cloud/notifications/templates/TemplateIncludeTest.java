package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.qute.TemplateException;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import static com.redhat.cloud.notifications.templates.TemplateService.USE_TEMPLATES_FROM_DB_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TemplateIncludeTest {

    @Inject
    EntityManager entityManager;

    @Inject
    TemplateService templateService;

    @Test
    void testIncludeExistingTemplate() {
        System.setProperty(USE_TEMPLATES_FROM_DB_KEY, "true");

        Template outerTemplate = createTemplate("outer-template", "Hello, {#include inner-template /}");
        createTemplate("inner-template", "World!");

        assertEquals("Hello, World!", templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render());

        System.clearProperty(USE_TEMPLATES_FROM_DB_KEY);
    }

    @Test
    void testIncludeUnknownTemplate() {
        System.setProperty(USE_TEMPLATES_FROM_DB_KEY, "true");

        Template outerTemplate = createTemplate("other-outer-template", "Hello, {#include unknown-inner-template /}");

        TemplateException e = assertThrows(TemplateException.class, () -> {
            templateService.compileTemplate(outerTemplate.getData(), outerTemplate.getName()).render();
        });
        assertEquals("Template not found: unknown-inner-template", e.getMessage());

        System.clearProperty(USE_TEMPLATES_FROM_DB_KEY);
    }

    @Transactional
    Template createTemplate(String name, String data) {
        Template template = new Template();
        template.setName(name);
        template.setData(data);
        entityManager.persist(template);
        return template;
    }
}
