package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EventType;
import org.hibernate.StatelessSession;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventTypeRepository {

    @Inject
    StatelessSession statelessSession;

    public EventType getEventType(String bundleName, String applicationName, String eventTypeName) {
        String query = "FROM EventType e JOIN FETCH e.application a JOIN FETCH a.bundle b " +
                "WHERE e.name = :eventTypeName AND a.name = :applicationName AND b.name = :bundleName";
        return statelessSession.createQuery(query, EventType.class)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("eventTypeName", eventTypeName)
                .getSingleResult();
    }
}
