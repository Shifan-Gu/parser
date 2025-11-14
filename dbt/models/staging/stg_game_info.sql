{{
    config(
        materialized='view'
    )
}}

-- Staging model for game metadata information
-- Provides team tagging details per match

select
    match_id,
    game_winner,
    radiant_team_id,
    dire_team_id,
    radiant_team_tag,
    dire_team_tag,
    created_at
from {{ source('dota_parser', 'game_info') }}

