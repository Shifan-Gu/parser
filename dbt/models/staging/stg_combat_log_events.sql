{{
    config(
        materialized='view'
    )
}}

-- Staging model for combat log events
-- Focus on building destruction events to determine match winners

select
    id,
    match_id,
    time,
    type,
    attackername,
    targetname,
    sourcename,
    targetsourcename,
    value,
    created_at
from {{ source('dota_parser', 'combat_log_events') }}
where type is not null

