    -- Exactly one PENDING/RUNNING evolution task may exist for a post.
    -- MySQL allows multiple NULL values in a unique index, so completed tasks remain unrestricted.
    ALTER TABLE post_evolution_task
        ADD COLUMN active_post_id BIGINT
            GENERATED ALWAYS AS (
                CASE WHEN task_status IN ('PENDING', 'RUNNING') THEN post_id ELSE NULL END
            ) STORED;

    ALTER TABLE post_evolution_task
        ADD UNIQUE KEY uk_post_evolution_active_task (active_post_id);
