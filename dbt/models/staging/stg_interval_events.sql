{{
    config(
        materialized='view'
    )
}}

-- Staging model for interval events
-- These are periodic snapshots of player state during the game

select
    id,
    match_id,
    time,
    slot,
    hero_id,
    level,
    gold,
    lh,
    xp,
    kills,
    deaths,
    assists,
    denies,
    networth,
    created_at
from {{ source('dota_parser', 'interval_events') }}
where slot is not null
    and hero_id is not null

