package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Event;
import org.hibernate.StatelessSession;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventRepository {

    @Inject
    StatelessSession statelessSession;

    public Event create(Event event) {
        event.prePersist(); // This method must be called manually while using a StatelessSession.
        statelessSession.insert(event);
        return event;
    }
}
