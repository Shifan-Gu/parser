{{
    config(
        materialized='table'
    )
}}

-- Calculate win rates for each hero
-- This is the main analytical output showing hero performance

with hero_stats as (
    select
        hero_id,
        count(*) as total_matches,
        sum(case when won then 1 else 0 end) as wins,
        sum(case when not won then 1 else 0 end) as losses,
        sum(case when won and team = 0 then 1 else 0 end) as wins_radiant,
        sum(case when won and team = 1 then 1 else 0 end) as wins_dire
    from {{ ref('fct_hero_match_results') }}
    group by hero_id
),

hero_winning_teams as (
    select
        hero_id,
        string_agg(winning_team_name, ', ' order by winning_team_name) as winning_team_names
    from (
        select distinct
            hero_id,
            winning_team_name
        from {{ ref('fct_hero_match_results') }}
        where won
            and winning_team_name is not null
    ) distinct_wins
    group by hero_id
)

select
    hs.hero_id,
    hcn.chinese_name as hero_chinese_name,
    hs.total_matches,
    hs.wins,
    hs.losses,
    hs.wins_radiant,
    hs.wins_dire,
    round(100.0 * hs.wins / nullif(hs.total_matches, 0), 2) as win_rate_pct,
    round(1.0 * hs.wins / nullif(hs.total_matches, 0), 4) as win_rate_decimal,
    round(100.0 * hs.wins_radiant / nullif(hs.total_matches, 0), 2) as win_rate_radiant_pct,
    round(100.0 * hs.wins_dire / nullif(hs.total_matches, 0), 2) as win_rate_dire_pct,
    hwt.winning_team_names
from hero_stats hs
left join hero_winning_teams hwt on hs.hero_id = hwt.hero_id
left join {{ source('dota_constants', 'hero_chinese_names') }} hcn
    on hs.hero_id = hcn.hero_id
where hs.total_matches > 0
order by total_matches desc, win_rate_pct desc

