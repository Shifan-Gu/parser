package tidebound;

import com.google.gson.Gson;
import com.google.protobuf.GeneratedMessage;
import skadistats.clarity.io.Util;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.model.StringTable;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.entities.OnEntityDeleted;
import skadistats.clarity.processor.entities.OnEntityEntered;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnMessage;
import skadistats.clarity.processor.reader.OnTickEnd;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.processor.stringtables.StringTables;
import skadistats.clarity.processor.stringtables.UsesStringTable;
import skadistats.clarity.source.InputStreamSource;
import skadistats.clarity.wire.shared.common.proto.CommonNetworkBaseTypes.CNETMsg_Tick;
import skadistats.clarity.wire.shared.demo.proto.Demo;
import skadistats.clarity.wire.shared.demo.proto.Demo.CDemoFileInfo;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_ChatEvent;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_ChatMessage;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_ChatWheel;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_LocationPing;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.CDOTAUserMsg_SpectatorPlayerUnitOrders;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages.DOTA_COMBATLOG_TYPES;
import skadistats.clarity.wire.dota.s2.proto.DOTAS2GcMessagesCommon.CMsgDOTAMatch;
import skadistats.clarity.wire.shared.s1.proto.S1UserMessages.CUserMsg_SayText2;
import skadistats.clarity.wire.shared.s2.proto.S2UserMessages.CUserMessageSayText2;

import java.util.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

import tidebound.combatlogvisitors.TrackVisitor;
import tidebound.combatlogvisitors.GreevilsGreedVisitor;
import tidebound.combatlogvisitors.TrackVisitor.TrackStatus;
import tidebound.database.GameEventDAO;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages;
import tidebound.database.GameInfoDAO;
import tidebound.database.DatabaseInitializer;

public class Parse {
    
    // Constants
    private static final float INTERVAL_SECONDS = 1.0f;
    private static final float CELL_SIZE = 128.0f;
    private static final int NUM_PLAYERS = 10;
    private static final int MAX_PLAYER_SEARCH_INDEX = 30;
    private static final int DRAFT_HEROES_ARRAY_SIZE = 24;
    private static final int MAX_ABILITIES = 32;
    private static final int MAX_INVENTORY_SLOTS = 8;
    private static final int INVALID_HANDLE = 0xFFFFFF;
    private static final int TICKS_PER_SECOND = 30;
    private static final int MAX_PING_COUNT = 10000;
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final long STEAM_ID_OFFSET = 76561197960265728L;
    private static final int RADIANT_TEAM_ID = 2;
    private static final int DIRE_TEAM_ID = 3;
    private static final int WAITING_FOR_DRAFT_TEAM_ID = 14;
    private static final int PLAYER_SLOT_OFFSET = 128;
    private static final int GAME_STATE_POST_GAME = 6;
    private static final int GAME_STATE_PRE_GAME = 5;
    private static final int DRAFT_STAGE = 2;
    private static final int LIFE_STATE_ALIVE = 0;
    private static final int LIFE_STATE_DEAD = 1;
    private static final int LIFE_STATE_RESPAWNING = 2;
    private static final int COMBAT_LOG_TYPE_THRESHOLD = 19;
    private static final int CHAT_CHANNEL_TYPE_ALL_CHAT = 11;
    private static final int FACET_KEY_HERO_ID_SHIFT = 32;
    private static final int FACET_KEY_VARIANT_MASK = 0xFF;
    
    // Entity class names
    private static final String ENTITY_WEARABLE_ITEM = "CDOTAWearableItem";
    private static final String ENTITY_GAMERULES_PROXY = "CDOTAGamerulesProxy";
    private static final String ENTITY_PLAYER_RESOURCE = "CDOTA_PlayerResource";
    private static final String ENTITY_DATA_RADIANT = "CDOTA_DataRadiant";
    private static final String ENTITY_DATA_DIRE = "CDOTA_DataDire";
    private static final String ENTITY_PREFIX_ITEM = "CDOTA_Item_";
    private static final String ENTITY_PREFIX_HERO = "CDOTA_Unit_Hero_";
    private static final String ENTITY_PREFIX_COMBAT_LOG_HERO = "npc_dota_hero_";
    
    // Property names
    private static final String PROPERTY_PLAYER_ID = "m_nPlayerID";
    private static final String PROPERTY_PLAYER_ID_OLD = "m_iPlayerID";
    private static final String PROPERTY_PLAYER_OWNER_ID = "m_iPlayerOwnerID";
    private static final String PROPERTY_CURSOR_X = "m_iCursor.0000";
    private static final String PROPERTY_CURSOR_Y = "m_iCursor.0001";
    private static final String PROPERTY_ASSIGNED_HERO = "m_hAssignedHero";
    private static final String PROPERTY_GAME_TIME = "m_pGameRules.m_fGameTime";
    private static final String PROPERTY_GAME_PAUSED = "m_pGameRules.m_bGamePaused";
    private static final String PROPERTY_PAUSE_START_TICK = "m_pGameRules.m_nPauseStartTick";
    private static final String PROPERTY_TOTAL_PAUSED_TICKS = "m_pGameRules.m_nTotalPausedTicks";
    private static final String PROPERTY_GAME_START_TIME = "m_pGameRules.m_flGameStartTime";
    private static final String PROPERTY_GAME_STATE = "m_pGameRules.m_nGameState";
    private static final String PROPERTY_PLAYER_IDS_IN_CONTROL = "m_pGameRules.m_iPlayerIDsInControl";
    private static final String PROPERTY_ACTIVE_TEAM = "m_pGameRules.m_iActiveTeam";
    private static final String PROPERTY_EXTRA_TIME_REMAINING = "m_pGameRules.m_fExtraTimeRemaining";
    private static final String PROPERTY_BANNED_HEROES = "m_pGameRules.m_BannedHeroes";
    private static final String PROPERTY_SELECTED_HEROES = "m_pGameRules.m_SelectedHeroes";
    private static final String PROPERTY_CELL_X = "CBodyComponent.m_cellX";
    private static final String PROPERTY_CELL_Y = "CBodyComponent.m_cellY";
    private static final String PROPERTY_CELL_Z = "CBodyComponent.m_cellZ";
    private static final String PROPERTY_VEC_X = "CBodyComponent.m_vecX";
    private static final String PROPERTY_VEC_Y = "CBodyComponent.m_vecY";
    private static final String PROPERTY_VEC_Z = "CBodyComponent.m_vecZ";
    private static final String PROPERTY_LIFE_STATE = "m_lifeState";
    private static final String PROPERTY_HERO_FACET_KEY = "m_iHeroFacetKey";
    private static final String PROPERTY_OWNER_ENTITY = "m_hOwnerEntity";
    private static final String PROPERTY_ITEMS = "m_hItems";
    private static final String PROPERTY_ABILITIES = "m_hAbilities";
    private static final String PROPERTY_ABILITIES_VEC = "m_vecAbilities";
    private static final String PROPERTY_ENTITY_NAME_INDEX = "m_pEntity.m_nameStringableIndex";
    private static final String PROPERTY_CURRENT_CHARGES = "m_iCurrentCharges";
    private static final String PROPERTY_SECONDARY_CHARGES = "m_iSecondaryCharges";
    private static final String PROPERTY_ABILITY_LEVEL = "m_iLevel";
    private static final String PROPERTY_ACCOUNT_ID = "m_iAccountID";
    private static final String PROPERTY_ITEM_DEFINITION_INDEX = "m_iItemDefinitionIndex";
    private static final String PROPERTY_NEUTRAL_ACTIVE_DROP = "m_bIsNeutralActiveDrop";
    private static final String PROPERTY_NEUTRAL_PASSIVE_DROP = "m_bIsNeutralPassiveDrop";
    private static final String PROPERTY_NEUTRAL_DROP_TEAM = "m_nNeutralDropTeam";
    
    // String table name
    private static final String STRING_TABLE_ENTITY_NAMES = "EntityNames";

