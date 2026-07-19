package es.boffmedia.teras.dungeon.encounter;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.entity.GeoEnemyVariant;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.data.INPCDisplay;
import noppes.npcs.api.entity.data.INPCInventory;
import noppes.npcs.api.entity.data.INPCRanged;
import noppes.npcs.api.entity.data.INPCStats;
import noppes.npcs.api.handler.data.IFaction;

import java.util.List;

/**
 * The only dungeon class allowed to import {@code noppes.npcs.*} — the same isolation rule as
 * {@code WorldEditBridge} / {@code IvKartVehicleService}. Callers MUST check {@link #available()}
 * before naming this class so it never links on servers without CustomNPCs.
 *
 * <p>Two jobs: spawning authored enemies (clones, by tab and name) and <i>installing</i> the
 * built-in bestiary as clones in the first place, so a fresh server has enemies without anyone
 * opening the NPC editor. Installed clones are ordinary CustomNPCs data afterwards — editing or
 * replacing them in-game is the intended workflow, and the installer never overwrites by default.
 * Verified against the 1.21.1 unofficial build (file 7411561), which Teras already ships against
 * for quests.</p>
 */
public final class CnpcBridge {
    private CnpcBridge() {}

    /** Faction the installed bestiary belongs to, created on demand. */
    private static final String FACTION_NAME = "Mazmorra";

    /** CustomNPCs' damage-type slots for {@code INPCStats.setResistance}. */
    private static final int RESIST_MELEE = 0;
    private static final int RESIST_ARROW = 1;
    /** {@code INPCAi.setMovingType}: stand and shoot rather than close to melee range. */
    private static final int MOVING_TYPE_STANDING = 0;
    /**
     * {@code INPCStats.setRespawnType}: 0 always respawns, 1 respawns by day, 2 by night, 3 dies
     * like a vanilla mob — read off {@code EntityNPCInterface.tickDeath}, which only takes the
     * vanilla death path for 3/4 and otherwise parks a hidden corpse that {@code reset()}s itself
     * when its timer and the daylight condition line up. The bestiary originally shipped with 1
     * ("keeps a killed dungeon enemy dead", it claimed) and in a daylit dimension every kill came
     * back ~20s later — respawning into whatever floor now occupied its coordinates, which is how
     * playtest 3 found enemies embedded in the ground of treasure rooms.
     */
    private static final int RESPAWN_NONE = 3;

    public static boolean available() {
        return ModList.get().isLoaded("customnpcs") && NpcAPI.IsAvailable();
    }

    /** Spawns clone {@code name} from {@code tab}; null when the clone does not exist. */
    public static Entity spawnClone(ServerLevel level, double x, double y, double z, int tab, String name) {
        try {
            NpcAPI api = NpcAPI.Instance();
            IEntity<?> spawned = api.getClones().spawn(x, y, z, tab, name, api.getIWorld(level));
            if (spawned instanceof ICustomNpc<?> npc) {
                // Enforced on every spawn, not only at install: clones written before the
                // RESPAWN_NONE fix — or hand-edited in the NPC editor — carry a respawning type,
                // and a dungeon enemy that resurrects itself puts untracked mobs in cleared rooms
                // and, across floors, inside the next floor's geometry.
                npc.getStats().setRespawnType(RESPAWN_NONE);
            }
            return spawned == null ? null : (Entity) spawned.getMCEntity();
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not spawn CNPC clone '{}' (tab {}): {}", name, tab, e.toString());
            return null;
        }
    }

    /** Clone names already present in {@code tab}. */
    public static List<String> clonesIn(int tab) {
        try {
            // The handler has no list method; the controller behind it does, and this is read-only.
            return noppes.npcs.controllers.ServerCloneController.Instance.getClones(tab);
        } catch (Throwable t) {
            Teras.LOGGER.warn("Dungeons: could not list CNPC clones in tab {}: {}", tab, t.toString());
            return List.of();
        }
    }

    /**
     * Writes the bestiary into {@code tab} as clones. Returns how many were installed; presets
     * that already exist are skipped unless {@code overwrite}.
     *
     * <p>Each NPC is built, configured, stored and then despawned — the clone is the artifact, the
     * entity is scaffolding. A failure on one preset is logged and does not abort the rest.</p>
     */
    public static int install(ServerLevel level, int tab, List<EnemyPreset> presets, boolean overwrite) {
        NpcAPI api = NpcAPI.Instance();
        IWorld world = api.getIWorld(level);
        List<String> existing = clonesIn(tab);
        int factionId = ensureFaction(api);
        int installed = 0;
        for (EnemyPreset preset : presets) {
            if (!overwrite && existing.contains(preset.id())) {
                continue;
            }
            try {
                ICustomNpc<?> npc = api.createNPC(level);
                apply(world, npc, preset, factionId);
                npc.storeAsClone(tab, preset.id());
                npc.despawn();
                installed++;
            } catch (Exception e) {
                Teras.LOGGER.error("Dungeons: could not install enemy '{}': {}", preset.id(), e.toString());
            }
        }
        return installed;
    }

