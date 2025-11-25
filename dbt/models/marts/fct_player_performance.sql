{{
    config(
        materialized='table'
    )
}}

-- Fact table for player performance metrics per match
-- Each row represents a player's performance in a single match
-- Aggregates final stats from interval events and joins with player info and match results

with player_final_stats as (
    -- Get the final (maximum) stats for each player in each match
    select
        ie.match_id,
        ie.slot,
        ie.hero_id,
        max(hcn.chinese_name) as hero_chinese_name,
        max(ie.level) as final_level,
        max(ie.gold) as final_gold,
        max(ie.lh) as last_hits,
        max(ie.xp) as total_xp,
        max(ie.kills) as kills,
        max(ie.deaths) as deaths,
        max(ie.assists) as assists,
        max(ie.denies) as denies,
        max(ie.networth) as final_networth,
        max(ie.stuns) as total_stuns,
        max(ie.obs_placed) as obs_placed,
        max(ie.sen_placed) as sen_placed,
        max(ie.creeps_stacked) as creeps_stacked,
        max(ie.camps_stacked) as camps_stacked,
        max(ie.rune_pickups) as rune_pickups,
        max(ie.towers_killed) as towers_killed,
        max(ie.roshans_killed) as roshans_killed,
        max(ie.observers_placed) as observers_placed,
        max(ie.firstblood_claimed) as firstblood_claimed,
        max(ie.teamfight_participation) as teamfight_participation,
        max(case when ie.repicked then 1 else 0 end) as repicked,
        max(case when ie.randomed then 1 else 0 end) as randomed,
        max(case when ie.pred_vict then 1 else 0 end) as pred_vict
    from {{ source('dota_parser', 'interval_events') }} ie
    left join {{ source('dota_constants', 'hero_chinese_names') }} hcn
        on ie.hero_id = hcn.hero_id
    where ie.slot is not null
        and ie.hero_id is not null
    group by ie.match_id, ie.slot, ie.hero_id
),

player_info as (
    -- Get player information from game_players
    select
        match_id,
        player_slot,
        steam_id,
        player_name,
        hero_name,
        game_team,
        is_fake_client
    from {{ ref('stg_players') }}
),

match_results as (
    -- Get match winner information
    select
        match_id,
        winning_team,
        winning_team_name,
        radiant_team_tag,
        dire_team_tag
    from {{ ref('int_match_winners') }}
)

select
    pfs.match_id,
    pfs.slot as player_slot,
    pi.steam_id,
    pi.player_name,
    pfs.hero_id,
    pfs.hero_chinese_name,
    pi.hero_name as hero_name_english,
    coalesce(pi.game_team, 
        case when pfs.slot between 0 and 4 then 2 else 3 end
    ) as team,
    case 
        when coalesce(pi.game_team, 
            case when pfs.slot between 0 and 4 then 2 else 3 end) = 2 
        then coalesce(nullif(mr.radiant_team_tag, ''), 'Radiant')
        else coalesce(nullif(mr.dire_team_tag, ''), 'Dire')
    end as team_name,
    mr.winning_team,
    mr.winning_team_name,
    case 
        when coalesce(pi.game_team, 
            case when pfs.slot between 0 and 4 then 2 else 3 end) = mr.winning_team 
        then true 
        else false 
    end as won,
    pi.is_fake_client,
    -- Performance metrics
    pfs.final_level,
    pfs.final_gold,
    pfs.last_hits,
    pfs.total_xp,
    pfs.kills,
    pfs.deaths,
    pfs.assists,
    pfs.denies,
    pfs.final_networth,
    -- Calculated metrics
    case 
        when pfs.deaths > 0 
        then round(1.0 * pfs.kills / pfs.deaths, 2)
        else pfs.kills
    end as kda_ratio,
    pfs.kills + pfs.assists as kill_participation,
    case 
        when pfs.deaths > 0 
        then round(1.0 * (pfs.kills + pfs.assists) / pfs.deaths, 2)
        else (pfs.kills + pfs.assists)
    end as kill_participation_ratio,
    -- Additional performance metrics
    pfs.total_stuns,
    pfs.obs_placed,
    pfs.sen_placed,
    pfs.creeps_stacked,
    pfs.camps_stacked,
    pfs.rune_pickups,
    pfs.towers_killed,
    pfs.roshans_killed,
    pfs.observers_placed,
    pfs.firstblood_claimed,
    pfs.teamfight_participation,
    pfs.repicked,
    pfs.randomed,
    pfs.pred_vict
from player_final_stats pfs
left join lateral (
    -- Match player info by converting slot to player_slot format
    -- slot 0-4 maps to player_slot 0-4 (Radiant)
    -- slot 5-9 maps to player_slot 128-132 (Dire)
    select pi.*
    from player_info pi
    where pi.match_id = pfs.match_id
        and (
            (pfs.slot between 0 and 4 and pi.player_slot = pfs.slot)
            or
            (pfs.slot between 5 and 9 and pi.player_slot = 128 + (pfs.slot - 5))
        )
    limit 1
) pi on true
left join match_results mr
    on pfs.match_id = mr.match_id
where pfs.match_id is not null
    and pfs.slot is not null

