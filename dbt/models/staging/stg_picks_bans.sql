{{
    config(
        materialized='view'
    )
}}

-- Staging model for picks_bans data from normalized game_picks_bans table

select
    gpb.match_id,
    gpb.is_pick,
    gpb.team,
    gpb.hero_id,
    hcn.chinese_name as hero_chinese_name,
    gpb.created_at
from {{ source('dota_parser', 'game_picks_bans') }} gpb
left join {{ source('dota_constants', 'hero_chinese_names') }} hcn
    on gpb.hero_id = hcn.hero_id
where gpb.match_id is not null

