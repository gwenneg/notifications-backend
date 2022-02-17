package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.FlatEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

// TODO NOTIF-491 Delete this class when the migration is complete.
@RequestScoped
@Path("/internal/flatEvents/migrate")
public class FlatEventMigrationService {

    private static Logger LOGGER = Logger.getLogger(FlatEventMigrationService.class);

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @GET
    public Uni<Void> migrate() {
        LOGGER.debug("Flat events migration starting...");
        return sessionFactory.withTransaction((session, transaction) -> {
            return session.createQuery("FROM Event e JOIN FETCH e.eventType et JOIN FETCH et.application a JOIN FETCH a.bundle b", Event.class)
                    .getResultList()
                    .onItem().transformToMulti(Multi.createFrom()::iterable)
                    .onItem().transformToUniAndConcatenate(event -> {
                        FlatEvent flatEvent = new FlatEvent();
                        flatEvent.setAccountId(event.getAccountId());
                        flatEvent.setBundleId(event.getEventType().getApplication().getBundle().getId());
                        flatEvent.setBundleDisplayName(event.getEventType().getApplication().getBundle().getDisplayName());
                        flatEvent.setApplicationId(event.getEventType().getApplication().getId());
                        flatEvent.setApplicationDisplayName(event.getEventType().getApplication().getDisplayName());
                        flatEvent.setEventTypeId(event.getEventType().getId());
                        flatEvent.setEventTypeDisplayName(event.getEventType().getDisplayName());
                        flatEvent.setPayload(event.getPayload());
                        return session.persist(flatEvent)
                                .call(session::flush)
                                .call(() -> {
                                    return session.createQuery("UPDATE NotificationHistory SET flatEvent = :flatEvent WHERE event = :event")
                                            .setParameter("flatEvent", flatEvent)
                                            .setParameter("event", event)
                                            .executeUpdate()
                                            .call(session::flush);
                                });
                    })
                    .onItem().ignoreAsUni();
        }).invoke(() -> LOGGER.debug("Flat events migration ended"));
    }
}
