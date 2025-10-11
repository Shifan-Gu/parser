{{
    config(
        materialized='table'
    )
}}

-- Map heroes to matches and teams
-- draft_active_team in the source data indicates which team is picking
-- team 0 is Radiant (slots 0-4), team 1 is Dire (slots 5-9)

select
    match_id,
    hero_id,
    draft_active_team as team,
    draft_order,
    time as pick_time
from {{ ref('stg_draft_timing_events') }}

