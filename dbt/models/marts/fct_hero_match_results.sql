{{
    config(
        materialized='table'
    )
}}

-- Fact table combining hero picks with match results
-- Each row represents a hero in a match with the outcome

select
    hp.match_id,
    hp.hero_id,
    hp.hero_chinese_name,
    hp.team,
    hp.draft_order,
    hp.pick_time,
    mw.winning_team,
    mw.winning_team_name,
    mw.radiant_team_tag,
    mw.dire_team_tag,
    coalesce(
        case 
            when hp.team = 0 then coalesce(nullif(mw.radiant_team_tag, ''), 'Radiant')
            when hp.team = 1 then coalesce(nullif(mw.dire_team_tag, ''), 'Dire')
        end,
        case 
            when mw.winning_team = 0 then coalesce(nullif(mw.radiant_team_tag, ''), 'Radiant')
            when mw.winning_team = 1 then coalesce(nullif(mw.dire_team_tag, ''), 'Dire')
        end,
        'Unknown'
    ) as hero_team_name,
    case 
        when hp.team = mw.winning_team then true 
        else false 
    end as won
from (
    select
        match_id,
        hero_id,
        max(hero_chinese_name) as hero_chinese_name,
        max(team) as team,
        min(draft_order) as draft_order,
        min(pick_time) as pick_time
    from {{ ref('int_hero_picks') }}
    group by match_id, hero_id
) hp
inner join {{ ref('int_match_winners') }} mw 
    on hp.match_id = mw.match_id

