package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.DAYS;

@ApplicationScoped
public class SubscriptionsDeduplicationConfig implements EventDeduplicationConfig {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Inject
    EntityManager entityManager;

    @Override
    public LocalDateTime getDeleteAfter(Event event) {
        // The event_deduplication entries will be purged from the DB on the first day of the next month, when the first purge cronjob runs after midnight UTC.
        return event.getTimestamp().plusMonths(1).withDayOfMonth(1).truncatedTo(DAYS);
    }

    @Override
    public Optional<String> getDeduplicationKey(Event event) {

        JsonObject context = new JsonObject(event.getPayload()).getJsonObject("context");
        if (context == null) {
            Log.debug("Deduplication key could not be built because of a missing context");
            return Optional.empty();
        }

        // Determine if the org will be notified based on behavior groups and subscriptions
        boolean willBeNotified = willOrgBeNotified(
                event.getOrgId(),
                event.getEventType().getId()
        );

        // TODO We could build a simpler String key with all field values concatenated. Check if the JSON data structure has a significant impact on the query performances.
        // Use TreeMap to ensure consistent alphabetical ordering of fields in the JSON output
        TreeMap<String, Object> deduplicationKey = new TreeMap<>();
        deduplicationKey.put("org_id", event.getOrgId());
        deduplicationKey.put("product_id", context.getString("product_id"));
        deduplicationKey.put("metric_id", context.getString("metric_id"));
        deduplicationKey.put("billing_account_id", context.getString("billing_account_id"));
        deduplicationKey.put("month", event.getTimestamp().format(MONTH_FORMATTER));
        deduplicationKey.put("will_be_notified", willBeNotified);

        return Optional.of(new JsonObject(deduplicationKey).encode());
    }

    /**
     * Determines whether the given organization will be notified for the specified event type.
     * An org will be notified if:
     * <ol>
     *   <li>A behavior group exists (org-specific or default) that is linked to the event type, AND
     *       that behavior group has at least one enabled endpoint action, OR</li>
     *   <li>At least one user in the org has an active email subscription for the event type</li>
     * </ol>
     *
     * @param orgId the organization ID
     * @param eventTypeId the event type ID
     * @return true if the org will be notified, false otherwise
     */
    @CacheResult(cacheName = "org-notification-eligibility")
    public boolean willOrgBeNotified(String orgId, UUID eventTypeId) {
        Log.debugf("Checking notification eligibility for org %s and event type %s", orgId, eventTypeId);

        // Check if there's a behavior group with enabled endpoints for this org and event type
        String behaviorGroupQuery = """
            SELECT COUNT(bga) > 0
            FROM BehaviorGroupAction bga
            JOIN bga.behaviorGroup bg
            JOIN bg.behaviors b
            WHERE b.eventType.id = :eventTypeId
            AND (bg.orgId = :orgId OR bg.orgId IS NULL)
            AND bga.endpoint.enabled = true
            """;

        boolean hasBehaviorGroupWithEndpoint = entityManager.createQuery(behaviorGroupQuery, Boolean.class)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("orgId", orgId)
                .getSingleResult();

        if (hasBehaviorGroupWithEndpoint) {
            Log.debugf("Org %s has behavior group with enabled endpoint for event type %s", orgId, eventTypeId);
            return true;
        }

        // Check if any user in the org has an active email subscription for this event type
        String emailSubscriptionQuery = """
            SELECT COUNT(es) > 0
            FROM EventTypeEmailSubscription es
            WHERE es.id.orgId = :orgId
            AND es.id.eventTypeId = :eventTypeId
            AND es.subscribed = true
            """;

        boolean hasEmailSubscription = entityManager.createQuery(emailSubscriptionQuery, Boolean.class)
                .setParameter("orgId", orgId)
                .setParameter("eventTypeId", eventTypeId)
                .getSingleResult();

        if (hasEmailSubscription) {
            Log.debugf("Org %s has email subscription for event type %s", orgId, eventTypeId);
            return true;
        }

        Log.debugf("Org %s will NOT be notified for event type %s", orgId, eventTypeId);
        return false;
    }
}
