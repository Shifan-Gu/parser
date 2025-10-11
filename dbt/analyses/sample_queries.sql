-- Sample Queries for Dota Analytics
-- These queries demonstrate how to use the hero win rates data model

-- =============================================================================
-- BASIC QUERIES
-- =============================================================================

-- 1. Get all heroes with their win rates, ordered by most played
SELECT 
    hero_id,
    total_matches,
    wins,
    losses,
    win_rate_pct
FROM {{ ref('hero_win_rates') }}
ORDER BY total_matches DESC;

-- 2. Get top 10 heroes by win rate (minimum 5 matches)
SELECT 
    hero_id,
    total_matches,
    wins,
    losses,
    win_rate_pct
FROM {{ ref('hero_win_rates') }}
WHERE total_matches >= 5
ORDER BY win_rate_pct DESC
LIMIT 10;

-- 3. Get bottom 10 heroes by win rate (minimum 5 matches)
SELECT 
    hero_id,
    total_matches,
    wins,
    losses,
    win_rate_pct
FROM {{ ref('hero_win_rates') }}
WHERE total_matches >= 5
ORDER BY win_rate_pct ASC
LIMIT 10;

-- =============================================================================
-- INTERMEDIATE QUERIES
-- =============================================================================

-- 4. Get heroes with balanced win rates (between 45% and 55%)
SELECT 
    hero_id,
    total_matches,
    wins,
    losses,
    win_rate_pct
FROM {{ ref('hero_win_rates') }}
WHERE win_rate_pct BETWEEN 45 AND 55
    AND total_matches >= 10
ORDER BY total_matches DESC;

-- 5. Get statistical summary of hero win rates
SELECT 
    COUNT(*) as total_heroes,
    AVG(win_rate_pct) as avg_win_rate,
    MIN(win_rate_pct) as min_win_rate,
    MAX(win_rate_pct) as max_win_rate,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY win_rate_pct) as median_win_rate
FROM {{ ref('hero_win_rates') }}
WHERE total_matches >= 5;

-- 6. Categorize heroes by performance tier
SELECT 
    CASE 
        WHEN win_rate_pct >= 55 THEN 'S-Tier (>55%)'
        WHEN win_rate_pct >= 52 THEN 'A-Tier (52-55%)'
        WHEN win_rate_pct >= 48 THEN 'B-Tier (48-52%)'
        WHEN win_rate_pct >= 45 THEN 'C-Tier (45-48%)'
        ELSE 'D-Tier (<45%)'
    END as tier,
    COUNT(*) as hero_count,
    AVG(total_matches) as avg_matches_per_hero
FROM {{ ref('hero_win_rates') }}
WHERE total_matches >= 10
GROUP BY tier
ORDER BY MIN(win_rate_pct) DESC;

-- =============================================================================
-- ADVANCED QUERIES
-- =============================================================================

-- 7. Get detailed match history for a specific hero
SELECT 
    fmr.match_id,
    fmr.hero_id,
    fmr.team,
    fmr.winning_team,
    fmr.won,
    fmr.determination_method,
    fmr.draft_order,
    fmr.pick_time
FROM {{ ref('fct_hero_match_results') }} fmr
WHERE fmr.hero_id = 1  -- Replace with desired hero_id
ORDER BY fmr.match_id DESC;

-- 8. Compare win rates by team (Radiant vs Dire)
SELECT 
    fmr.hero_id,
    COUNT(*) as total_matches,
    SUM(CASE WHEN fmr.team = 0 THEN 1 ELSE 0 END) as radiant_picks,
    SUM(CASE WHEN fmr.team = 1 THEN 1 ELSE 0 END) as dire_picks,
    ROUND(100.0 * SUM(CASE WHEN fmr.team = 0 AND fmr.won THEN 1 ELSE 0 END) / 
          NULLIF(SUM(CASE WHEN fmr.team = 0 THEN 1 ELSE 0 END), 0), 2) as radiant_win_rate,
    ROUND(100.0 * SUM(CASE WHEN fmr.team = 1 AND fmr.won THEN 1 ELSE 0 END) / 
          NULLIF(SUM(CASE WHEN fmr.team = 1 THEN 1 ELSE 0 END), 0), 2) as dire_win_rate
FROM {{ ref('fct_hero_match_results') }} fmr
GROUP BY fmr.hero_id
HAVING COUNT(*) >= 10
ORDER BY COUNT(*) DESC;

-- 9. Analyze win rates by draft position
SELECT 
    fmr.draft_order,
    COUNT(*) as total_picks,
    SUM(CASE WHEN fmr.won THEN 1 ELSE 0 END) as wins,
    ROUND(100.0 * SUM(CASE WHEN fmr.won THEN 1 ELSE 0 END) / COUNT(*), 2) as win_rate_pct
FROM {{ ref('fct_hero_match_results') }} fmr
GROUP BY fmr.draft_order
ORDER BY fmr.draft_order;

-- 10. Find heroes with significant performance differences between teams
WITH team_performance AS (
    SELECT 
        fmr.hero_id,
        COUNT(*) as total_matches,
        ROUND(100.0 * SUM(CASE WHEN fmr.team = 0 AND fmr.won THEN 1 ELSE 0 END) / 
              NULLIF(SUM(CASE WHEN fmr.team = 0 THEN 1 ELSE 0 END), 0), 2) as radiant_win_rate,
        ROUND(100.0 * SUM(CASE WHEN fmr.team = 1 AND fmr.won THEN 1 ELSE 0 END) / 
              NULLIF(SUM(CASE WHEN fmr.team = 1 THEN 1 ELSE 0 END), 0), 2) as dire_win_rate
    FROM {{ ref('fct_hero_match_results') }} fmr
    GROUP BY fmr.hero_id
    HAVING COUNT(*) >= 10
        AND SUM(CASE WHEN fmr.team = 0 THEN 1 ELSE 0 END) >= 5
        AND SUM(CASE WHEN fmr.team = 1 THEN 1 ELSE 0 END) >= 5
)
SELECT 
    hero_id,
    total_matches,
    radiant_win_rate,
    dire_win_rate,
    ABS(radiant_win_rate - dire_win_rate) as win_rate_difference
FROM team_performance
WHERE radiant_win_rate IS NOT NULL 
    AND dire_win_rate IS NOT NULL
ORDER BY ABS(radiant_win_rate - dire_win_rate) DESC
LIMIT 20;

-- =============================================================================
-- DATA QUALITY CHECKS
-- =============================================================================

-- 11. Check match winner determination methods
SELECT 
    determination_method,
    COUNT(*) as match_count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) as percentage
FROM {{ ref('int_match_winners') }}
GROUP BY determination_method;

-- 12. Verify no heroes have impossible win rates
SELECT 
    hero_id,
    total_matches,
    wins,
    losses,
    win_rate_pct
FROM {{ ref('hero_win_rates') }}
WHERE wins + losses != total_matches
    OR win_rate_pct < 0
    OR win_rate_pct > 100;

-- 13. Check for matches without winners
SELECT COUNT(*) as matches_without_winners
FROM {{ ref('int_hero_picks') }} hp
LEFT JOIN {{ ref('int_match_winners') }} mw ON hp.match_id = mw.match_id
WHERE mw.match_id IS NULL;

-- =============================================================================
-- EXPORT QUERIES
-- =============================================================================

-- 14. Export hero win rates for external analysis (CSV friendly)
SELECT 
    hero_id,
    total_matches,
    wins,
    losses,
    win_rate_pct,
    win_rate_decimal
FROM {{ ref('hero_win_rates') }}
ORDER BY hero_id;

