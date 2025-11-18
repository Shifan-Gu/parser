{{
    config(
        materialized='table'
    )
}}

-- Map heroes to matches and teams
-- draft_active_team in the source data indicates which team is picking
-- team 0 is Radiant (slots 0-4), team 1 is Dire (slots 5-9)

with picks as (
    select
        match_id,
        hero_id,
        hero_chinese_name,
        draft_active_team,
        draft_order,
        time as pick_time
    from {{ ref('stg_draft_timing_events') }}
),

hero_slot_team as (
    select
        match_id,
        hero_id,
        case 
            when slot between 0 and 4 then 0
            when slot between 5 and 9 then 1
            else null
        end as slot_team
    from (
        select
            match_id,
            hero_id,
            slot,
            row_number() over (
                partition by match_id, hero_id
                order by time
            ) as rn
        from {{ ref('stg_interval_events') }}
        where slot is not null
            and hero_id is not null
    ) ranked_slots
    where rn = 1
)

select
    p.match_id,
    p.hero_id,
    p.hero_chinese_name,
    coalesce(
        case 
            when lower((p.draft_active_team)::text) in ('0', 'radiant', 'team_0', 'false', 'f') then 0
            when lower((p.draft_active_team)::text) in ('1', 'dire', 'team_1', 'true', 't') then 1
            else null
        end,
        h.slot_team
    ) as team,
    p.draft_order,
    p.pick_time
from picks p
left join hero_slot_team h
    on p.match_id = h.match_id
   and p.hero_id = h.hero_id

