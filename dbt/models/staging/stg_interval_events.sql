{{
    config(
        materialized='view'
    )
}}

-- Staging model for interval events
-- These are periodic snapshots of player state during the game

select
    ie.id,
    ie.match_id,
    ie.time,
    ie.slot,
    ie.hero_id,
    hcn.chinese_name as hero_chinese_name,
    ie.level,
    ie.gold,
    ie.lh,
    ie.xp,
    ie.kills,
    ie.deaths,
    ie.assists,
    ie.denies,
    ie.networth,
    ie.created_at
from {{ source('dota_parser', 'interval_events') }} ie
left join {{ source('dota_constants', 'hero_chinese_names') }} hcn
    on ie.hero_id = hcn.hero_id
where ie.slot is not null
    and ie.hero_id is not null

