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
        sum(case when not won then 1 else 0 end) as losses
    from {{ ref('fct_hero_match_results') }}
    group by hero_id
)

select
    hero_id,
    total_matches,
    wins,
    losses,
    round(100.0 * wins / nullif(total_matches, 0), 2) as win_rate_pct,
    round(1.0 * wins / nullif(total_matches, 0), 4) as win_rate_decimal
from hero_stats
where total_matches > 0
order by total_matches desc, win_rate_pct desc