    private static void apply(IWorld world, ICustomNpc<?> npc, EnemyPreset preset, int factionId) {
        INPCDisplay display = npc.getDisplay();
        display.setName(preset.displayName());
        display.setSkinTexture(preset.skinTexture());
        display.setSize(preset.size());
        display.setShowName(1);
        if (!preset.geoModel().isEmpty()) {
            // The CNPC Gecko addon renders this when installed; without it the NPC keeps the skin
            // above and nothing is lost but the animation.
            GeoEnemyVariant variant = GeoEnemyVariant.of(preset.geoModel());
            CnpcGeckoBridge.applyModel(display,
                    "teras:" + variant.model(), "teras:" + variant.animation(),
                    0.6f * variant.scale(), 1.95f * variant.scale());
        }

        INPCStats stats = npc.getStats();
        stats.setMaxHealth(preset.health());
        stats.setAggroRange(preset.aggroRange());
        stats.setRespawnType(RESPAWN_NONE);
        stats.setHideDeadBody(true);
        stats.getMelee().setStrength(preset.strength());
        stats.getMelee().setDelay(preset.delay());
        stats.getMelee().setRange(preset.range());
        stats.getMelee().setKnockback(preset.knockback());

        if (preset.meleeEffect() > 0) {
            stats.getMelee().setEffect(preset.meleeEffect(), preset.meleeEffectTime(), 0);
        }
        // Resistances are shares ignored, 0..1 — the same scale the NPC editor's sliders use.
        if (preset.arrowResist() > 0) {
            stats.setResistance(RESIST_ARROW, preset.arrowResist());
        }
        if (preset.meleeResist() > 0) {
            stats.setResistance(RESIST_MELEE, preset.meleeResist());
        }
        // Ranged is off unless the preset asks for it: strength 0 leaves a melee-only enemy, which
        // is what every preset was before traits existed.
        if (preset.isRanged()) {
            INPCRanged ranged = stats.getRanged();
            ranged.setStrength(preset.rangedStrength());
            ranged.setSpeed(preset.rangedSpeed());
            ranged.setBurst(preset.rangedBurst());
            ranged.setDelay(preset.rangedDelay(), preset.rangedDelay() + 20);
            ranged.setRange(preset.aggroRange());
            ranged.setHasGravity(true);
            if (preset.rangedEffect() > 0) {
                ranged.setEffect(preset.rangedEffect(), preset.rangedEffectTime(), 0);
            }
            // Without this the NPC walks into melee range before ever loosing a shot.
            npc.getAi().setMovingType(MOVING_TYPE_STANDING);
        }
        if (preset.bossBar() > 0) {
            display.setBossbar(preset.bossBar());
        }

        npc.getAi().setWalkingSpeed(preset.speed());
        npc.getAi().setAttackLOS(true);
        npc.getAi().setReturnsHome(false);
        npc.getAi().setCanSwim(true);
        npc.getAi().setLeapAtTarget(preset.leaps());

        INPCInventory inventory = npc.getInventory();
        if (!preset.mainHand().isEmpty()) {
            inventory.setRightHand(world.createItem(preset.mainHand(), 1));
        }
        if (!preset.helmet().isEmpty()) {
            inventory.setArmor(0, world.createItem(preset.helmet(), 1));
        }
        if (!preset.dropItem().isEmpty()) {
            inventory.setDropItem(0, world.createItem(preset.dropItem(), 1), preset.dropChance());
        }
        inventory.setExp(preset.expMin(), preset.expMax());

        if (factionId >= 0) {
            npc.setFaction(factionId);
        }
    }

    /**
     * The dungeon faction, created once. Its default points sit at the bottom of the scale so
     * players start hostile to it; the spawner also aggroes enemies explicitly, so combat does not
     * depend on getting CustomNPCs' faction thresholds right.
     */
    private static int ensureFaction(NpcAPI api) {
        try {
            for (IFaction faction : api.getFactions().list()) {
                if (FACTION_NAME.equals(faction.getName())) {
                    return faction.getId();
                }
            }
            IFaction created = api.getFactions().create(FACTION_NAME, 0);
            created.setAttackedByMobs(false);
            created.save();
            Teras.LOGGER.info("Dungeons: created CustomNPCs faction '{}' (id {})",
                    FACTION_NAME, created.getId());
            return created.getId();
        } catch (Throwable t) {
            Teras.LOGGER.warn("Dungeons: could not prepare the '{}' faction, leaving the default: {}",
                    FACTION_NAME, t.toString());
            return -1;
        }
    }
}