    public class Entry {
        public Integer time = 0;
        public String type;
        public Integer team;
        public String unit;
        public String key;
        public Integer value;
        public Integer slot;
        public Integer player_slot;
        // chat event fields
        public Integer player1;
        public Integer player2;
        // combat log fields
        public String attackername;
        public String targetname;
        public String sourcename;
        public String targetsourcename;
        public Boolean attackerhero;
        public Boolean targethero;
        public Boolean attackerillusion;
        public Boolean targetillusion;
        public Integer abilitylevel;
        public String inflictor;
        public Integer gold_reason;
        public Integer xp_reason;
        public String valuename;
        // public Float stun_duration;
        // public Float slow_duration;
        // entity fields
        public Integer gold;
        public Integer lh;
        public Integer xp;
        public Float x;
        public Float y;
        public Float z;
        public Float stuns;
        public Integer hero_id;
        public Integer variant;
        public Integer facet_hero_id;
        public transient List<Item> hero_inventory;
        public Integer itemslot;
        public Integer charges;
        public Integer secondary_charges;
        public Integer life_state;
        public Integer level;
        public Integer kills;
        public Integer deaths;
        public Integer assists;
        public Integer denies;
        public Boolean entityleft;
        public Integer ehandle;
        public Boolean isNeutralActiveDrop;
        public Boolean isNeutralPassiveDrop;
        public Integer obs_placed;
        public Integer sen_placed;
        public Integer creeps_stacked;
        public Integer camps_stacked;
        public Integer rune_pickups;
        public Boolean repicked;
        public Boolean randomed;
        public Boolean pred_vict;
        public Float stun_duration;
        public Float slow_duration;
        public Boolean tracked_death;
        public Integer greevils_greed_stack;
        public String tracked_sourcename;
        public Integer firstblood_claimed;
        public Float teamfight_participation;
        public Integer towers_killed;
        public Integer roshans_killed;
        public Integer observers_placed;
        public Integer draft_order;
        public Boolean pick;
        public Integer draft_active_team;
        public Integer draft_extime0;
        public Integer draft_extime1;
        public Integer networth;
        public Integer stage;

        public Entry() {
        }

        public Entry(Integer time) {
            this.time = time;
        }
    }

    /**
     * Calculates precise location from cell coordinates and vector offset.
     * 
     * @param cell Cell coordinate
     * @param vec Vector offset within the cell
     * @return Precise location coordinate
     */
    private Float getPreciseLocation(Integer cell, Float vec) {
        if (cell == null || vec == null) {
            return null;
        }
        return (cell * CELL_SIZE + vec) / CELL_SIZE;
    }

    private class Item {
        String id;
        // Charges can be used to determine how many items are stacked together on
        // stackable items
        Integer slot;
        Integer num_charges;
        // item_ward_dispenser uses num_charges for observer wards
        // and num_secondary_charges for sentry wards count
        // and is considered not stackable
        Integer num_secondary_charges;
    }

    private class Ability {
        String id;
        Integer abilityLevel;
    }

    private class UnknownItemFoundException extends RuntimeException {
        public UnknownItemFoundException(String message) {
            super(message);
        }
    }

    private class UnknownAbilityFoundException extends RuntimeException {
        public UnknownAbilityFoundException(String message) {
            super(message);
        }
    }

    // Game state tracking
    private float nextInterval = 0;
    private Integer time = 0;
    private int[] validIndices = new int[NUM_PLAYERS];
    private boolean initialized = false;
    private int gameStartTime = 0;
    private boolean postGame = false; // true when ancient destroyed
    private boolean epilogue = false;
    private int serverTick = 0;
    
    // JSON serialization
    private final Gson gson = new Gson();
    
    // Player and entity mappings
    private final HashMap<String, Integer> nameToSlot = new HashMap<>();
    private final Map<String, Integer> abilitiesTracking = new HashMap<>();
    private List<Ability> abilities;
    private final Map<Integer, Integer> slotToPlayerSlot = new HashMap<>();
    private final Map<Long, Integer> steamIdToPlayerSlot = new HashMap<>();
    private final Map<Integer, Integer> cosmeticsMap = new HashMap<>();
    private final Map<Integer, Integer> dotaPlusXpMap = new HashMap<>(); // playerslot, xp
    private final Map<Integer, Integer> wardEhandleToSlot = new HashMap<>();
    
    // I/O streams
    private final InputStream inputStream;
    private final OutputStream outputStream;
    
    // Visitors for combat log processing
    private final GreevilsGreedVisitor greevilsGreedVisitor;
    private final TrackVisitor trackVisitor;
    
    // Player state tracking
    private final List<Boolean> isPlayerStartingItemsWritten;
    private int pingCount = 0;
    private List<Entry> logBuffer = new ArrayList<>();
    private final List<Entry> pendingDatabaseEvents = new ArrayList<>();
    
    // Ward tracking fields (consolidated from Wards.java)
    private static final Map<String, String> WARDS_TARGET_NAME_BY_DT_CLASS;
    private static final Set<String> WARDS_DT_CLASSES;
    private static final Set<String> WARDS_TARGET_NAMES;
    
    static {
        final String TARGET_NAME_OBSERVER = "npc_dota_observer_wards";
        final String TARGET_NAME_SENTRY = "npc_dota_sentry_wards";
        HashMap<String, String> target_by_dtclass = new HashMap<>(4);
        
        target_by_dtclass.put("DT_DOTA_NPC_Observer_Ward", TARGET_NAME_OBSERVER);
        target_by_dtclass.put("CDOTA_NPC_Observer_Ward", TARGET_NAME_OBSERVER);
        target_by_dtclass.put("DT_DOTA_NPC_Observer_Ward_TrueSight", TARGET_NAME_SENTRY);
        target_by_dtclass.put("CDOTA_NPC_Observer_Ward_TrueSight", TARGET_NAME_SENTRY);
        
        WARDS_TARGET_NAME_BY_DT_CLASS = Collections.unmodifiableMap(target_by_dtclass);
        WARDS_DT_CLASSES = Collections.unmodifiableSet(target_by_dtclass.keySet());
        WARDS_TARGET_NAMES = Collections.unmodifiableSet(new HashSet<>(target_by_dtclass.values()));
    }
    
    private final Map<Integer, FieldPath> wardLifeStatePaths = new HashMap<>();
    private final Map<Integer, Integer> wardCurrentLifeState = new HashMap<>();
    private final Map<String, Queue<String>> wardKillersByWardClass = new HashMap<>();
    private Queue<WardProcessEntityCommand> wardToProcess = new ArrayDeque<>();
    
    private class WardProcessEntityCommand {
        private final Entity entity;
        private final FieldPath fieldPath;
        
        public WardProcessEntityCommand(Entity e, FieldPath p) {
            entity = e;
            fieldPath = p;
        }
    }
    
    // Database integration
    private GameEventDAO gameEventDAO;
    private GameInfoDAO gameInfoDAO;
    private Long matchId;
    private boolean databaseEnabled;
    private final int batchSize = DEFAULT_BATCH_SIZE;
    private int currentBatchSize = 0;

    // Draft stage tracking
    private final boolean[] draftOrderProcessed = new boolean[DRAFT_HEROES_ARRAY_SIZE];
    private int draftOrder = 1;
    private boolean isDraftStartTimeProcessed = false;

    // Dota Plus tracking
    private boolean isDotaPlusProcessed = false;

    // Pause timing tracking
    private boolean wasPaused = false;
    private int pauseStartTime = 0;
    private int pauseStartGameTime = 0;

