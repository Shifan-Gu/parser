{{
    config(
        materialized='view'
    )
}}

-- Staging model for players data from normalized game_players table

select
    gp.match_id,
    gp.player_slot,
    gp.steam_id,
    gp.player_name,
    gp.hero_name,
    gp.game_team,
    gp.is_fake_client,
    gp.created_at
from {{ source('dota_parser', 'game_players') }} gp
where gp.match_id is not null

