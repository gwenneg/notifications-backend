package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.hibernate.StatelessSession;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventLogCleanerTest {

    /*
     * The Event#created field is automatically set because of the @PrePersist annotation in CreationTimestamped when
     * an Event is persisted using a stateful session. @PrePersist does not work with a stateless session. We need to
     * set Event#created manually to a past date in the tests below, that's why these tests are run using a stateless
     * session.
     */
    @Inject
    StatelessSession statelessSession;

    @Test
    @Transactional
    void testPostgresStoredProcedure() {
        deleteAllEvents();
        EventType eventType = createEventType();
        createEvent(eventType, now().minus(Duration.ofHours(1L)));
        createEvent(eventType, now().minus(Duration.ofDays(62L)));
        assertEquals(2L, count());
        statelessSession.createNativeQuery("CALL cleanEventLog()").executeUpdate();
        assertEquals(1L, count());
    }

    private Integer deleteAllEvents() {
        return statelessSession.createQuery("DELETE FROM Event")
                .executeUpdate();
    }

    private EventType createEventType() {
        Bundle bundle = new Bundle();
        bundle.setName("bundle");
        bundle.setDisplayName("Bundle");
        bundle.prePersist();

        Application app = new Application();
        app.setBundle(bundle);
        app.setName("app");
        app.setDisplayName("Application");
        app.prePersist();

        EventType eventType = new EventType();
        eventType.setApplication(app);
        eventType.setName("event-type");
        eventType.setDisplayName("Event type");

        statelessSession.insert(bundle);
        statelessSession.insert(app);
        statelessSession.insert(eventType);
        return eventType;
    }

    private void createEvent(EventType eventType, LocalDateTime created) {
        Event event = new Event("account-id", eventType);
        event.setCreated(created);
        statelessSession.insert(event);
    }

    private Long count() {
        return statelessSession.createQuery("SELECT COUNT(*) FROM Event", Long.class)
                .getSingleResult();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
