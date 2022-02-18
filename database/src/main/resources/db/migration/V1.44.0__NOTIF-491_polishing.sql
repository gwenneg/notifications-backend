ALTER TABLE event
    ALTER COLUMN bundle_id SET NOT NULL,
    ALTER COLUMN bundle_display_name SET NOT NULL,
    ALTER COLUMN application_id SET NOT NULL,
    ALTER COLUMN application_display_name SET NOT NULL,
    ALTER COLUMN event_type_display_name SET NOT NULL;

-- terminer les index