package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointDefault;
import com.redhat.cloud.notifications.models.EndpointTarget;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class DbCleaner {

    private static final String DEFAULT_BUNDLE_NAME = "rhel";
    private static final String DEFAULT_BUNDLE_DISPLAY_NAME = "Red Hat Enterprise Linux";
    private static final String DEFAULT_APP_NAME = "policies";
    private static final String DEFAULT_APP_DISPLAY_NAME = "Policies";
    private static final String DEFAULT_EVENT_TYPE_NAME = "policy-triggered";
    private static final String DEFAULT_EVENT_TYPE_DISPLAY_NAME = "Policy triggered";
    private static final String DEFAULT_EVENT_TYPE_DESCRIPTION = "Matching policy";

    @Inject
    PgPool pgClient;

    /**
     * Deletes all records from all database tables (except for flyway_schema_history) and restores the default records.
     * This method should be called from a method annotated with <b>both</b> {@link BeforeEach} and {@link AfterEach} in
     * all test classes that involve SQL queries to guarantee the tests isolation in terms of stored data. Ideally, we
     * should do that with {@link io.quarkus.test.TestTransaction} but it doesn't work with Hibernate Reactive, so this
     * is a temporary workaround to make our tests more reliable and easy to maintain.
     */
    public void clean() {
        deleteAllFrom(EmailAggregation.class);
        deleteAllFrom(EmailSubscription.class);
        deleteAllFrom(NotificationHistory.class);
        deleteAllFrom(EndpointDefault.class); // TODO [BG Phase 2] Delete this line
        deleteAllFrom(EndpointTarget.class); // TODO [BG Phase 2] Delete this line
        deleteAllFrom(BehaviorGroupAction.class);
        deleteAllFrom(WebhookProperties.class);
        deleteAllFrom(Endpoint.class);
        deleteAllFrom(EventTypeBehavior.class);
        deleteAllFrom(BehaviorGroup.class);
        deleteAllFrom(EventType.class);
        deleteAllFrom(Application.class);
        deleteAllFrom(Bundle.class);

        UUID bundleId = UUID.randomUUID();
        pgClient.preparedQuery("INSERT INTO " + getTableName(Bundle.class) + " (id, name, display_name, created) VALUES ($1, $2, $3, $4)")
                .execute(Tuple.of(bundleId, DEFAULT_BUNDLE_NAME, DEFAULT_BUNDLE_DISPLAY_NAME, LocalDateTime.now()))
                .await().indefinitely();

        UUID appId = UUID.randomUUID();
        pgClient.preparedQuery("INSERT INTO " + getTableName(Application.class) + " (id, name, display_name, created, bundle_id) VALUES ($1, $2, $3, $4, $5)")
                .execute(Tuple.of(appId, DEFAULT_APP_NAME, DEFAULT_APP_DISPLAY_NAME, LocalDateTime.now(), bundleId))
                .await().indefinitely();

        pgClient.preparedQuery("INSERT INTO " + getTableName(EventType.class) + " (id, name, display_name, description, application_id) VALUES ($1, $2, $3, $4, $5)")
                .execute(Tuple.of(bundleId, DEFAULT_EVENT_TYPE_NAME, DEFAULT_EVENT_TYPE_DISPLAY_NAME, DEFAULT_EVENT_TYPE_DESCRIPTION, appId))
                .await().indefinitely();
    }

    private void deleteAllFrom(Class<?> entityClass) {
        pgClient.query("DELETE FROM " + getTableName(entityClass)).executeAndAwait();
    }

    private String getTableName(Class<?> entityClass) {
        return entityClass.getAnnotation(Table.class).name();
    }
}
