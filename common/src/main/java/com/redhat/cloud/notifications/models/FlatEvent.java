package com.redhat.cloud.notifications.models;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static javax.persistence.CascadeType.REMOVE;

@Entity
@Table(name = "events")
// TODO NOTIF-491 Rename to Event when the old Event class is removed.
public class FlatEvent extends CreationTimestamped {

    @Id
    @GeneratedValue
    private UUID id;

    @NotNull
    @Size(max = 50)
    private String accountId;

    @NotNull
    private UUID bundleId;

    @NotNull
    // TODO NOTIF-491 Should we update this if the bundle is updated?
    private String bundleDisplayName;

    @NotNull
    private UUID applicationId;

    @NotNull
    // TODO NOTIF-491 Should we update this if the application is updated?
    private String applicationDisplayName;

    @NotNull
    private UUID eventTypeId;

    @NotNull
    // TODO NOTIF-491 Should we update this if the event type is updated?
    private String eventTypeDisplayName;

    @NotNull
    private String payload;

    // TODO NOTIF-491 Rename the field to 'event' after the old one has been removed.
    @OneToMany(mappedBy = "flatEvent", cascade = REMOVE)
    Set<NotificationHistory> historyEntries;

    public FlatEvent() { }

    public FlatEvent(String accountId, String payload, EventType eventType) {
        this.accountId = accountId;
        this.payload = payload;
        bundleId = eventType.getApplication().getBundle().getId();
        bundleDisplayName = eventType.getApplication().getBundle().getDisplayName();
        applicationId = eventType.getApplication().getId();
        applicationDisplayName = eventType.getApplication().getDisplayName();
        eventTypeId = eventType.getId();
        eventTypeDisplayName = eventType.getDisplayName();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public void setBundleId(UUID bundleId) {
        this.bundleId = bundleId;
    }

    public String getBundleDisplayName() {
        return bundleDisplayName;
    }

    public void setBundleDisplayName(String bundleDisplayName) {
        this.bundleDisplayName = bundleDisplayName;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationDisplayName() {
        return applicationDisplayName;
    }

    public void setApplicationDisplayName(String applicationDisplayName) {
        this.applicationDisplayName = applicationDisplayName;
    }

    public UUID getEventTypeId() {
        return eventTypeId;
    }

    public void setEventTypeId(UUID eventTypeId) {
        this.eventTypeId = eventTypeId;
    }

    public String getEventTypeDisplayName() {
        return eventTypeDisplayName;
    }

    public void setEventTypeDisplayName(String eventTypeDisplayName) {
        this.eventTypeDisplayName = eventTypeDisplayName;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Set<NotificationHistory> getHistoryEntries() {
        return historyEntries;
    }

    public void setHistoryEntries(Set<NotificationHistory> historyEntries) {
        this.historyEntries = historyEntries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof FlatEvent) {
            FlatEvent other = (FlatEvent) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
