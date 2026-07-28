package es.boffmedia.teras.dungeon.encounter;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.entity.GeoEnemyVariant;
import es.boffmedia.teras.dungeon.instance.DescentCommand;
import es.boffmedia.teras.dungeon.instance.ElevatorAccess;
import es.boffmedia.teras.dungeon.entity.goal.CloneHopGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
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
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogOption;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * {@code INPCAi.setStandingType}: wander near the spawn when idle rather than stand still — the
     * value {@code EntityAIWander} gates on. Only applied to ambient atmosphere, which has no target
     * to chase and would otherwise freeze in place.
     */
    private static final int AI_WANDER = 1;
    /** How far ambient atmosphere drifts from where it spawned, in blocks. */
    private static final int AMBIENT_WANDER = 6;
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

    /** Whether this entity is a CustomNPCs NPC at all — asked before we claim a right-click. */
    public static boolean isNpc(Entity entity) {
        return entity instanceof noppes.npcs.entity.EntityNPCInterface;
    }

    /**
     * The highest dialogue slot an NPC has. CustomNPCs' own API rejects anything outside 0–11
     * ("Slot needs to be between 0 and 11"), which is the real ceiling — the editor showing fewer
     * rows is a UI limit, not the data's.
     */
    private static final int MAX_DIALOG_SLOT = 11;

    /**
     * Whether this character has a dialogue assigned in the NPC editor — asked at spawn so the mod
     * knows whether to keep out of the right-click.
     *
     * <p><b>Detected, never configured.</b> An NPC's dialogue assignments live in its own saved NBT
     * ({@code DataAdvanced} writes them as {@code NPCDialogOptions}/{@code DialogSlot}), and a stored
     * clone <i>is</i> that NBT — so assigning a dialogue to the clone in the editor persists and
     * every spawned copy carries it. There is nothing for the mod to attach and nothing to put in a
     * config file: the normal CustomNPCs workflow already does the whole job, and this only reads
     * the result.</p>
     *
     * <p><b>Its option commands need command blocks enabled.</b> CustomNPCs routes them through
     * {@code NoppesUtilServer.runCommand}, which refuses outright when
     * {@code MinecraftServer.isCommandBlockEnabled()} is false and says so only to OPs — so it is
     * warned about here, where the failure can still be explained.</p>
     */
    public static boolean hasDialog(Entity npc) {
        if (!available()) {
            return false;
        }
        try {
            if (!(NpcAPI.Instance().getIEntity(npc) instanceof ICustomNpc<?> wrapped)) {
                return false;
            }
            for (int slot = 0; slot <= MAX_DIALOG_SLOT; slot++) {
                if (wrapped.getDialog(slot) != null) {
                    if (npc.level().getServer() != null
                            && !npc.level().getServer().isCommandBlockEnabled()) {
                        Teras.LOGGER.warn("Dungeons: '{}' has a dialogue, but command blocks are "
                                + "DISABLED — CustomNPCs refuses every dialogue-option command, so "
                                + "its choices will do nothing. Set enable-command-block=true.",
                                npc.getName().getString());
                    }
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            Teras.LOGGER.warn("Dungeons: could not read dialogues off a character: {}", t.toString());
            return false;
        }
    }

    /**
     * Writes this floor's real numbers into a dialogue that is about to be shown, replacing every
     * {@code %token%} in its body, its title and its option labels.
     *
     * <p><b>Only ever called on the per-player copy.</b> {@code NoppesUtilServer.openDialog} does
     * {@code dialog = dialog.copy(player)} before serialising it into the packet, and that copy is
     * the one thing in CustomNPCs that belongs to a single player for a single moment — which is
     * what makes a dialogue quoting a price legal at all. The stored dialogue never changes, so two
     * parties on two floors see two prices in the same authored text.</p>
     *
     * <p>The options are the trap: {@code copy()} puts the <i>same</i> {@link DialogOption} objects
     * in the copy's map, so writing a label through it would rewrite the operator's dialogue for
     * everyone, permanently. Each substituted option is therefore rebuilt from its own NBT first.</p>
     */
    /**
     * Removes the descent options this player has not earned, from their own copy of the dialogue.
     *
     * <h2>Why the mod removes them instead of CustomNPCs hiding them</h2>
     *
     * <p>Two independent reasons, both already paid for. A <b>Command</b> option is the only type
     * that can start a run and the one type CustomNPCs will never hide, whatever conditions are set
     * on it (mimir 65). And the scoreboard objective its condition screen would need cannot exist
     * in this modpack at all: whoever creates {@code teras_ascensor}, CustomNPCs announces it a
     * second time and every connected client is disconnected by the duplicate packet (mimir 120).</p>
     *
     * <p>So the branch point moves out of the editor and into here. {@code Dialog.copy} has already
     * made this player their own instance — the same copy {@link #fillDialog} rewrites the numbers
     * in — so dropping entries from its option map affects nobody else and nothing on disk.</p>
     *
     * <h2>What it will not do</h2>
     *
     * <p>Only options whose command is a descent to a stage the ledger refuses. Anything it cannot
     * read is kept ({@link DescentCommand#stageOf} returns 0), because an operator's option quietly
     * vanishing is a far worse failure than one extra row that the command itself then refuses with
     * a message naming who is short.</p>
     */
    public static void gateDescents(Dialog dialog, ServerPlayer player) {
        try {
            if (dialog.options == null || dialog.options.isEmpty()) {
                return;
            }
            HashMap<Integer, DialogOption> kept = new HashMap<>();
            dialog.options.forEach((slot, option) -> {
                int stage = option == null ? 0 : DescentCommand.stageOf(option.command);
                if (stage <= 1 || ElevatorAccess.canBoard(player.getUUID(), stage)) {
                    kept.put(slot, option);
                }
            });
            dialog.options = kept;
        } catch (Throwable t) {
            // A dialogue that shows too much still works; one that throws here shows nothing.
            Teras.LOGGER.warn("Dungeons: could not filter a dialogue's descents: {}", t.toString());
        }
    }

    public static void fillDialog(Dialog dialog, Map<String, String> tokens) {
        try {
            dialog.text = substitute(dialog.text, tokens);
            dialog.title = substitute(dialog.title, tokens);
            HashMap<Integer, DialogOption> rendered = new HashMap<>();
            dialog.options.forEach((slot, option) -> rendered.put(slot, filled(option, tokens)));
            dialog.options = rendered;
        } catch (Throwable t) {
            Teras.LOGGER.warn("Dungeons: could not fill a dialogue's numbers: {}", t.toString());
        }
    }

    private static DialogOption filled(DialogOption option, Map<String, String> tokens) {
        String label = substitute(option.title, tokens);
        if (label.equals(option.title)) {
            return option;
        }
        DialogOption copy = new DialogOption();
        copy.readNBT(option.writeNBT());
        // writeNBT covers the five fields the client is sent; these three it leaves behind.
        copy.id = option.id;
        copy.slot = option.slot;
        copy.option = option.option;
        copy.title = label;
        return copy;
    }

    private static String substitute(String text, Map<String, String> tokens) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        String filled = text;
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            filled = filled.replace(token.getKey(), token.getValue());
        }
        return filled;
    }

    /**
     * Installs the dungeon's <i>characters</i> — the ones that talk rather than fight — as clones.
     * Same install-once/spawn-per-floor contract as the bestiary, which is what makes El Acreedor
     * the same being on every floor without any entity persisting between them.
     */
    public static int installCharacters(ServerLevel level, int tab, List<CharacterPreset> presets,
                                        boolean overwrite) {
        NpcAPI api = NpcAPI.Instance();
        List<String> existing = clonesIn(tab);
        int installed = 0;
        for (CharacterPreset preset : presets) {
            if (!overwrite && existing.contains(preset.id())) {
                continue;
            }
            try {
                ICustomNpc<?> npc = api.createNPC(level);
                INPCDisplay display = npc.getDisplay();
                display.setName(preset.displayName());
                display.setSkinTexture(preset.skinTexture());
                display.setSize(preset.size());
                display.setShowName(1);
                INPCStats stats = npc.getStats();
                stats.setMaxHealth(preset.health());
                // A character is not a combatant: no aggro, no faction to be hostile to, and it
                // stays dead if something manages to kill it rather than resurrecting mid-run.
                stats.setAggroRange(0);
                stats.setRespawnType(RESPAWN_NONE);
                stats.setHideDeadBody(true);
                stats.getMelee().setStrength(0);
                npc.getAi().setWalkingSpeed(0);
                npc.getAi().setReturnsHome(true);
                npc.getAi().setStandingType(0);
                npc.storeAsClone(tab, preset.id());
                npc.despawn();
                installed++;
            } catch (Exception e) {
                Teras.LOGGER.error("Dungeons: could not install character '{}': {}",
                        preset.id(), e.toString());
            }
        }
        return installed;
    }

    /** A talking dungeon character, as data. */
    public record CharacterPreset(String id, String displayName, String skinTexture,
                                  int size, int health) {}

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
                applyLook(npc, DungeonEnemyPacks.byId(name));
            }
            Entity entity = spawned == null ? null : (Entity) spawned.getMCEntity();
            if (entity instanceof Mob mob
                    && DungeonEnemyPacks.hopsLikeASlime(DungeonEnemyPacks.byId(name))) {
                mob.goalSelector.addGoal(2, new CloneHopGoal(mob));
            }
            return entity;
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

    /**
     * Re-stamps how a shipped enemy <i>looks</i> onto the clone that just spawned.
     *
     * <p>A clone is written once by {@code enemigos instalar}, which skips ids already present, so
     * every later change to the bestiary's size or model sat in the jar and never reached play —
     * the size of a preset appeared not to do anything at all, and the fix was an operator
     * remembering an {@code instalar sobrescribir} nobody had a reason to suspect. This is the same
     * argument as {@code RESPAWN_NONE} above: what the mod ships is authoritative on every spawn,
     * and stale clone data cannot quietly outlive it.</p>
     *
     * <p>Deliberately only the render identity — size, model, skin. Stats, AI and drops stay with
     * the clone, so an admin retuning a built-in in the NPC editor keeps their work; a preset the
     * bestiary does not know is left entirely alone.</p>
     */
    private static void applyLook(ICustomNpc<?> npc, EnemyPreset preset) {
        if (preset == null) {
            return;
        }
        try {
            INPCDisplay display = npc.getDisplay();
            display.setSize(preset.size());
            display.setSkinTexture(preset.skinTexture());
            if (!preset.entityModel().isEmpty()) {
                display.setModel(preset.entityModel());
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not refresh clone '{}': {}", preset.id(), e.toString());
        }
    }

    private static void apply(IWorld world, ICustomNpc<?> npc, EnemyPreset preset, int factionId) {
        INPCDisplay display = npc.getDisplay();
        display.setName(preset.displayName());
        display.setSkinTexture(preset.skinTexture());
        display.setSize(preset.size());
        // Ambience wears no nameplate: a floating "Murciélago" label would give the game away that
        // the bat is anything other than scenery.
        display.setShowName(preset.isAmbient() ? 0 : 1);
        if (!preset.entityModel().isEmpty()) {
            // Render as a vanilla mob. CustomNPCs' ModelData resolves the id to an EntityType and
            // draws that entity, so the clone looks like a silverfish/slime/bat while staying a
            // CustomNPCs entity that Pixelmon's monster-replacement never touches.
            display.setModel(preset.entityModel());
        }
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
        if (preset.isAmbient()) {
            // Atmosphere has no target (aggroRange 0), so without a wander range it stands frozen
            // where it spawned. A range lets it drift around the room. True flight is beyond a
            // CustomNPCs NPC — a bat clone walks — but a wandering bat reads far better than a
            // statue with a nameplate.
            npc.getAi().setWanderingRange(AMBIENT_WANDER);
            npc.getAi().setStandingType(AI_WANDER);
        }

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

        // Ambient atmosphere stays factionless so it is nobody's enemy and nothing's target; its
        // aggroRange of 0 already keeps it from noticing players. A combat enemy joins the dungeon
        // faction so the party starts hostile to it.
        if (factionId >= 0 && !preset.isAmbient()) {
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