    public Parse(InputStream input, OutputStream output) throws IOException {
        this.inputStream = input;
        this.outputStream = output;
        
        greevilsGreedVisitor = new GreevilsGreedVisitor(nameToSlot);
        trackVisitor = new TrackVisitor();

        isPlayerStartingItemsWritten = new ArrayList<>(Arrays.asList(new Boolean[NUM_PLAYERS]));
        Collections.fill(isPlayerStartingItemsWritten, Boolean.FALSE);
        
        // Initialize ward tracking
        WARDS_TARGET_NAMES.forEach((cls) -> {
            wardKillersByWardClass.put(cls, new ArrayDeque<>());
        });
        
        // Initialize database connection
        initializeDatabase();
        
        long startTime = System.currentTimeMillis();
        new SimpleRunner(new InputStreamSource(inputStream)).runWith(this);
        
        // Flush any remaining database operations
        if (databaseEnabled) {
            try {
                if (!pendingDatabaseEvents.isEmpty()) {
                    flushPendingDatabaseEvents();
                }
                if (!pendingDatabaseEvents.isEmpty()) {
                    System.err.println(String.format(
                        "Unable to persist %d buffered events because no match ID was determined.",
                        pendingDatabaseEvents.size()
                    ));
                    pendingDatabaseEvents.clear();
                }
                if (gameEventDAO != null) {
                    gameEventDAO.executeBatch();
                    gameEventDAO.close();
                }
                if (gameInfoDAO != null) {
                    gameInfoDAO.close();
                }
                System.err.println("Database operations completed successfully.");
            } catch (Exception e) {
                System.err.println("Error finalizing database operations: " + e.getMessage());
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        System.err.format("total time taken: %s\n", totalTime / 1000.0);
    }

    /**
     * Outputs an entry to the output stream and optionally to the database.
     * 
     * @param entry The entry to output
     */
    public void output(Entry entry) {
        try {
            if (!epilogue && gameStartTime == 0 && logBuffer != null) {
                logBuffer.add(entry);
            } else {
                entry.time -= gameStartTime;
                outputStream.write((gson.toJson(entry) + "\n").getBytes());
                
                // Save to database if enabled
                if (databaseEnabled) {
                    try {
                        enqueueDatabaseEvent(entry);
                    } catch (Exception ex) {
                        System.err.println("Error saving event to database: " + ex.getMessage());
                        pendingDatabaseEvents.add(entry);
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("Error writing entry to output stream: " + ex.getMessage());
        } catch (IllegalArgumentException iex) {
            System.err.println("Invalid argument in entry: " + iex.getMessage());
        }
    }

    public void flushLogBuffer() {
        if (logBuffer == null) {
            return;
        }
        for (Entry e : logBuffer) {
            output(e);
        }
        logBuffer = null;
    }


    @OnMessage(CMsgDOTAMatch.class)
    public void onDotaMatch(Context ctx, CMsgDOTAMatch message) {
        // Could use this for match overview data for uploads
        // Currently not used
    }

    /**
     * Extracts player slot from an entity.
     * 
     * @param ctx Context
     * @param entity Entity to extract slot from
     * @return Player slot or null if not found
     */
    public Integer getPlayerSlotFromEntity(Context ctx, Entity entity) {
        if (entity == null) {
            return null;
        }
        Integer slot = getEntityProperty(entity, PROPERTY_PLAYER_ID, null);
        // Sentry wards still use pre 7.31 property for storing new ID
        if (slot == null) {
            slot = getEntityProperty(entity, PROPERTY_PLAYER_ID_OLD, null);
        }
        if (slot == null) {
            slot = getEntityProperty(entity, PROPERTY_PLAYER_OWNER_ID, null);
        }
        if (slot != null) {
            slot /= 2;
        }
        return slot;
    }

    @OnMessage(CDOTAUserMsg_SpectatorPlayerUnitOrders.class)
    public void onSpectatorPlayerUnitOrders(Context ctx, CDOTAUserMsg_SpectatorPlayerUnitOrders message) {
        Entry entry = new Entry(time);
        entry.type = "actions";
        // the entindex points to a CDOTAPlayer. This is probably the player that gave
        // the order.
        Entity e = ctx.getProcessor(Entities.class).getByIndex(message.getEntindex());
        entry.slot = getPlayerSlotFromEntity(ctx, e);
        // Integer handle = (Integer)getEntityProperty(e, "m_hAssignedHero", null);
        // Entity h = ctx.getProcessor(Entities.class).getByHandle(handle);
        // System.err.println(h.getDtClass().getDtName());
        // break actions into types?
        entry.key = String.valueOf(message.getOrderType());
        // System.err.println(message);
        output(entry);
    }

    @OnMessage(CDOTAUserMsg_LocationPing.class)
    public void onPlayerPing(Context ctx, CDOTAUserMsg_LocationPing message) {
        pingCount++;
        if (pingCount > MAX_PING_COUNT) {
            return;
        }

        Entry entry = new Entry(time);
        entry.type = "pings";
        entry.slot = message.getPlayerId();
        // Could get the ping coordinates/type if needed
        output(entry);
    }

    @OnMessage(CDOTAUserMsg_ChatEvent.class)
    public void onChatEvent(Context ctx, CDOTAUserMsg_ChatEvent message) {
        Integer player1 = message.getPlayerid1();
        Integer player2 = message.getPlayerid2();
        Integer value = message.getValue();
        String type = String.valueOf(message.getType());
        Entry entry = new Entry(time);
        entry.type = type;
        entry.player1 = player1;
        entry.player2 = player2;
        entry.value = value;
        output(entry);
    }

    // New chat event
    @OnMessage(CDOTAUserMsg_ChatMessage.class)
    public void onAllChatMessage(Context ctx, CDOTAUserMsg_ChatMessage message) {
        int channelType = message.getChannelType();
        Entry entry = new Entry(time);
        entry.slot = message.getSourcePlayerId();
        entry.type = (channelType == CHAT_CHANNEL_TYPE_ALL_CHAT) ? "chat" : String.valueOf(channelType);
        entry.key = message.getMessageText();
        output(entry);
    }

    @OnMessage(CDOTAUserMsg_ChatWheel.class)
    public void onChatWheel(Context ctx, CDOTAUserMsg_ChatWheel message) {
        Entry entry = new Entry(time);
        entry.type = "chatwheel";
        entry.slot = message.getPlayerId();
        entry.key = String.valueOf(message.getChatMessageId());
        output(entry);
    }

    @OnMessage(CUserMsg_SayText2.class)
    public void onAllChatS1(Context ctx, CUserMsg_SayText2 message) {
        Entry entry = new Entry(time);
        entry.unit = String.valueOf(message.getPrefix());
        entry.key = String.valueOf(message.getText());
        entry.type = "chat";
        output(entry);
    }

    @OnMessage(CUserMessageSayText2.class)
    public void onAllChatS2(Context ctx, CUserMessageSayText2 message) {
        Entry entry = new Entry(time);
        entry.unit = String.valueOf(message.getParam1());
        entry.key = String.valueOf(message.getParam2());
        Entity e = ctx.getProcessor(Entities.class).getByIndex(message.getEntityindex());
        entry.slot = getPlayerSlotFromEntity(ctx, e);
        entry.type = "chat";
        output(entry);
    }

    @OnMessage(CDemoFileInfo.class)
    public void onFileInfo(Context ctx, CDemoFileInfo message) {
        // Extracted cosmetics data from CDOTAWearableItem entities
        Entry cosmeticsEntry = new Entry();
        cosmeticsEntry.type = "cosmetics";
        cosmeticsEntry.key = gson.toJson(cosmeticsMap);
        output(cosmeticsEntry);

        // Dota plus hero levels
        Entry dotaPlusEntry = new Entry();
        dotaPlusEntry.type = "dotaplus";
        dotaPlusEntry.key = gson.toJson(dotaPlusXpMap);
        output(dotaPlusEntry);

        // Emit epilogue event to mark finish
        Entry epilogueEntry = new Entry();
        epilogueEntry.type = "epilogue";
        epilogueEntry.key = gson.toJson(message);
        output(epilogueEntry);
        
        persistGameInfo(message);

        epilogue = true;
        // Some replays don't have a game start time and we never flush, so just do it now
        flushLogBuffer();
    }

    @OnCombatLogEntry
    public void onCombatLogEntry(Context ctx, CombatLogEntry cle) {
        try {
            time = Math.round(cle.getTimestamp());
            
            // Handle ward death tracking (from Wards.java)
            if (isWardDeath(cle)) {
                String killer;
                if ((killer = cle.getDamageSourceName()) != null) {
                    wardKillersByWardClass.get(cle.getTargetName()).add(killer);
                }
            }
            
            // create a new entry
            Entry combatLogEntry = new Entry(time);
            combatLogEntry.type = cle.getType().name();
            // translate the fields using string tables if necessary (get*Name methods)
            combatLogEntry.attackername = cle.getAttackerName();
            combatLogEntry.targetname = cle.getTargetName();
            combatLogEntry.sourcename = cle.getDamageSourceName();
            combatLogEntry.targetsourcename = cle.getTargetSourceName();
            combatLogEntry.inflictor = cle.getInflictorName();
            combatLogEntry.attackerhero = cle.isAttackerHero();
            combatLogEntry.targethero = cle.isTargetHero();
            combatLogEntry.attackerillusion = cle.isAttackerIllusion();
            combatLogEntry.targetillusion = cle.isTargetIllusion();
            combatLogEntry.value = cle.getValue();
            float stunDuration = cle.getStunDuration();
            if (stunDuration > 0) {
                combatLogEntry.stun_duration = stunDuration;
            }
            float slowDuration = cle.getSlowDuration();
            if (slowDuration > 0) {
                combatLogEntry.slow_duration = slowDuration;
            }
            // value may be out of bounds in string table, we can only get valuename if a
            // purchase (type 11)
            if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_PURCHASE) {
                combatLogEntry.valuename = cle.getValueName();
            } else if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_GOLD) {
                combatLogEntry.gold_reason = cle.getGoldReason();
            } else if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_XP) {
                combatLogEntry.xp_reason = cle.getXpReason();
            }

            combatLogEntry.greevils_greed_stack = greevilsGreedVisitor.visit(time, cle);
            TrackStatus trackStatus = trackVisitor.visit(time, cle);
            if (trackStatus != null) {
                combatLogEntry.tracked_death = trackStatus.tracked;
                combatLogEntry.tracked_sourcename = trackStatus.inflictor;
            }
            if (combatLogEntry.type.equals("DOTA_COMBATLOG_GAME_STATE")) {
                if (combatLogEntry.value == GAME_STATE_POST_GAME) {
                    postGame = true;
                } else if (combatLogEntry.value == GAME_STATE_PRE_GAME) {
                    // See alternative gameStartTime from gameRulesProxy
                    if (gameStartTime == 0) {
                        gameStartTime = combatLogEntry.time;
                        flushLogBuffer();
                    }
                }
            }
            if (cle.getType().ordinal() <= COMBAT_LOG_TYPE_THRESHOLD) {
                output(combatLogEntry);
            }
        } catch (Exception e) {
            System.err.println(e);
            System.err.println(cle);
        }
    }

    @OnEntityEntered
    public void onEntityEntered(Context ctx, Entity entity) {
        String entityName = entity.getDtClass().getDtName();

        if (ENTITY_WEARABLE_ITEM.equals(entityName)) {
            handleWearableItem(ctx, entity);
        } else if (entityName.startsWith(ENTITY_PREFIX_ITEM + "Tier") && entityName.endsWith("Token")) {
            handleNeutralToken(ctx, entity, entityName);
        } else if (entityName.startsWith(ENTITY_PREFIX_ITEM)) {
            handleNeutralItem(ctx, entity, entityName);
        }
    }
    
    private void handleWearableItem(Context ctx, Entity entity) {
        Integer accountId = getEntityProperty(entity, PROPERTY_ACCOUNT_ID, null);
        Integer itemDefinitionIndex = getEntityProperty(entity, PROPERTY_ITEM_DEFINITION_INDEX, null);
        if (accountId != null && accountId > 0 && itemDefinitionIndex != null) {
            Long accountId64 = STEAM_ID_OFFSET + (long) accountId;
            Integer playerSlot = steamIdToPlayerSlot.get(accountId64);
            if (playerSlot != null) {
                cosmeticsMap.put(itemDefinitionIndex, playerSlot);
            }
        }
    }
    
    private void handleNeutralToken(Context ctx, Entity entity, String entityName) {
        Entry entry = new Entry(time);
        entry.type = "neutral_token";
        entry.slot = getPlayerSlotFromEntity(ctx, entity);
        entry.key = entityName.substring(ENTITY_PREFIX_ITEM.length());
        output(entry);
    }
    
    private void handleNeutralItem(Context ctx, Entity entity, String entityName) {
        Boolean isNeutralActiveDrop = getEntityProperty(entity, PROPERTY_NEUTRAL_ACTIVE_DROP, null);
        Boolean isNeutralPassiveDrop = getEntityProperty(entity, PROPERTY_NEUTRAL_PASSIVE_DROP, null);
        Integer neutralDropTeam = getEntityProperty(entity, PROPERTY_NEUTRAL_DROP_TEAM, null);
        
        boolean isNeutralDrop = (isNeutralActiveDrop != null && isNeutralActiveDrop) 
            || (isNeutralPassiveDrop != null && isNeutralPassiveDrop);
        
        if (neutralDropTeam != null && neutralDropTeam != 0 && isNeutralDrop) {
            Entry entry = new Entry(time);
            entry.type = "neutral_item_history";
            entry.slot = getPlayerSlotFromEntity(ctx, entity);
            entry.key = entityName.substring(ENTITY_PREFIX_ITEM.length());
            entry.isNeutralActiveDrop = isNeutralActiveDrop;
            entry.isNeutralPassiveDrop = isNeutralPassiveDrop;
            output(entry);
        }
    }

    @OnMessage(CNETMsg_Tick.class)
    public void onMessage(CNETMsg_Tick message) {
        serverTick = message.getTick();
    }

    @UsesStringTable(STRING_TABLE_ENTITY_NAMES)
    @UsesEntities
    @OnTickStart
    public void onTickStart(Context ctx, boolean synthetic) {
        Entities entities = ctx.getProcessor(Entities.class);
        Entity gameRulesProxy = entities.getByDtName(ENTITY_GAMERULES_PROXY);
        Entity playerResource = entities.getByDtName(ENTITY_PLAYER_RESOURCE);
        Entity dataDire = entities.getByDtName(ENTITY_DATA_DIRE);
        Entity dataRadiant = entities.getByDtName(ENTITY_DATA_RADIANT);

        Integer draftStage = getEntityProperty(gameRulesProxy, PROPERTY_GAME_STATE, null);

        if (gameRulesProxy != null) {
            Float oldTime = getEntityProperty(gameRulesProxy, PROPERTY_GAME_TIME, null);
            if (oldTime == null) {
                // 7.32e on, need to calculate time manually
                boolean isPaused = getEntityProperty(gameRulesProxy, PROPERTY_GAME_PAUSED, null);
                int timeTick = isPaused ? getEntityProperty(gameRulesProxy, PROPERTY_PAUSE_START_TICK, null) : serverTick;
                int pausedTicks = getEntityProperty(gameRulesProxy, PROPERTY_TOTAL_PAUSED_TICKS, null);
                time = Math.round((float) (timeTick - pausedTicks) / TICKS_PER_SECOND);

                // Tracking game pauses
                if (isPaused && !wasPaused) {
                    // Game just got paused
                    pauseStartTime = timeTick;
                    pauseStartGameTime = time;
                    wasPaused = true;
                } else if (!isPaused && wasPaused) {
                    // Game just got unpaused
                    int pauseDuration = Math.round((float) (timeTick - pauseStartTime) / TICKS_PER_SECOND);
                    if (pauseDuration > 0) {
                        Entry pauseEntry = new Entry(pauseStartGameTime);
                        pauseEntry.type = "game_paused";
                        pauseEntry.key = "pause_duration";
                        pauseEntry.value = pauseDuration;
                        output(pauseEntry);
                    }
                    wasPaused = false;
                }
            } else {
                time = Math.round(oldTime);
            }
            
            // Alternate to combat log for getting game zero time
            // Some replays don't have the combat log event for some reason so also do this here
            int currentGameStartTime = Math.round((float) gameRulesProxy.getProperty(PROPERTY_GAME_START_TIME));
            if (gameStartTime == 0 && currentGameStartTime != 0) {
                gameStartTime = currentGameStartTime;
                flushLogBuffer();
            }
            
            if (draftStage == DRAFT_STAGE) {

                handleDraftStage(ctx, gameRulesProxy, draftStage);
            }
            
            // Initialize nextInterval value
            if (nextInterval == 0) {
                nextInterval = time;
            }
        }
        
        if (playerResource != null) {
            // Radiant coach shows up in vecPlayerTeamData as position 5
            // all the remaining dire entities are offset by 1 and so we miss reading the
            // last one and don't get data for the first dire player
            // coaches appear to be on team 1, radiant is 2 and dire is 3?
            // Construct an array of valid indices to get vecPlayerTeamData from
            if (!initialized) {
                initializePlayers(ctx, playerResource);
            }

            if (initialized && !postGame && time >= nextInterval) {
                processIntervalUpdate(ctx, playerResource, dataRadiant, dataDire, draftStage);
                nextInterval += INTERVAL_SECONDS;
            }

            // When the game is over, get dota plus levels
            if (postGame && !isDotaPlusProcessed) {
                processDotaPlusLevels(ctx, playerResource);
            }
        }
    }
    
    private void handleDraftStage(Context ctx, Entity gameRulesProxy, Integer draftStage) {
        // Determine the time the draftings start
        if (!isDraftStartTimeProcessed) {
            Long playerIdsInControl = getEntityProperty(gameRulesProxy, PROPERTY_PLAYER_IDS_IN_CONTROL, null);
            boolean isDraftStarted = playerIdsInControl != null && playerIdsInControl.compareTo(0L) != 0;
            if (isDraftStarted) {
                Entry draftStartEntry = new Entry(time);
                draftStartEntry.type = "draft_start";
                output(draftStartEntry);
                isDraftStartTimeProcessed = true;
            }
        }

        // Picks and ban are not in order due to draft change rules changes between patches
        // Need to listen for the picks and ban to change
        int[] draftHeroes = loadDraftHeroes(gameRulesProxy);
        
        // Once a pick or ban happens grab the time and extra time remaining for both teams
        for (int i = 0; i < draftHeroes.length; i++) {
            if (draftHeroes[i] > 0 && !draftOrderProcessed[i]) {
                draftOrderProcessed[i] = true;
                Entry draftTimingsEntry = new Entry(time);
                draftTimingsEntry.type = "draft_timings";
                draftTimingsEntry.draft_order = draftOrder;
                draftOrder++;
                draftTimingsEntry.pick = i >= 14; // First 14 are bans, rest are picks
                draftTimingsEntry.hero_id = draftHeroes[i] / 2;
                draftTimingsEntry.draft_active_team = getEntityProperty(gameRulesProxy, PROPERTY_ACTIVE_TEAM, null);
                draftTimingsEntry.draft_extime0 = Math.round((float) getEntityProperty(gameRulesProxy, 
                    PROPERTY_EXTRA_TIME_REMAINING + ".0000", null));
                draftTimingsEntry.draft_extime1 = Math.round((float) getEntityProperty(gameRulesProxy, 
                    PROPERTY_EXTRA_TIME_REMAINING + ".0001", null));
                output(draftTimingsEntry);
            }
        }
    }
    
    private int[] loadDraftHeroes(Entity gameRulesProxy) {
        int[] draftHeroes = new int[DRAFT_HEROES_ARRAY_SIZE];
        
        // Load banned heroes (0-13)
        for (int i = 0; i < 14; i++) {
            String index = String.format("%04d", i);
            Integer hero = getEntityProperty(gameRulesProxy, PROPERTY_BANNED_HEROES + "." + index, null);
            draftHeroes[i] = (hero == null) ? 0 : hero;
        }
        
        // Load selected heroes (14-23)
        for (int i = 0; i < 10; i++) {
            String index = String.format("%04d", i);
            draftHeroes[14 + i] = getEntityProperty(gameRulesProxy, PROPERTY_SELECTED_HEROES + "." + index, null);
        }
        
        return draftHeroes;
    }
    
    private void initializePlayers(Context ctx, Entity playerResource) {
        int added = 0;
        int index = 0;
        boolean hasWaitingForDraftPlayers = false;
        List<Entry> playerEntries = new ArrayList<>();
        
        // According to @Decoud Valve seems to have fixed this issue and players should
        // be in first 10 slots again. Sanity check of index to prevent infinite loop when <10 players
        while (added < NUM_PLAYERS && index < MAX_PLAYER_SEARCH_INDEX) {
            try {
                int playerTeam = getEntityProperty(playerResource, "m_vecPlayerData.%i.m_iPlayerTeam", index);
                int teamSlot = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_iTeamSlot", index);
                Long steamId = getEntityProperty(playerResource, "m_vecPlayerData.%i.m_iPlayerSteamID", index);
                
                if (playerTeam == RADIANT_TEAM_ID || playerTeam == DIRE_TEAM_ID) {
                    Entry entry = new Entry(time);
                    entry.type = "player_slot";
                    entry.key = String.valueOf(added);
                    entry.value = (playerTeam == RADIANT_TEAM_ID ? 0 : PLAYER_SLOT_OFFSET) + teamSlot;
                    playerEntries.add(entry);
                    validIndices[added] = index;
                    added++;
                    slotToPlayerSlot.put(added, entry.value);
                    steamIdToPlayerSlot.put(steamId, entry.value);
                } else if (playerTeam == WAITING_FOR_DRAFT_TEAM_ID) {
                    // 7.33 player waiting to be drafted onto a team
                    hasWaitingForDraftPlayers = true;
                    break;
                }
            } catch (Exception e) {
                // Swallow the exception when an unexpected number of players (!=10)
            }
            index++;
        }
        
        if (!hasWaitingForDraftPlayers) {
            for (Entry entry : playerEntries) {
                output(entry);
            }
            initialized = true;
        }
    }
    
    private void processIntervalUpdate(Context ctx, Entity playerResource, Entity dataRadiant, Entity dataDire, Integer draftStage) {
        for (int i = 0; i < NUM_PLAYERS; i++) {
            Integer hero = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_nSelectedHeroID", validIndices[i]);
            int handle = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_hSelectedHero", validIndices[i]);
            int playerTeam = getEntityProperty(playerResource, "m_vecPlayerData.%i.m_iPlayerTeam", validIndices[i]);
            int teamSlot = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_iTeamSlot", validIndices[i]);

            // Facet/variant format and key location changed in 7.39, leaving this as a fallback
            Integer variant = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_nSelectedHeroVariant", validIndices[i]);
            Integer facetHeroId = null;

            Entity dataTeam = (playerTeam == RADIANT_TEAM_ID) ? dataRadiant : dataDire;

            Entry entry = new Entry(time);
            entry.type = "interval";
            entry.slot = i;
            entry.repicked = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_bHasRepicked", validIndices[i]);
            entry.randomed = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_bHasRandomed", validIndices[i]);
            entry.pred_vict = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_bHasPredictedVictory", validIndices[i]);
            entry.firstblood_claimed = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_iFirstBloodClaimed", validIndices[i]);
            entry.teamfight_participation = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_flTeamFightParticipation", validIndices[i]);
            entry.level = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_iLevel", validIndices[i]);
            entry.kills = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_iKills", validIndices[i]);
            entry.deaths = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_iDeaths", validIndices[i]);
            entry.assists = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_iAssists", validIndices[i]);
            entry.denies = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iDenyCount", teamSlot);
            entry.obs_placed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iObserverWardsPlaced", teamSlot);
            entry.sen_placed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iSentryWardsPlaced", teamSlot);
            entry.creeps_stacked = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iCreepsStacked", teamSlot);
            entry.camps_stacked = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iCampsStacked", teamSlot);
            entry.rune_pickups = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iRunePickups", teamSlot);
            entry.towers_killed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTowerKills", teamSlot);
            entry.roshans_killed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iRoshanKills", teamSlot);
            entry.observers_placed = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iObserverWardsPlaced", teamSlot);
            entry.networth = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iNetWorth", teamSlot);
            entry.stage = draftStage;

            if (teamSlot >= 0) {
                entry.gold = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedGold", teamSlot);
                entry.lh = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iLastHitCount", teamSlot);
                entry.xp = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedXP", teamSlot);
                entry.stuns = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_fStuns", teamSlot);
            }

            // Get the player's hero entity
            Entity heroEntity = ctx.getProcessor(Entities.class).getByHandle(handle);
            if (heroEntity != null) {
                processHeroEntity(ctx, heroEntity, entry, hero, variant, facetHeroId);
            }
            
            output(entry);
        }
    }
    
    private void processHeroEntity(Context ctx, Entity heroEntity, Entry entry, Integer hero, Integer variant, Integer facetHeroId) {
        // Get the hero's coordinates
        Integer cellX = getEntityProperty(heroEntity, PROPERTY_CELL_X, null);
        Integer cellY = getEntityProperty(heroEntity, PROPERTY_CELL_Y, null);
        Float vecX = getEntityProperty(heroEntity, PROPERTY_VEC_X, null);
        Float vecY = getEntityProperty(heroEntity, PROPERTY_VEC_Y, null);
        
        if (cellX != null && cellY != null) {
            entry.x = getPreciseLocation(cellX, vecX);
            entry.y = getPreciseLocation(cellY, vecY);
        }

        // Post-7.39 format for facets is a 128bit number, where last 32 bits represent the variant
        // and the first 32 bits represent the hero id, which acts as the source for the facet
        // (same as hero_id in all cases, except ability draft)
        // 0xHHHH00000000VVVV
        Long facetKey = getEntityProperty(heroEntity, PROPERTY_HERO_FACET_KEY, null);
        if (facetKey != null) {
            facetHeroId = (int) (facetKey >> FACET_KEY_HERO_ID_SHIFT);
            variant = (int) (facetKey & FACET_KEY_VARIANT_MASK);
        }

        entry.unit = heroEntity.getDtClass().getDtName();
        entry.hero_id = hero;
        entry.variant = variant;
        entry.facet_hero_id = facetHeroId;
        entry.life_state = getEntityProperty(heroEntity, PROPERTY_LIFE_STATE, null);
        
        // Check if hero has been assigned to entity
        if (hero != null && hero > 0) {
            String unit = heroEntity.getDtClass().getDtName();
            String ending = unit.substring(ENTITY_PREFIX_HERO.length());
            // Valve is inconsistent with combat log names - could involve replacing camelCase with _ or not
            // Double map it so we can look up both cases
            String combatLogName = ENTITY_PREFIX_COMBAT_LOG_HERO + ending.toLowerCase();
            String combatLogName2 = "npc_dota_hero" + ending.replaceAll("([A-Z])", "_$1").toLowerCase();
            
            // Populate for combat log mapping
            nameToSlot.put(combatLogName, entry.slot);
            nameToSlot.put(combatLogName2, entry.slot);

            abilities = getHeroAbilities(ctx, heroEntity);
            for (Ability ability : abilities) {
                // Only push ability updates when the level changes
                String abilityKey = combatLogName + ability.id;
                Integer currentLevel = abilitiesTracking.get(abilityKey);
                if (currentLevel == null || !currentLevel.equals(ability.abilityLevel)) {
                    Entry abilitiesEntry = new Entry(time);
                    abilitiesEntry.type = "DOTA_ABILITY_LEVEL";
                    abilitiesEntry.targetname = combatLogName;
                    abilitiesEntry.valuename = ability.id;
                    abilitiesEntry.abilitylevel = ability.abilityLevel;
                    // We use the combatLogName & the ability id as some ability IDs are the same
                    abilitiesTracking.put(abilityKey, ability.abilityLevel);
                    output(abilitiesEntry);
                }
            }

            entry.hero_inventory = getHeroInventory(ctx, heroEntity);
            processStartingItems(entry, combatLogName);
        }
    }
    
    private void processStartingItems(Entry entry, String combatLogName) {
        if (time - gameStartTime - 1 == 0 && entry.hero_inventory != null) {
            for (Item item : entry.hero_inventory) {
                Entry startingItems = new Entry(time);
                startingItems.type = "STARTING_ITEM";
                startingItems.targetname = combatLogName;
                startingItems.valuename = item.id;
                startingItems.slot = entry.slot;
                startingItems.value = (entry.slot < 5 ? 0 : PLAYER_SLOT_OFFSET) + entry.slot;
                startingItems.itemslot = item.slot;
                startingItems.charges = item.num_charges;
                startingItems.secondary_charges = item.num_secondary_charges;
                output(startingItems);
            }
        }
        
        if (!isPlayerStartingItemsWritten.get(entry.slot) && entry.hero_inventory != null) {
            // Making something similar to DOTA_COMBATLOG_PURCHASE for each item in the beginning of the game
            isPlayerStartingItemsWritten.set(entry.slot, true);
            for (Item item : entry.hero_inventory) {
                Entry startingItemsEntry = new Entry(time);
                startingItemsEntry.type = "DOTA_COMBATLOG_PURCHASE";
                startingItemsEntry.slot = entry.slot;
                startingItemsEntry.value = (entry.slot < 5 ? 0 : PLAYER_SLOT_OFFSET) + entry.slot;
                startingItemsEntry.valuename = item.id;
                startingItemsEntry.targetname = combatLogName;
                startingItemsEntry.charges = item.num_charges;
                output(startingItemsEntry);
            }
        }
    }
    
    private void processDotaPlusLevels(Context ctx, Entity playerResource) {
        for (int i = 0; i < NUM_PLAYERS; i++) {
            Integer xp = getEntityProperty(playerResource, "m_vecPlayerTeamData.%i.m_unSelectedHeroBadgeXP", i);
            if (xp == null) {
                xp = 0;
            }
            Long steamId = getEntityProperty(playerResource, "m_vecPlayerData.%i.m_iPlayerSteamID", i);
            if (steamId != null && steamIdToPlayerSlot.containsKey(steamId)) {
                int playerSlot = steamIdToPlayerSlot.get(steamId);
                dotaPlusXpMap.put(playerSlot, xp);
            }
        }
        isDotaPlusProcessed = true;
    }

    private List<Item> getHeroInventory(Context ctx, Entity heroEntity) {
        List<Item> inventoryList = new ArrayList<>(6);

        for (int i = 0; i < MAX_INVENTORY_SLOTS; i++) {
            try {
                Item item = getHeroItem(ctx, heroEntity, i);
                if (item != null) {
                    inventoryList.add(item);
                }
            } catch (Exception e) {
                // Silently skip invalid item slots
            }
        }

        return inventoryList;
    }

    private List<Ability> getHeroAbilities(Context ctx, Entity heroEntity) {
        List<Ability> abilityList = new ArrayList<>(MAX_ABILITIES);
        for (int i = 0; i < MAX_ABILITIES; i++) {
            try {
                Ability ability = getHeroAbility(ctx, heroEntity, i);
                if (ability != null) {
                    abilityList.add(ability);
                }
            } catch (Exception e) {
                // Silently skip invalid ability slots
            }
        }
        return abilityList;
    }

    /**
     * Uses "EntityNames" string table and Entities processor
     * 
     * @param ctx        Context
     * @param heroEntity Hero entity
     * @param idx        0-5 - inventory, 6-8 - backpack, 9-16 - stash
     * @return {@code null} - empty slot. Throws {@link UnknownItemFoundException}
     *         if item information can't be extracted
     */
    private Item getHeroItem(Context ctx, Entity heroEntity, int idx) throws UnknownItemFoundException {
        StringTable entityNamesTable = ctx.getProcessor(StringTables.class).forName(STRING_TABLE_ENTITY_NAMES);
        Entities entities = ctx.getProcessor(Entities.class);

        Integer itemHandle = heroEntity.getProperty(PROPERTY_ITEMS + "." + Util.arrayIdxToString(idx));
        if (itemHandle == null || itemHandle == INVALID_HANDLE) {
            return null;
        }
        
        Entity itemEntity = entities.getByHandle(itemHandle);
        if (itemEntity == null) {
            throw new UnknownItemFoundException(String.format("Can't find item by its handle (%d)", itemHandle));
        }
        
        String itemName = entityNamesTable.getNameByIndex(itemEntity.getProperty(PROPERTY_ENTITY_NAME_INDEX));
        if (itemName == null) {
            throw new UnknownItemFoundException("Can't get item name from EntityName string table");
        }

        Item item = new Item();
        item.id = itemName;
        item.slot = idx;
        int numCharges = itemEntity.getProperty(PROPERTY_CURRENT_CHARGES);
        if (numCharges != 0) {
            item.num_charges = numCharges;
        }
        int numSecondaryCharges = itemEntity.getProperty(PROPERTY_SECONDARY_CHARGES);
        if (numSecondaryCharges != 0) {
            item.num_secondary_charges = numSecondaryCharges;
        }
        return item;
    }

    /**
     * Uses "EntityNames" string table and Entities processor
     * 
     * @param ctx        Context
     * @param heroEntity Hero entity
     * @param idx        0-31 = Hero abilities including talents and special event items
     * @return {@code null} - empty slot. Throws {@link UnknownAbilityFoundException}
     *         if ability information can't be extracted
     */
    private Ability getHeroAbility(Context ctx, Entity heroEntity, int idx) throws UnknownAbilityFoundException {
        StringTable entityNamesTable = ctx.getProcessor(StringTables.class).forName(STRING_TABLE_ENTITY_NAMES);
        Entities entities = ctx.getProcessor(Entities.class);

        Integer abilityHandle;
        String arrayIndex = Util.arrayIdxToString(idx);
        if (heroEntity.hasProperty(PROPERTY_ABILITIES + "." + arrayIndex)) {
            abilityHandle = heroEntity.getProperty(PROPERTY_ABILITIES + "." + arrayIndex);
        } else if (heroEntity.hasProperty(PROPERTY_ABILITIES_VEC + "." + arrayIndex)) {
            abilityHandle = heroEntity.getProperty(PROPERTY_ABILITIES_VEC + "." + arrayIndex);
        } else {
            abilityHandle = INVALID_HANDLE;
        }
        
        if (abilityHandle == null || abilityHandle == INVALID_HANDLE) {
            return null;
        }
        
        Entity abilityEntity = entities.getByHandle(abilityHandle);
        if (abilityEntity == null) {
            throw new UnknownAbilityFoundException(String.format("Can't find ability by its handle (%d)", abilityHandle));
        }
        
        String abilityName = entityNamesTable.getNameByIndex(abilityEntity.getProperty(PROPERTY_ENTITY_NAME_INDEX));
        if (abilityName == null) {
            throw new UnknownAbilityFoundException("Can't get ability name from EntityName string table");
        }

        Ability ability = new Ability();
        ability.id = abilityName;
        ability.abilityLevel = abilityEntity.getProperty(PROPERTY_ABILITY_LEVEL);

        return ability;
    }

    /**
     * Gets a property value from an entity.
     * 
     * @param <T>      The type of the property value
     * @param entity   The entity to get the property from
     * @param property The property name (may contain %i placeholder for array index)
     * @param idx      Optional array index to replace %i placeholder
     * @return The property value or null if not found or on error
     */
    public <T> T getEntityProperty(Entity entity, String property, Integer idx) {
        try {
            if (entity == null) {
                return null;
            }
            if (idx != null) {
                property = property.replace("%i", Util.arrayIdxToString(idx));
            }
            FieldPath fieldPath = entity.getDtClass().getFieldPathForName(property);
            if (fieldPath == null) {
                return null;
            }
            return entity.getPropertyForFieldPath(fieldPath);
        } catch (Exception ex) {
            // Silently return null on error - entity properties may not exist in all game versions
            return null;
        }
    }

    // Ward tracking handlers (consolidated from Wards.java)
    @OnEntityCreated
    public void onWardCreated(Context ctx, Entity e) {
        if (!isWard(e)) return;
        
        FieldPath lifeStatePath;
        
        clearWardCachedState(e);
        ensureWardFieldPathForEntityInitialized(e);
        if ((lifeStatePath = getWardFieldPathForEntity(e)) != null) {
            processWardLifeStateChange(ctx, e, lifeStatePath);
        }
    }
    
    @OnEntityUpdated
    public void onWardUpdated(Context ctx, Entity e, FieldPath[] fieldPaths, int num) {
        if (!isWard(e)) return;
        
        FieldPath p;
        if ((p = getWardFieldPathForEntity(e)) != null) {
            for (int i = 0; i < num; i++) {
                if (fieldPaths[i].equals(p)) {
                    wardToProcess.add(new WardProcessEntityCommand(e, p));
                    break;
                }
            }
        }
    }
    
    @OnEntityDeleted
    public void onWardDeleted(Context ctx, Entity e) {
        if (!isWard(e)) return;
        clearWardCachedState(e);
    }
    
    @OnTickEnd
    public void onWardTickEnd(Context ctx, boolean synthetic) {
        if (!synthetic) return;
        
        WardProcessEntityCommand cmd;
        while ((cmd = wardToProcess.poll()) != null) {
            processWardLifeStateChange(ctx, cmd.entity, cmd.fieldPath);
        }
    }
    
    private void onWardKilled(Context ctx, Entity e, String killerHeroName) {
        Entry wardEntry = buildWardEntry(ctx, e);
        wardEntry.attackername = killerHeroName;
        output(wardEntry);
    }

    private void onWardExistenceChanged(Context ctx, Entity e) {
        output(buildWardEntry(ctx, e));
    }

    // Ward tracking helper methods (consolidated from Wards.java)
    private FieldPath getWardFieldPathForEntity(Entity e) {
        return wardLifeStatePaths.get(e.getDtClass().getClassId());
    }

    private void clearWardCachedState(Entity e) {
        wardCurrentLifeState.remove(e.getIndex());
    }
    
    private void ensureWardFieldPathForEntityInitialized(Entity e) {
        Integer cid = e.getDtClass().getClassId();
        if (!wardLifeStatePaths.containsKey(cid)) {
            wardLifeStatePaths.put(cid, e.getDtClass().getFieldPathForName("m_lifeState"));
        }
    }
    
    private boolean isWard(Entity e) {
        return WARDS_DT_CLASSES.contains(e.getDtClass().getDtName());
    }
    
    private boolean isWardDeath(CombatLogEntry e) {
        return e.getType().equals(DOTAUserMessages.DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_DEATH)
                && WARDS_TARGET_NAMES.contains(e.getTargetName());
    }
    
    private void processWardLifeStateChange(Context ctx, Entity e, FieldPath p) {
        int oldState = wardCurrentLifeState.containsKey(e.getIndex()) ? wardCurrentLifeState.get(e.getIndex()) : 2;
        int newState = e.getPropertyForFieldPath(p);
        if (oldState != newState) {
            switch(newState) {
                case 0:
                    onWardExistenceChanged(ctx, e);
                    break;
                case 1:
                    String killer;
                    if ((killer = wardKillersByWardClass.get(getWardTargetName(e.getDtClass().getDtName())).poll()) != null) {
                        onWardKilled(ctx, e, killer);
                    } else {
                        onWardExistenceChanged(ctx, e);
                    }
                    break;
            }
        }
        
        wardCurrentLifeState.put(e.getIndex(), newState);
    }
    
    private String getWardTargetName(String ward_dtclass_name) {
        return WARDS_TARGET_NAME_BY_DT_CLASS.get(ward_dtclass_name);
    }
    
    private Entry buildWardEntry(Context ctx, Entity wardEntity) {
        Entry entry = new Entry(time);
        boolean isObserver = !wardEntity.getDtClass().getDtName().contains("TrueSight");

        Integer cellX = getEntityProperty(wardEntity, PROPERTY_CELL_X, null);
        Integer cellY = getEntityProperty(wardEntity, PROPERTY_CELL_Y, null);
        Integer cellZ = getEntityProperty(wardEntity, PROPERTY_CELL_Z, null);

        Float vecX = getEntityProperty(wardEntity, PROPERTY_VEC_X, null);
        Float vecY = getEntityProperty(wardEntity, PROPERTY_VEC_Y, null);
        Float vecZ = getEntityProperty(wardEntity, PROPERTY_VEC_Z, null);

        Integer lifeState = getEntityProperty(wardEntity, PROPERTY_LIFE_STATE, null);

        if (cellX != null && cellY != null && cellZ != null) {
            entry.x = getPreciseLocation(cellX, vecX);
            entry.y = getPreciseLocation(cellY, vecY);
            entry.z = getPreciseLocation(cellZ, vecZ);
        }

        entry.type = isObserver ? "obs" : "sen";
        entry.entityleft = (lifeState != null && lifeState == LIFE_STATE_DEAD);
        entry.ehandle = wardEntity.getHandle();

        if (entry.entityleft) {
            entry.type += "_left";
        }

        Integer ownerHandle = getEntityProperty(wardEntity, PROPERTY_OWNER_ENTITY, null);
        Entity ownerEntity = ctx.getProcessor(Entities.class).getByHandle(ownerHandle);
        entry.slot = getPlayerSlotFromEntity(ctx, ownerEntity);

        return entry;
    }
    
    private void ensureGameEventDaoInitialized() throws SQLException {
        if (gameEventDAO == null && matchId != null) {
            gameEventDAO = new GameEventDAO(matchId);
        }
    }
    
    private void insertDatabaseEvent(Entry entry) throws SQLException {
        ensureGameEventDaoInitialized();
        if (gameEventDAO == null) {
            pendingDatabaseEvents.add(entry);
            return;
        }
        gameEventDAO.insertEvent(entry);
        currentBatchSize++;
        if (currentBatchSize >= batchSize) {
            gameEventDAO.executeBatch();
            currentBatchSize = 0;
        }
    }
    
    private void flushPendingDatabaseEvents() throws SQLException {
        if (pendingDatabaseEvents.isEmpty()) {
            return;
        }
        ensureGameEventDaoInitialized();
        if (gameEventDAO == null) {
            return;
        }
        for (Entry pendingEvent : pendingDatabaseEvents) {
            gameEventDAO.insertEvent(pendingEvent);
            currentBatchSize++;
            if (currentBatchSize >= batchSize) {
                gameEventDAO.executeBatch();
                currentBatchSize = 0;
            }
        }
        pendingDatabaseEvents.clear();
    }
    
    private void enqueueDatabaseEvent(Entry entry) throws SQLException {
        if (matchId == null) {
            pendingDatabaseEvents.add(entry);
            return;
        }
        if (!pendingDatabaseEvents.isEmpty()) {
            flushPendingDatabaseEvents();
        }
        insertDatabaseEvent(entry);
    }
    
    private void handleDiscoveredMatchId(Long discoveredMatchId) {
        if (discoveredMatchId == null) {
            return;
        }
        if (matchId != null && !matchId.equals(discoveredMatchId)) {
            System.err.println(String.format(
                "Replay match ID %d differs from configured match ID %d; keeping configured value.",
                discoveredMatchId,
                matchId
            ));
            return;
        }
        if (matchId == null) {
            matchId = discoveredMatchId;
            System.err.println("Replay match ID detected: " + matchId);
            if (databaseEnabled) {
                try {
                    flushPendingDatabaseEvents();
                } catch (SQLException e) {
                    System.err.println("Error flushing pending database events: " + e.getMessage());
                }
            }
        }
    }
    
    private void persistGameInfo(CDemoFileInfo message) {
        if (!databaseEnabled || gameInfoDAO == null) {
            return;
        }
        
        try {
            GameInfoPayload payload = buildGameInfoPayload(message);
            handleDiscoveredMatchId(payload.sourceMatchId);
            if (matchId == null) {
                System.err.println("Replay match ID not available; skipping game info persistence.");
                return;
            }
            gameInfoDAO.upsertGameInfo(
                matchId,
                payload.playbackTime,
                payload.playbackTicks,
                payload.playbackFrames,
                payload.gameMode,
                payload.gameWinner,
                payload.leagueId,
                payload.radiantTeamId,
                payload.direTeamId,
                payload.radiantTeamTag,
                payload.direTeamTag,
                payload.endTime,
                payload.playersJson,
                payload.picksBansJson,
                payload.rawFileInfoJson
            );
            
            // Insert normalized players data
            if (payload.playersList != null && !payload.playersList.isEmpty()) {
                gameInfoDAO.insertPlayers(matchId, payload.playersList);
            }
            
            // Insert normalized picks_bans data
            if (payload.picksBansList != null && !payload.picksBansList.isEmpty()) {
                gameInfoDAO.insertPicksBans(matchId, payload.picksBansList);
            }
        } catch (Exception e) {
            System.err.println("Error saving game info to database: " + e.getMessage());
        }
    }
    
    private GameInfoPayload buildGameInfoPayload(CDemoFileInfo message) {
        GameInfoPayload payload = new GameInfoPayload();
        Map<String, Object> rawInfo = new LinkedHashMap<>();
        
        if (message.hasPlaybackTime()) {
            payload.playbackTime = message.getPlaybackTime();
            rawInfo.put("playback_time", payload.playbackTime);
        }
        if (message.hasPlaybackTicks()) {
            payload.playbackTicks = message.getPlaybackTicks();
            rawInfo.put("playback_ticks", payload.playbackTicks);
        }
        if (message.hasPlaybackFrames()) {
            payload.playbackFrames = message.getPlaybackFrames();
            rawInfo.put("playback_frames", payload.playbackFrames);
        }
        
        if (message.hasGameInfo()) {
            Map<String, Object> gameInfoMap = new LinkedHashMap<>();
            Demo.CGameInfo gameInfo = message.getGameInfo();
            if (gameInfo.hasDota()) {
                Map<String, Object> dotaMap = new LinkedHashMap<>();
                Demo.CGameInfo.CDotaGameInfo dotaInfo = gameInfo.getDota();
                if (dotaInfo.hasMatchId()) {
                    payload.sourceMatchId = dotaInfo.getMatchId();
                    dotaMap.put("match_id", payload.sourceMatchId);
                }
                if (dotaInfo.hasGameMode()) {
                    payload.gameMode = dotaInfo.getGameMode();
                    dotaMap.put("game_mode", payload.gameMode);
                }
                if (dotaInfo.hasGameWinner()) {
                    payload.gameWinner = dotaInfo.getGameWinner();
                    dotaMap.put("game_winner", payload.gameWinner);
                }
                if (dotaInfo.hasLeagueid()) {
                    payload.leagueId = dotaInfo.getLeagueid();
                    dotaMap.put("league_id", payload.leagueId);
                }
                if (dotaInfo.hasRadiantTeamId()) {
                    payload.radiantTeamId = dotaInfo.getRadiantTeamId();
                    dotaMap.put("radiant_team_id", payload.radiantTeamId);
                }
                if (dotaInfo.hasDireTeamId()) {
                    payload.direTeamId = dotaInfo.getDireTeamId();
                    dotaMap.put("dire_team_id", payload.direTeamId);
                }
                if (dotaInfo.hasRadiantTeamTag()) {
                    payload.radiantTeamTag = dotaInfo.getRadiantTeamTag();
                    dotaMap.put("radiant_team_tag", payload.radiantTeamTag);
                }
                if (dotaInfo.hasDireTeamTag()) {
                    payload.direTeamTag = dotaInfo.getDireTeamTag();
                    dotaMap.put("dire_team_tag", payload.direTeamTag);
                }
                if (dotaInfo.hasEndTime()) {
                    payload.endTime = dotaInfo.getEndTime();
                    dotaMap.put("end_time", payload.endTime);
                }
                
                List<Map<String, Object>> playerEntries = new ArrayList<>();
                List<GameInfoDAO.PlayerInfo> playersList = new ArrayList<>();
                for (Demo.CGameInfo.CDotaGameInfo.CPlayerInfo playerInfo : dotaInfo.getPlayerInfoList()) {
                    Map<String, Object> playerData = new LinkedHashMap<>();
                    GameInfoDAO.PlayerInfo normalizedPlayer = new GameInfoDAO.PlayerInfo();
                    
                    if (playerInfo.hasSteamid()) {
                        playerData.put("steam_id", playerInfo.getSteamid());
                        normalizedPlayer.steamId = playerInfo.getSteamid();
                        // Use steamId to get the correct player_slot from the mapping created during initialization
                        Integer mappedPlayerSlot = steamIdToPlayerSlot.get(playerInfo.getSteamid());
                        if (mappedPlayerSlot != null) {
                            normalizedPlayer.playerSlot = mappedPlayerSlot;
                        }
                    }
                    if (playerInfo.hasPlayerName()) {
                        playerData.put("player_name", playerInfo.getPlayerName());
                        normalizedPlayer.playerName = playerInfo.getPlayerName();
                    }
                    if (playerInfo.hasHeroName()) {
                        playerData.put("hero_name", playerInfo.getHeroName());
                        normalizedPlayer.heroName = playerInfo.getHeroName();
                    }
                    if (playerInfo.hasGameTeam()) {
                        playerData.put("game_team", playerInfo.getGameTeam());
                        normalizedPlayer.gameTeam = playerInfo.getGameTeam();
                        
                        // If player_slot wasn't set from steamId mapping, calculate based on team
                        // This is a fallback - ideally steamId matching should work
                        if (normalizedPlayer.playerSlot == null) {
                            // Use a simple counter-based approach as fallback
                            // Note: This may not match slot order perfectly, but it's better than null
                            int[] radiantCount = {0};
                            int[] direCount = {0};
                            for (GameInfoDAO.PlayerInfo p : playersList) {
                                if (p.gameTeam != null) {
                                    if (p.gameTeam == RADIANT_TEAM_ID) {
                                        radiantCount[0]++;
                                    } else if (p.gameTeam == DIRE_TEAM_ID) {
                                        direCount[0]++;
                                    }
                                }
                            }
                            if (normalizedPlayer.gameTeam == RADIANT_TEAM_ID) {
                                normalizedPlayer.playerSlot = radiantCount[0];
                            } else if (normalizedPlayer.gameTeam == DIRE_TEAM_ID) {
                                normalizedPlayer.playerSlot = PLAYER_SLOT_OFFSET + direCount[0];
                            }
                        }
                    }
                    if (playerInfo.hasIsFakeClient()) {
                        playerData.put("is_fake_client", playerInfo.getIsFakeClient());
                        normalizedPlayer.isFakeClient = playerInfo.getIsFakeClient();
                    }
                    
                    if (!playerData.isEmpty()) {
                        playerEntries.add(playerData);
                        playersList.add(normalizedPlayer);
                    }
                }
                if (!playerEntries.isEmpty()) {
                    payload.playersJson = gson.toJson(playerEntries);
                    payload.playersList = playersList;
                    dotaMap.put("players", playerEntries);
                }
                
                List<Map<String, Object>> picksBansEntries = new ArrayList<>();
                List<GameInfoDAO.PickBanInfo> picksBansList = new ArrayList<>();
                for (Demo.CGameInfo.CDotaGameInfo.CHeroSelectEvent event : dotaInfo.getPicksBansList()) {
                    Map<String, Object> pickBanData = new LinkedHashMap<>();
                    GameInfoDAO.PickBanInfo normalizedPickBan = new GameInfoDAO.PickBanInfo();
                    
                    if (event.hasIsPick()) {
                        pickBanData.put("is_pick", event.getIsPick());
                        normalizedPickBan.isPick = event.getIsPick();
                    }
                    if (event.hasTeam()) {
                        pickBanData.put("team", event.getTeam());
                        normalizedPickBan.team = event.getTeam();
                    }
                    if (event.hasHeroId()) {
                        pickBanData.put("hero_id", event.getHeroId());
                        normalizedPickBan.heroId = event.getHeroId();
                    }
                    if (!pickBanData.isEmpty()) {
                        picksBansEntries.add(pickBanData);
                        picksBansList.add(normalizedPickBan);
                    }
                }
                if (!picksBansEntries.isEmpty()) {
                    payload.picksBansJson = gson.toJson(picksBansEntries);
                    payload.picksBansList = picksBansList;
                    dotaMap.put("picks_bans", picksBansEntries);
                }
                
                if (!dotaMap.isEmpty()) {
                    gameInfoMap.put("dota", dotaMap);
                }
            }
            if (!gameInfoMap.isEmpty()) {
                rawInfo.put("game_info", gameInfoMap);
            }
        }
        
        rawInfo.put("raw_text", message.toString());
        payload.rawFileInfoJson = gson.toJson(rawInfo);
        
        return payload;
    }
    
    private static class GameInfoPayload {
        Long sourceMatchId;
        Float playbackTime;
        Integer playbackTicks;
        Integer playbackFrames;
        Integer gameMode;
        Integer gameWinner;
        Integer leagueId;
        Integer radiantTeamId;
        Integer direTeamId;
        String radiantTeamTag;
        String direTeamTag;
        Integer endTime;
        String playersJson;
        String picksBansJson;
        String rawFileInfoJson;
        List<GameInfoDAO.PlayerInfo> playersList;
        List<GameInfoDAO.PickBanInfo> picksBansList;
    }
    
    private void initializeDatabase() {
        try {
            // Check if database is enabled via environment variable
            String dbEnabled = System.getenv("DB_ENABLED");
            databaseEnabled = "true".equalsIgnoreCase(dbEnabled) || "1".equals(dbEnabled);
            
            if (!databaseEnabled) {
                System.err.println("Database integration disabled. Set DB_ENABLED=true to enable.");
                return;
            }
            
            // Initialize database if it doesn't exist
            try {
                DatabaseInitializer.createDatabaseIfNotExists();
            } catch (Exception e) {
                System.err.println("Warning: Could not create database: " + e.getMessage());
            }
            
            // Initialize database tables
            DatabaseInitializer.initializeDatabase();
            
            gameInfoDAO = new GameInfoDAO();
            
        } catch (Exception e) {
            System.err.println("Error initializing database: " + e.getMessage());
            databaseEnabled = false;
            if (gameEventDAO != null) {
                try {
                    gameEventDAO.close();
                } catch (Exception ignored) {
                }
                gameEventDAO = null;
            }
            if (gameInfoDAO != null) {
                try {
                    gameInfoDAO.close();
                } catch (Exception ignored) {
                }
                gameInfoDAO = null;
            }
        }
    }
}
