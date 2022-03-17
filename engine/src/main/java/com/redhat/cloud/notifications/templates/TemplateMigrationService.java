package com.redhat.cloud.notifications.templates;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;

@ApplicationScoped
public class TemplateMigrationService {

    @Inject
    EntityManager entityManager;

    public void migrate() {
    }
}
