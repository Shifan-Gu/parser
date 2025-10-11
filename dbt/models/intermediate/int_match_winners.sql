{{
    config(
        materialized='table'
    )
}}

-- Determine match winners by analyzing the final state of the game
-- In Dota 2: slots 0-4 are team 0 (Radiant), slots 5-9 are team 1 (Dire)
-- We determine the winner by looking at which team destroyed the enemy ancient
-- This can be inferred from combat log events or by looking at the final game state

with ancient_destruction as (
    -- Look for ancient destruction events in combat logs
    select
        match_id,
        case 
            -- If Dire ancient (npc_dota_badguys_fort) is destroyed, Radiant wins (team 0)
            when targetname like '%badguys_fort%' or targetname like '%bad_ancient%' then 0
            -- If Radiant ancient (npc_dota_goodguys_fort) is destroyed, Dire wins (team 1)
            when targetname like '%goodguys_fort%' or targetname like '%good_ancient%' then 1
            else null
        end as winning_team,
        time as end_time
    from {{ ref('stg_combat_log_events') }}
    where type = 'DOTA_COMBATLOG_DEATH'
        and (
            targetname like '%fort%' 
            or targetname like '%ancient%'
            or targetname like '%badguys_fort%'
            or targetname like '%goodguys_fort%'
        )
),

-- Alternative method: use final game state from interval events
-- The team with higher total networth at game end typically won
final_state as (
    select
        match_id,
        max(time) as game_end_time
    from {{ ref('stg_interval_events') }}
    group by match_id
),

team_final_networth as (
    select
        i.match_id,
        case when i.slot between 0 and 4 then 0 else 1 end as team,
        sum(i.networth) as total_networth
    from {{ ref('stg_interval_events') }} i
    inner join final_state fs on i.match_id = fs.match_id and i.time = fs.game_end_time
    group by i.match_id, case when i.slot between 0 and 4 then 0 else 1 end
),

networth_winner_ranked as (
    select
        match_id,
        team as winning_team,
        row_number() over (partition by match_id order by total_networth desc) as rn
    from team_final_networth
),

networth_winner as (
    select
        match_id,
        winning_team
    from networth_winner_ranked
    where rn = 1
),

-- Combine both methods, prefer ancient destruction, fall back to networth
combined_winners as (
    select
        coalesce(ad.match_id, nw.match_id) as match_id,
        coalesce(ad.winning_team, nw.winning_team) as winning_team,
        case 
            when ad.winning_team is not null then 'ancient_destruction'
            else 'networth_heuristic'
        end as determination_method
    from ancient_destruction ad
    full outer join networth_winner nw on ad.match_id = nw.match_id
)

select 
    match_id,
    winning_team,
    determination_method
from combined_winners
where winning_team is not null

