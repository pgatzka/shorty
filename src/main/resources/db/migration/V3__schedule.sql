CREATE TABLE schedule_settings (
    id         INTEGER PRIMARY KEY,
    enabled    BOOLEAN                  NOT NULL DEFAULT FALSE,
    post_times VARCHAR(200)             NOT NULL DEFAULT '',
    zone_id    VARCHAR(60)              NOT NULL DEFAULT 'UTC',
    last_slot  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT schedule_settings_singleton CHECK (id = 1)
);
