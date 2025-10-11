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
    hp.team,
    hp.draft_order,
    hp.pick_time,
    mw.winning_team,
    mw.determination_method,
    case 
        when hp.team = mw.winning_team then true 
        else false 
    end as won
from {{ ref('int_hero_picks') }} hp
inner join {{ ref('int_match_winners') }} mw 
    on hp.match_id = mw.match_id

