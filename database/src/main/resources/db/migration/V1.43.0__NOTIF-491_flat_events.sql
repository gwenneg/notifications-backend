CREATE TABLE events (
    id UUID NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    bundle_id UUID NOT NULL,
    bundle_display_name VARCHAR NOT NULL,
    application_id UUID NOT NULL,
    application_display_name VARCHAR NOT NULL,
    event_type_id UUID NOT NULL,
    event_type_display_name VARCHAR NOT NULL,
    payload TEXT NOT NULL,
    created TIMESTAMP NOT NULL,
    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT fk_events_bundle_id FOREIGN KEY (bundle_id) REFERENCES bundles (id) ON DELETE CASCADE,
    CONSTRAINT fk_events_application_id FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_events_event_type_id FOREIGN KEY (event_type_id) REFERENCES event_type (id) ON DELETE CASCADE
);

CREATE INDEX ix_events ON events (account_id, bundle_id, application_id, event_type_display_name, created DESC, id);

ALTER TABLE notification_history
   ADD COLUMN flat_event_id UUID,
   ADD CONSTRAINT fk_notification_history_flat_event_id FOREIGN KEY (flat_event_id) REFERENCES events(id) ON DELETE CASCADE;
