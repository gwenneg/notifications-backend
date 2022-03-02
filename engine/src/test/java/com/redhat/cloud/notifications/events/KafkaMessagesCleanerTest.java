package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.KafkaMessage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.hibernate.StatelessSession;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class KafkaMessagesCleanerTest {

    /*
     * The KafkaMessage#created field is automatically set because of the @PrePersist annotation in CreationTimestamped
     * when a KafkaMessage is persisted using a stateful session. @PrePersist does not work with a stateless session. We
     * need to set KafkaMessage#created manually to a past date in the tests below, that's why these tests are run using
     * a stateless session.
     */
    @Inject
    StatelessSession statelessSession;

    @Test
    @Transactional
    void testPostgresStoredProcedure() {
        deleteAllKafkaMessages();
        createKafkaMessage(now().minus(Duration.ofHours(13L)));
        createKafkaMessage(now().minus(Duration.ofDays(2L)));
        assertEquals(2L, count());
        statelessSession.createNativeQuery("CALL cleanKafkaMessagesIds()").executeUpdate();
        assertEquals(1L, count());
    }

    private Integer deleteAllKafkaMessages() {
        return statelessSession.createQuery("DELETE FROM KafkaMessage")
                .executeUpdate();
    }

    private void createKafkaMessage(LocalDateTime created) {
        KafkaMessage kafkaMessage = new KafkaMessage(UUID.randomUUID());
        kafkaMessage.setCreated(created);
        statelessSession.insert(kafkaMessage);
    }

    private Long count() {
        return statelessSession.createQuery("SELECT COUNT(*) FROM KafkaMessage", Long.class)
                .getSingleResult();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
