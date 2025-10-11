{{
    config(
        materialized='view'
    )
}}

-- Staging model for draft timing events
-- Filters to only picks (not bans) and cleans the data

select
    id,
    match_id,
    time,
    draft_order,
    pick,
    hero_id,
    draft_active_team,
    draft_extime0,
    draft_extime1,
    created_at
from {{ source('dota_parser', 'draft_timing_events') }}
where pick = true  -- Only include picks, not bans
    and hero_id is not null

