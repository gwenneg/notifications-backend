package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.FlatEvent;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventRepository {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<Event> create(Event event) {
        event.prePersist(); // This method must be called manually while using a StatelessSession.
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.insert(event)
                    .replaceWith(event);
        });
    }

    public Uni<FlatEvent> createFlat(FlatEvent flatEvent) {
        flatEvent.prePersist(); // This method must be called manually while using a StatelessSession.
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.insert(flatEvent)
                    .replaceWith(flatEvent);
        });
    }
}
