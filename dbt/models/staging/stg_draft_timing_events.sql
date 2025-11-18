{{
    config(
        materialized='view'
    )
}}

-- Staging model for draft timing events
-- Filters to only picks (not bans) and cleans the data

select
    dte.id,
    dte.match_id,
    dte.time,
    dte.draft_order,
    dte.pick,
    dte.hero_id,
    hcn.chinese_name as hero_chinese_name,
    dte.draft_active_team,
    dte.draft_extime0,
    dte.draft_extime1,
    dte.created_at
from {{ source('dota_parser', 'draft_timing_events') }} dte
left join {{ source('dota_constants', 'hero_chinese_names') }} hcn
    on dte.hero_id = hcn.hero_id
where dte.pick = true  -- Only include picks, not bans
    and dte.hero_id is not null

