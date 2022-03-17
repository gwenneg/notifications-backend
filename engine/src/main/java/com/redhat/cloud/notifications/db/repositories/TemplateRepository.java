package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.ApplicationEmailTemplate;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.EventTypeEmailTemplate;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TemplateRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    public boolean isSupported(String bundleName, String appName) {
        String hql = "SELECT COUNT(*) FROM ApplicationEmailTemplate WHERE application.bundle.name = :bundleName " +
                "AND application.name = :appName";
        return statelessSessionFactory.getCurrentSession().createQuery(hql, Long.class)
                .setParameter("bundleName", bundleName)
                .setParameter("appName", appName)
                .getSingleResult() > 0;
    }

    public Optional<EventTypeEmailTemplate> findEventTypeEmailTemplate(UUID eventTypeId, EmailSubscriptionType subscriptionType) {
        String hql = "FROM EventTypeEmailTemplate WHERE eventType.id = :eventTypeId " +
                "AND id.subscriptionType = :subscriptionType";
        try {
            EventTypeEmailTemplate emailTemplate = statelessSessionFactory.getCurrentSession().createQuery(hql, EventTypeEmailTemplate.class)
                    .setParameter("eventTypeId", eventTypeId)
                    .setParameter("subscriptionType", subscriptionType)
                    .getSingleResult();
            return Optional.of(emailTemplate);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<ApplicationEmailTemplate> findApplicationEmailTemplate(String bundleName, String appName, EmailSubscriptionType subscriptionType) {
        String hql = "FROM ApplicationEmailTemplate WHERE application.bundle.name = :bundleName " +
                "AND application.name = :appName AND id.subscriptionType = :subscriptionType";
        try {
            ApplicationEmailTemplate emailTemplate = statelessSessionFactory.getCurrentSession().createQuery(hql, ApplicationEmailTemplate.class)
                    .setParameter("bundleName", bundleName)
                    .setParameter("appName", appName)
                    .setParameter("subscriptionType", subscriptionType)
                    .getSingleResult();
            return Optional.of(emailTemplate);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
