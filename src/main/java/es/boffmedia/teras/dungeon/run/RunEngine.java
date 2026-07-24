package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.BuiltDungeon;
import es.boffmedia.teras.dungeon.build.DoorCarver;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.encounter.EnemySpawner;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.economy.EconomyStore;
import es.boffmedia.teras.net.DungeonMapPayload;
import es.boffmedia.teras.net.DungeonWalletPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Minecraft half of the floor loop: feeds {@link RunCore} with cell entries from player
 * positions, implements its callbacks against the built floor, and keeps the enemy ledger honest
 * — deaths through {@link LivingDeathEvent}, everything else (despawns, unloads, scripted
 * removals) through a periodic existence sweep, so a room can never stay sealed over an enemy
 * that silently stopped existing.
 *
 * <p>The way down is a real hole. On a piso with a sala del sello the boss's death lights the
 * seal, retracts the pit's grate and carves open the walled-off exit chamber; without one it
 * falls back to carving the pit in the arena. Either way the first member through the pit is parked below the floor and
 * starts the straggler bell, and the party descends together at zero — or the moment everyone has
 * jumped. Player deaths respawn at the floor's start room with the configured money penalty.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class RunEngine {
    private RunEngine() {}

    /**
     * Every tick. A five-tick sample let a sprinting player cover more than a block between
     * checks, which showed up as the doors closing noticeably after they were already inside. The
     * work is a cell lookup and a couple of box tests per party member.
     */
    private static final int MOVE_SCAN_TICKS = 1;
    private static final int SWEEP_TICKS = 20;
    /**
     * Slack around a doorway's block volume when testing whether a player is standing in it.
     * Small on purpose: it only has to cover being pressed against the opening, not to hold the
     * room open while someone walks in.
     */
    private static final double DOOR_MARGIN = 0.1;

    private static final Map<Integer, ActiveFloor> FLOORS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> RESPAWN_AT_START = new HashMap<>();
    private static long tick;

    /**
     * Per-floor state. Everything here dies with the floor — which is exactly right for what the
     * shop sold this floor (map, compass, insurance are all "for this floor"), and exactly wrong
     * for the purse and the hearts a devil deal took, which live on {@link DungeonRun} and survive
     * the descent.
     */
    static final class ActiveFloor {
        final DungeonRun run;
        final BuiltDungeon built;
        final ServerLevel level;
        final RunCore core;
        final Map<UUID, Room> enemyRooms = new HashMap<>();
        final Map<UUID, GridPos> lastCell = new HashMap<>();
        final java.util.Set<DoorEdge> openedSecrets = new java.util.HashSet<>();
        final DungeonShop shop = new DungeonShop();
        /** Rewards you take off a stand, rather than items dropped on the floor. */
        final RewardPedestals pedestals = new RewardPedestals();
        /** The bar over a live boss or mini-boss. */
        final DungeonBossBars bossBars = new DungeonBossBars();
        /** Bought at the shop: reveal the floor's layout / its special rooms on the minimap. */
        boolean mapRevealed;
        boolean compassRevealed;
        /** Halves the death penalty for this floor. */
        boolean seguro;
        /** Sacrifice plates: steps taken per room, and the rooms that have already paid out. */
        final Map<Room, Integer> sacrificeSteps = new HashMap<>();
        final java.util.Set<Room> sacrificeSpent = new java.util.HashSet<>();
        /**
         * Per-player, per-fixture cooldowns, so standing on a plate is a decision per step rather
         * than a stream of damage. Keyed by fixture as well as player: one map would have the
         * arcade's cooldown swallowing a sacrifice step taken half a second later.
         */
        final Map<String, Long> fixtureCooldown = new HashMap<>();
        /** Arcade machines that have given up, per room. */
        final java.util.Set<Room> arcadeBroken = new java.util.HashSet<>();
        /** Devil pedestals already claimed. */
        final java.util.Set<Room> devilClaimed = new java.util.HashSet<>();
        /** Who has already bled to get through a curse door. Per floor, so descending resets it. */
        final java.util.Set<UUID> curseTollPaid = new java.util.HashSet<>();
        /** The curse room's market: its offers, and the pedestal that undoes them. */
        final CurseMarket market = new CurseMarket();
        /** Missing-marker warnings already logged, so the tick loop cannot repeat one. */
        final java.util.Set<String> warnedMarkers = new java.util.HashSet<>();
        /**
         * How cleanly this floor was played — the floor-local half of the Acreedor/Orden odds
         * (PISOS §63c). It lives here because {@code ActiveFloor} is rebuilt every floor, so the
         * reset is free and there is no way to leak last floor's blood into this one's score.
         *
         * <p>The same signals PRODUCCION §4.2's <i>broche de sala</i> and §5.2's deathless-floor
         * esquirla want: one tracker, several consumers. Do not grow a second one.</p>
         */
        boolean tookDamage;
        boolean tookDamageInBossFight;
        boolean someoneDied;
        boolean soldHearts;
        /**
         * Set only while the mod charges one of its own tolls. A price the party <i>chose</i> to
         * pay is not a wound the floor gave them, so it must not cost them their grace — and the
         * toll cannot be told apart by damage type, because the curse door, the sacrifice plate and
         * an enemy's spell all arrive as {@code magic()}.
         */
        boolean chargingToll;
        /** Whether one of the two satellite mercies has been taken, closing the other's door. */
        boolean forkResolved;
        /**
         * How many of la Orden's gifts are still unclaimed on this floor, and which are already
         * gone. {@code -1} means her font has not been read yet: the count comes from the purity
         * tier of the floor just played, and asking for it before anyone has approached her would
         * fix the number before the run is even sure she is here.
         */
        int ordenPicksLeft = -1;
        final java.util.Set<String> ordenTaken = new java.util.HashSet<>();
        /** El Cobrador, while he is standing on this floor. Null when the run owes nothing. */
        UUID collector;

        boolean advancing;
        /** Members already through the pit, parked below the floor until the party descends. */
        final java.util.Set<UUID> descended = new java.util.HashSet<>();
        /**
         * The straggler bell (PRODUCCION §10.6): the first member down the pit starts it, and at
         * zero — or when everyone has jumped — the whole party descends. 0 while not running.
         */
        long descentDeadline;
        int descentTotalTicks;
        net.minecraft.server.level.ServerBossEvent descentBar;

        ActiveFloor(DungeonRun run, BuiltDungeon built, ServerLevel level) {
            this.run = run;
            this.built = built;
            this.level = level;
            this.core = new RunCore(built.layout(), new FloorCallbacks(this));
        }

        BuiltDungeon built() {
            return built;
        }

        ServerLevel level() {
            return level;
        }

        DungeonRun run() {
            return run;
        }
    }

    /** Starts (or replaces, on stage advance) the loop for a run's built floor. */
    public static void register(DungeonRun run, BuiltDungeon built, ServerLevel level) {
        ActiveFloor floor = new ActiveFloor(run, built, level);
        FLOORS.put(run.id(), floor);
        floor.core.start();
        floor.shop.stock(floor);
        broadcastWallet(floor);
        sendCollector(floor);
    }

    /**
     * El Cobrador, when a debt has stood unpaid long enough (PISOS §63b, PRODUCCION §6.4). He is
     * waiting at the entrance rather than placed in a room: the party arrives to find him, which is
     * the difference between being hunted and meeting a wandering monster.
     *
     * <p><b>Outside the kill ledger, deliberately.</b> He is not registered to a room, so no
     * doorway seals behind him and no room stays uncleared while he lives — a party that would
     * rather run from him and pay later is making the decision the mechanic is for. Killing him
     * forgives the debt outright; see {@link #onLivingDeath}.</p>
     */
    private static void sendCollector(ActiveFloor floor) {
        if (floor.run.deuda() <= 0
                || floor.run.floorsSinceBorrow() < DungeonsConfig.debtFloorsToCollect()) {
            return;
        }
        BlockPos at = floor.built.partySpawn(floor.built.layout().start());
        es.boffmedia.teras.dungeon.encounter.SpawnTables.SpawnEntry entry =
                es.boffmedia.teras.dungeon.encounter.SpawnTables.parseSpec("geo:cobrador");
        if (entry == null) {
            return;
        }
        // On the landing itself, not beside it: the party spawn is the one position on the floor
        // known to be clear — an offset from it can be inside the start chamber's own furniture,
        // and a collector suffocating in a wall forgives the debt for free.
        net.minecraft.world.entity.Entity collector = es.boffmedia.teras.dungeon.encounter
                .EnemySpawner.spawnSummon(floor.level, entry, at);
        if (collector == null) {
            return;
        }
        floor.collector = collector.getUUID();
        message(floor, "§5Alguien os espera en la entrada. §7Viene a cobrar.");
        playAt(floor, at, DungeonSound.DEVIL_OPENED, 1.0f);
    }

    public static void unregister(int runId) {
        ActiveFloor floor = FLOORS.remove(runId);
        if (floor != null) {
            removeRemainingEnemies(floor);
            // The floor's own teardown sweeps these with everything else, but a stage advance
            // discards the old pad a moment after this: clearing them here closes that window.
            floor.shop.despawnDisplays(floor);
            floor.pedestals.despawn(floor);
            floor.market.despawnDisplays(floor);
            floor.bossBars.clear();
            // Characters are floor-scoped like every other fixture: the entities go with the
            // floor's sweep, and this drops what the click router remembered about them.
            DungeonNpcs.clear(runId);
            clearDescentBar(floor);
            for (UUID member : floor.run.party().keySet()) {
                ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, DungeonMapPayload.hidden());
                    PacketDistributor.sendToPlayer(player, DungeonWalletPayload.hidden());
                    DungeonNpcs.clearConditions(player);
                }
            }
        }
    }

    /**
     * Everything a run did to a player's body or inventory, undone. Called on every way out —
     * walking out, the run ending, being sent home by the boot sweep — because a devil deal's
     * missing hearts, a shop blessing and a pocketful of dungeon potions must not survive into the
     * overworld.
     */
    public static void clearRunEffects(ServerPlayer player) {
        DungeonHealth.clearHpDebt(player);
        DungeonShop.clearBlessings(player);
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof es.boffmedia.teras.dungeon.item.DungeonPotionItem
                    || stack.is(es.boffmedia.teras.init.ItemInit.MONEDA_MAZMORRA.get())
                    || stack.is(es.boffmedia.teras.init.ItemInit.CARGA_ROMPEMUROS.get())) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * The party's purse and each member's afflictions, to their HUD. Called on every change — the
     * wallet is shared, so one player's purchase moves everyone's counter.
     *
     * <p>Built per member rather than once: the purse is common but afflictions are not. A player
     * carrying Plomo must see it and nobody else must, so the packet cannot be a single broadcast.</p>
     */
    static void broadcastWallet(ActiveFloor floor) {
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player == null) {
                continue;
            }
            List<String> carried = new java.util.ArrayList<>();
            floor.run.afflictions().carried().forEach(a -> carried.add(a.nombre()));
            floor.run.stateOf(member).afflictions().carried().forEach(a -> carried.add(a.nombre()));
            PacketDistributor.sendToPlayer(player, new DungeonWalletPayload(true,
                    floor.run.wallet().coins(), floor.run.wallet().wallCharges(), carried));
        }
    }

    /**
     * Afflictions changed. Same packet as the purse, because they change on the same events — the
     * curse room pays coins for taking one and charges coins to shed one.
     */
    static void syncAfflictions(ActiveFloor floor) {
        broadcastWallet(floor);
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player != null) {
                Afflictions.apply(floor.run, player);
            }
        }
    }

    /** A line to everyone still in the run. */
    static void message(ActiveFloor floor, String text) {
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(Component.literal(text));
            }
        }
    }

    /** A cue at a world position, for the things that happen at a block rather than to a room. */
    static void playAt(ActiveFloor floor, BlockPos pos, DungeonSound cue, float pitch) {
        SoundEvent event = soundEvent(cue.name());
        if (event != null) {
            floor.level.playSound(null, pos, event, SoundSource.BLOCKS,
                    DungeonsConfig.soundVolume(), pitch);
        }
    }

    public static RunCore coreOf(int runId) {
        ActiveFloor floor = FLOORS.get(runId);
        return floor == null ? null : floor.core;
    }

    /**
     * Takes an enemy summoned mid-fight into the ledger of whatever room its summoner is fighting
     * in. False when the summoner belongs to no tracked room — the caller must then get rid of the
     * add rather than leave a stray mob standing in the floor.
     */
    public static boolean registerSummon(Entity summoner, Entity add) {
        for (ActiveFloor floor : FLOORS.values()) {
            Room room = floor.enemyRooms.get(summoner.getUUID());
            if (room == null) {
                continue;
            }
            if (!floor.core.enemyAdded(room)) {
                return false;
            }
            floor.enemyRooms.put(add.getUUID(), room);
            return true;
        }
        return false;
    }

    /**
     * A splitting enemy's children join the ledger their parent was in.
     *
     * <p>{@code EnemySpawner} returns the entities it spawned <i>as</i> the kill ledger, so a slime
     * that dies and spawns children breaks it two ways at once: the children are not in the ledger,
     * so the room either clears with them still bouncing or — because {@code InstanceGuard} refuses
     * a mob it does not recognise — never spawn at all. Both are the same fix: tag each child as
     * ours before it joins the level, and add it to the parent's room.</p>
     *
     * <p>The room is found from the parent's position, not from {@code enemyRooms}: the parent left
     * the ledger when it died, a good second before {@code Slime.remove} fires this event. Reading
     * the position sidesteps that gap and, better, makes the last-enemy case correct for free —
     * {@link RunCore#enemyAdded} refuses a room that has already cleared, so the children are
     * dropped rather than spawned behind a party that has already been let out.</p>
     */
    @SubscribeEvent
    public static void onMobSplit(net.neoforged.neoforge.event.entity.living.MobSplitEvent event) {
        if (!event.getParent().getTags().contains(EnemySpawner.DUNGEON_TAG)) {
            return;
        }
        for (ActiveFloor floor : FLOORS.values()) {
            if (floor.level != event.getParent().level()) {
                continue;
            }
            Room room = roomAt(floor, event.getParent().blockPosition());
            if (room == null) {
                return;
            }
            var children = event.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                var child = children.get(i);
                child.addTag(EnemySpawner.DUNGEON_TAG);
                if (floor.core.enemyAdded(room)) {
                    floor.enemyRooms.put(child.getUUID(), room);
                } else {
                    children.remove(i);
                }
            }
            return;
        }
    }

    /**
     * Adds a nest hatchling to a room's kill ledger. Takes the room outright rather than deriving
     * it the way {@link #registerSummon} does from its summoner — a nest is a block, and the caller
     * already knows which room it sealed.
     *
     * <p>False means nothing is tracking that room any more (a floor that ended mid-hatch), and the
     * caller must discard the entity rather than leave a stray mob standing in a swept floor.</p>
     */
    public static boolean registerNestSpawn(ServerLevel level, Room room, Entity add) {
        for (ActiveFloor floor : FLOORS.values()) {
            if (floor.level != level) {
                continue;
            }
            if (!floor.core.enemyAdded(room)) {
                return false;
            }
            floor.enemyRooms.put(add.getUUID(), room);
            return true;
        }
        return false;
    }

    /** Plays a cue positioned on an enemy, for the ability layer's telegraphs. */
    public static void playAbilityCue(Entity source, DungeonSound cue) {
        playAbilityCue(source, cue, 1.0f, 1.0f);
    }

    /** {@code volumeMul} scales the configured volume; {@code pitch} drops it for menace. */
    public static void playAbilityCue(Entity source, DungeonSound cue, float volumeMul, float pitch) {
        SoundEvent event = soundEvent(cue.name());
        if (event != null && source.level() instanceof ServerLevel level) {
            level.playSound(null, source.getX(), source.getY(), source.getZ(), event,
                    SoundSource.HOSTILE, DungeonsConfig.soundVolume() * volumeMul, pitch);
        }
    }

    /**
     * Live state of a run's floor, for {@code /teras dungeon debug} — room states with their
     * remaining enemies, so "the doors did not close" can be read off the server instead of
     * guessed at from the outside.
     */
    public static List<String> describe(int runId) {
        ActiveFloor floor = FLOORS.get(runId);
        if (floor == null) {
            return List.of();
        }
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Run " + runId + " — etapa " + floor.run.stage()
                + ", dificultad " + floor.level.getDifficulty()
                + ", trampilla " + (floor.core.isTrapdoorOpen() ? "abierta" : "cerrada")
                + ", enemigos vivos " + floor.enemyRooms.size());
        for (Room room : floor.built.layout().rooms()) {
            RoomState state = floor.core.state(room);
            if (state == RoomState.UNDISCOVERED) {
                continue;
            }
            lines.add("  " + room + " — " + state
                    + (state == RoomState.IN_COMBAT
                            ? " (" + floor.core.enemiesRemaining(room) + " enemigos)" : ""));
        }
        return lines;
    }

    /**
     * Failures are contained to the run that caused them. A dungeon is a side attraction; a bug in
     * one must never take the whole server down, which is exactly what happened the first time a
     * party descended a floor.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick++;
        boolean scan = tick % MOVE_SCAN_TICKS == 0;
        boolean sweep = tick % SWEEP_TICKS == 0;
        if (!scan && !sweep) {
            return;
        }
        for (ActiveFloor floor : List.copyOf(FLOORS.values())) {
            try {
                if (scan) {
                    scanPlayers(floor);
                    // On the existing scan rather than a tick of its own: the bar follows a health
                    // value that changes on somebody else's schedule, and a player who walks in
                    // halfway has to be added to it.
                    floor.bossBars.tick(floor);
                    floor.market.tick(floor);
                }
                if (sweep) {
                    abandonDeserted(floor);
                    sweepEnemies(floor);
                }
            } catch (Throwable t) {
                Teras.LOGGER.error("Dungeons: run {} failed while ticking; ending it", floor.run.id(), t);
                abandon(floor);
            }
        }
    }

    /** Pulls a broken run out of the world without trusting any more of its state. */
    private static void abandon(ActiveFloor floor) {
        try {
            DungeonRunManager.end(floor.level.getServer(), floor.run.id());
        } catch (Throwable t) {
            Teras.LOGGER.error("Dungeons: could not cleanly end run {}", floor.run.id(), t);
        }
        // After, not before. Removing the floor first meant end()'s unregister found nothing and
        // took nothing down — so a run that broke mid-boss left its bar on every screen until relog.
        // Idempotent, so it costs nothing when end() already did it, and it still guarantees the
        // broken floor cannot tick again when end() refused the run.
        unregister(floor.run.id());
    }

    /** Below this share of average party health, the floor ends with the party limping. */
    private static final float LOW_HP_FRACTION = 1f / 3f;

    /** The floor a run is currently on, for the fixtures and characters that need to find it back. */
    static ActiveFloor activeFloor(int runId) {
        return FLOORS.get(runId);
    }

    /**
     * Scores the floor's purity as it is played: any damage a party member actually takes costs the
     * floor its flawless marks, and damage taken while the arena is sealed costs the boss-fight one
     * as well (PISOS §63c — a clean floor pulls la Orden, a bloody one pulls el Acreedor).
     *
     * <p>Two sources are deliberately not wounds. The scripted descent lands the party on the next
     * floor, and the mod's own tolls are prices they chose to pay; letting either count would mean
     * the game's staging disqualifies a party from grace, which reads as a bug rather than a rule.</p>
     */
    @SubscribeEvent
    public static void onPartyDamaged(net.neoforged.neoforge.event.entity.living
            .LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        if (run == null) {
            return;
        }
        ActiveFloor floor = FLOORS.get(run.id());
        if (floor == null || player.serverLevel() != floor.level || floor.chargingToll) {
            return;
        }
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
            return;
        }
        floor.tookDamage = true;
        Room boss = bossRoom(floor);
        if (boss != null && floor.core.state(boss) == RoomState.IN_COMBAT) {
            floor.tookDamageInBossFight = true;
        }
    }

    private static Room bossRoom(ActiveFloor floor) {
        for (Room room : floor.built.layout().rooms()) {
            if (room.type() == RoomType.BOSS) {
                return room;
            }
        }
        return null;
    }

    /**
     * The fork closes. Claiming at one satellite re-bars the other's doorway for the rest of the
     * floor — the two doors face each other across the sala del sello and only one may be walked
     * through, which is what makes the pair a choice instead of a windfall (PISOS §63e).
     *
     * <p>Takes the kind that was <i>claimed</i> and seals its opposite. First claim wins; a plain
     * check-then-set is enough because the server tick is single-threaded.</p>
     */
    static void closeTheFork(ActiveFloor floor, DoorKind taken) {
        if (floor.forkResolved) {
            return;
        }
        floor.forkResolved = true;
        DoorKind sealed = taken == DoorKind.GRACIA ? DoorKind.DEVIL : DoorKind.GRACIA;
        boolean any = false;
        for (DoorEdge door : floor.built.layout().doors()) {
            if (door.kind() != sealed) {
                continue;
            }
            DoorCarver.fillDoorway(floor.level, floor.built.origin(), door,
                    sealState(), floor.built.roomSize(),
                    DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
            any = true;
        }
        if (any) {
            message(floor, taken == DoorKind.GRACIA
                    ? "§6Aceptas la gracia. §5La puerta del trato se cierra de golpe."
                    : "§5Cierras el trato. §6La puerta de la Orden se cierra en silencio.");
        }
    }

    /**
     * Charges one of the mod's own tolls, flagged so {@link #onPartyDamaged} does not read it as
     * the floor drawing blood. Every deliberate price the party pays goes through here.
     */
    static void chargeToll(ActiveFloor floor, ServerPlayer player, float damage) {
        floor.chargingToll = true;
        try {
            player.hurt(player.damageSources().magic(), damage);
        } finally {
            floor.chargingToll = false;
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonRun run = DungeonRunManager.runOf(player.getUUID());
            if (run == null || !FLOORS.containsKey(run.id())) {
                return;
            }
            // The charm is spent before anything else reads the death: cancelling here means no
            // respawn, no coin penalty and no death on the record — the player simply stands back
            // up where they fell, which is the whole point of paying 40 coins for it.
            if (run.stateOf(player.getUUID()).consumePhoenix()) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth() / 2.0f);
                player.clearFire();
                ActiveFloor floor = FLOORS.get(run.id());
                if (floor != null) {
                    playAt(floor, player.blockPosition(), DungeonSound.PHOENIX, 1.0f);
                }
                DungeonTitles.send(player, "§6Renaces", "§7El amuleto fénix se consume");
                return;
            }
            run.stateOf(player.getUUID()).countDeath();
            ActiveFloor died = FLOORS.get(run.id());
            if (died != null) {
                // Costs this floor its grace and feeds his side of the odds — a death is +20 to the
                // Acreedor and forfeits la Orden's +20, which is the opposition working.
                died.someoneDied = true;
            }
            RESPAWN_AT_START.put(player.getUUID(), run.id());
            return;
        }
        UUID id = event.getEntity().getUUID();
        for (ActiveFloor floor : FLOORS.values()) {
            // The collector first: he is outside the kill ledger, so the room loop below would
            // never see him, and what his death pays is not coins.
            if (id.equals(floor.collector)) {
                floor.collector = null;
                int forgiven = floor.run.deuda();
                floor.run.settleDebt(forgiven);
                payCoins(floor, event.getEntity());
                message(floor, "§5El Cobrador cae. §7La deuda de §f" + forgiven
                        + "§7 monedas muere con él.");
                playAt(floor, event.getEntity().blockPosition(), DungeonSound.DEVIL_DEAL, 1.0f);
                return;
            }
            Room room = floor.enemyRooms.remove(id);
            if (room != null) {
                payCoins(floor, event.getEntity());
                floor.core.enemyRemoved(room);
                return;
            }
        }
    }

    /**
     * A dead enemy's whole worth. Its ordinary drops and experience were cancelled upstream
     * ({@link CoinDrops}), so this is the only thing a kill produces.
     */
    private static void payCoins(ActiveFloor floor, net.minecraft.world.entity.LivingEntity enemy) {
        int coins = CoinDrops.coinsFor(enemy, floor.run.stage(), floor.level.random);
        CoinDrops.spawnCoins(floor.level, enemy.position(), coins);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Integer runId = RESPAWN_AT_START.remove(player.getUUID());
        ActiveFloor floor = runId == null ? null : FLOORS.get(runId);
        if (floor == null) {
            return;
        }
        BlockPos start = floor.built.partySpawn(floor.built.layout().start());
        player.teleportTo(floor.level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        land(player);
        // A respawn is a fresh player entity with vanilla attributes: hearts sold to a devil deal
        // — or accepted as an affliction — have to be taken again, or dying would be the cheapest
        // way to buy them back.
        Afflictions.apply(floor.run, player);

        int coinPct = floor.seguro ? DungeonsConfig.coinDeathPenaltyPct() / 2
                : DungeonsConfig.coinDeathPenaltyPct();
        int lost = floor.run.wallet().applyDeathPenalty(coinPct);
        if (lost > 0) {
            broadcastWallet(floor);
            message(floor, "§c" + player.getName().getString() + " ha caído — el grupo pierde "
                    + lost + " monedas.");
        }
        DungeonTitles.send(player, "§4Has caído", lost > 0 ? "§7−" + lost + " monedas" : "");

        int penaltyPct = DungeonsConfig.deathPenaltyPct();
        if (penaltyPct > 0) {
            BigDecimal balance = EconomyStore.get(player.getUUID());
            BigDecimal penalty = balance.multiply(BigDecimal.valueOf(penaltyPct))
                    .divide(BigDecimal.valueOf(100));
            if (penalty.signum() > 0 && EconomyStore.withdraw(player.getUUID(), penalty)) {
                player.sendSystemMessage(Component.literal(
                        "§cHas caído — pierdes " + penalty.toBigInteger() + " ₽."));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FLOORS.clear();
        RESPAWN_AT_START.clear();
    }

    /**
     * Every right-click a run cares about, in the order they can shadow each other: shop pedestals
     * and room fixtures first, then secret walls.
     *
     * <p>Secret and super-secret rooms open by <b>interacting</b> with the wall, not by breaking
     * it — the party plays in adventure mode, so breaking was never available, and Isaac's bomb
     * becomes a purchased charge: the cracked bricks of a SECRET edge are the visible invitation,
     * a HIDDEN edge looks like any other wall, and either way opening one spends a charge from the
     * party's stock. That is what puts a price back on secrets now that there is a shop to sell it
     * — pressing walls is free, opening them is not. The opening itself stays exactly
     * {@link DoorCarver}'s doorway volume, so what gives way is precisely the passage.</p>
     */
    @SubscribeEvent
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        ActiveFloor floor = run == null ? null : FLOORS.get(run.id());
        if (floor == null || player.serverLevel() != floor.level) {
            return;
        }
        BlockPos pos = event.getPos();
        if (floor.shop.tryBuy(floor, player, pos)) {
            event.setCanceled(true);
            return;
        }
        Room room = roomAt(floor, pos);
        if (room != null && room.type() == RoomType.ARCADE
                && ArcadeMachine.tryPlay(floor, player, room, pos)) {
            event.setCanceled(true);
            return;
        }
        if (room != null && room.type() == RoomType.DEVIL_DEAL
                && DevilDeal.tryClaim(floor, player, room, pos, player.isShiftKeyDown())) {
            event.setCanceled(true);
            return;
        }
        if (room != null && room.type() == RoomType.ORDEN
                && OrdenGift.tryClaim(floor, player, room, pos)) {
            event.setCanceled(true);
            return;
        }
        if (room != null && room.type() == RoomType.CURSE
                && floor.market.tryUse(floor, player, pos)) {
            event.setCanceled(true);
            return;
        }
        if (floor.pedestals.tryClaim(floor, player, pos)) {
            event.setCanceled(true);
            return;
        }
        if (tryOpenSecret(floor, player, pos)) {
            event.setCanceled(true);
        }
    }

    /** False when the click was not on a secret wall at all; true once it was the run's business. */
    private static boolean tryOpenSecret(ActiveFloor floor, ServerPlayer player, BlockPos pos) {
        for (DoorEdge door : floor.built.layout().doors()) {
            if (door.kind() != DoorKind.SECRET_CRACK && door.kind() != DoorKind.HIDDEN) {
                continue;
            }
            if (!DoorCarver.doorwayContains(floor.built.origin(), door, pos, floor.built.roomSize(),
                    DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight())) {
                continue;
            }
            if (floor.openedSecrets.contains(door)) {
                return true;
            }
            if (!floor.run.wallet().tryUseCharge()) {
                player.displayClientMessage(Component.literal(
                        "§7El muro suena hueco — necesitas una carga rompemuros."), true);
                playAt(floor, pos, DungeonSound.PURCHASE_DENIED, 1.0f);
                return true;
            }
            floor.openedSecrets.add(door);
            DoorCarver.fillDoorway(floor.level, floor.built.origin(), door,
                    Blocks.AIR.defaultBlockState(), floor.built.roomSize(),
                    DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
            playAt(floor, pos, DungeonSound.SECRET_OPENED, 1.0f);
            DungeonTitles.send(player, "§bSala secreta", "§7El muro cede");
            broadcastWallet(floor);
            return true;
        }
        return false;
    }

    /** The room a world position falls in, or null when it is outside the floor's rooms. */
    static Room roomAt(ActiveFloor floor, BlockPos pos) {
        int cellX = Math.floorDiv(pos.getX() - floor.built.origin().getX(), floor.built.roomSize());
        int cellY = Math.floorDiv(pos.getZ() - floor.built.origin().getZ(), floor.built.roomSize());
        return floor.built.layout().grid().roomAt(new GridPos(cellX, cellY));
    }

    /**
     * The room's marker of {@code kind}, clamped a block off the walls, else its exact center —
     * loudly, because a template missing a marker is an authoring mistake that otherwise shows up
     * only as a fixture standing in an odd place.
     *
     * <p>Warned once per room and kind. Plates are read from the tick loop, so warning every time
     * would put the same line in the log twenty times a second for as long as a player stood in
     * the room.</p>
     */
    static BlockPos markerPos(ActiveFloor floor, Room room, String kind) {
        for (TemplateMarkers.Marker marker : floor.built.markers().getOrDefault(room, List.of())) {
            if (marker.kind().equals(kind)) {
                return floor.built.clampInside(room, marker.pos(), 1);
            }
        }
        if (floor.warnedMarkers.add(room + "/" + kind)) {
            Teras.LOGGER.warn("Dungeons: {} has no '{}' marker — using the room center. "
                    + "Add one to its template with the room editor.", room, kind);
        }
        return floor.built.roomCenter(room);
    }

    /**
     * Whether {@code pos} is close enough to a room fixture to count as being at it — standing on
     * a plate, or clicking a pedestal from the block beside it.
     *
     * <p>One test for every fixture in the dungeon (plates, shop pedestals, the arcade machine,
     * the devil's offer). They all mean the same thing, and four hand-rolled copies had already
     * drifted to three different reaches for no stated reason. {@code reach} is the horizontal
     * slack in blocks; vertical is always generous, because a marker authored a block above the
     * floor is common and a player's feet are not where they clicked.</p>
     */
    static boolean isAtFixture(BlockPos fixture, BlockPos pos, int reach) {
        return Math.abs(pos.getX() - fixture.getX()) <= reach
                && Math.abs(pos.getZ() - fixture.getZ()) <= reach
                && Math.abs(pos.getY() - fixture.getY()) <= 2;
    }

    /** Whether a player is standing on the room's {@code kind} marker. */
    private static boolean isOnMarker(ActiveFloor floor, Room room, String kind, ServerPlayer player) {
        return isAtFixture(markerPos(floor, room, kind), player.blockPosition(), 1);
    }

    /**
     * Plates are read from the tick loop rather than from a block event: the party is in adventure
     * mode and the "plate" is decoration over a template marker, so standing on the position is
     * the trigger. The cooldown is what keeps a sacrifice from draining a player who simply stopped
     * walking on it.
     */
    private static void checkPlates(ActiveFloor floor, ServerPlayer player, GridPos cell) {
        Room room = floor.built.layout().grid().roomAt(cell);
        if (room == null) {
            return;
        }
        if (room.type() == RoomType.CHALLENGE && floor.core.state(room) == RoomState.DISCOVERED
                && isOnMarker(floor, room, "challenge", player)) {
            floor.core.activatePlate(room);
            return;
        }
        if (room.type() == RoomType.SACRIFICE && !floor.sacrificeSpent.contains(room)
                && isOnMarker(floor, room, "sacrifice", player)) {
            SacrificePlate.step(floor, player, room);
        }
    }

    /**
     * True when {@code player} may act on {@code fixture} again; stamps the next cooldown when it
     * returns true. Cooldowns are per fixture, so one room's pacing never gates another's.
     */
    static boolean fixtureReady(ActiveFloor floor, ServerPlayer player, String fixture,
                                int cooldownTicks) {
        String key = player.getUUID() + "/" + fixture;
        long ready = floor.fixtureCooldown.getOrDefault(key, 0L);
        if (tick < ready) {
            return false;
        }
        floor.fixtureCooldown.put(key, tick + cooldownTicks);
        return true;
    }

    /** Resyncs every member's minimap — after a shop reveal, or any state change. */
    static void syncMapFor(ActiveFloor floor) {
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player != null && player.serverLevel() == floor.level) {
                sendMap(floor, player);
            }
        }
    }

    /**
     * Rolls a loot table at a position. Coins and charges in a table are respawned as the
     * never-pickup entities the magnet sweep understands, so a reward roll credits the shared purse
     * instead of putting currency in someone's backpack.
     */
    /**
     * The stacks a table rolls, without placing them anywhere.
     *
     * <p>Split out of {@link #rollLootAt} for the reward pedestals: a claimed reward goes into the
     * claimant's hands, not onto the floor, and coins and charges still have to divert to the shared
     * systems rather than becoming inventory items.</p>
     */
    static List<ItemStack> rollLoot(ActiveFloor floor, String tableId) {
        LootTable table = floor.level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(tableId)));
        LootParams params = new LootParams.Builder(floor.level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(floor.built.origin()))
                .create(LootContextParamSets.CHEST);
        List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack stack : table.getRandomItems(params)) {
            es.boffmedia.teras.dungeon.gear.GearStamp.decorate(stack);
            out.add(stack);
        }
        return out;
    }

    /** Into the claimant's inventory, or at their feet when it is full. Never lost. */
    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (stack.is(es.boffmedia.teras.init.ItemInit.MONEDA_MAZMORRA.get())) {
            CoinDrops.spawnCoins(player.serverLevel(), player.blockPosition(), stack.getCount());
            return;
        }
        if (stack.is(es.boffmedia.teras.init.ItemInit.CARGA_ROMPEMUROS.get())) {
            CoinDrops.spawnCharges(player.serverLevel(),
                    Vec3.atCenterOf(player.blockPosition()), stack.getCount());
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Throws a claimed reward out of a pedestal instead of teleporting it into a bag.
     *
     * <p>The pedestal rework moved rewards off the floor to fix four real problems — a latecomer
     * arriving to a bare stand, one player hoovering the pile, five-minute despawns, and stage
     * advance sweeping what was left. Putting the item straight into the inventory fixed all four
     * and cost the moment: a piece of gear appeared as a line of chat.</p>
     *
     * <p>This gets it back without giving any of it up. The stack arcs out of the stand as a real
     * {@link ItemEntity} with {@code setTarget}, which vanilla checks in {@code playerTouch} — so
     * <b>only the claimer can pick it up</b> and the anti-race properties are untouched. It also
     * gives a rarity-coloured beam something to stand over for servers running Loot Beams, which
     * reads the same {@code RARITY} component the tooltip does.</p>
     *
     * <p>Coins and charges keep their own spawners: they have magnet behaviour the run already
     * owns, and a beam over every coin would be noise rather than an event.</p>
     */
    static void ejectTo(ActiveFloor floor, ServerPlayer player, BlockPos from, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (stack.is(es.boffmedia.teras.init.ItemInit.MONEDA_MAZMORRA.get())
                || stack.is(es.boffmedia.teras.init.ItemInit.CARGA_ROMPEMUROS.get())) {
            giveOrDrop(player, stack);
            return;
        }
        ItemEntity item = new ItemEntity(floor.level,
                from.getX() + 0.5, from.getY() + 1.1, from.getZ() + 0.5, stack);
        Vec3 away = new Vec3(player.getX() - from.getX() - 0.5, 0, player.getZ() - from.getZ() - 0.5);
        Vec3 push = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0.3, 0)
                : away.normalize().scale(0.18).add(0, 0.28, 0);
        item.setDeltaMovement(push);
        item.setTarget(player.getUUID());
        item.setThrower(player);
        // Long enough for the arc to read as a throw, short enough that walking into it works.
        item.setPickUpDelay(10);
        floor.level.addFreshEntity(item);
    }

    /** Where a reward lands in a room that has no {@code loot} marker to stand it on. */
    static BlockPos fallbackLootPos(ActiveFloor floor, Room room) {
        return floor.built.partySpawn(room);
    }

    static void rollLootAt(ActiveFloor floor, BlockPos pos, String tableId) {
        LootTable table = floor.level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(tableId)));
        LootParams params = new LootParams.Builder(floor.level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
        for (ItemStack stack : table.getRandomItems(params)) {
            if (stack.is(es.boffmedia.teras.init.ItemInit.MONEDA_MAZMORRA.get())) {
                CoinDrops.spawnCoins(floor.level, pos, stack.getCount());
            } else if (stack.is(es.boffmedia.teras.init.ItemInit.CARGA_ROMPEMUROS.get())) {
                CoinDrops.spawnCharges(floor.level, Vec3.atCenterOf(pos), stack.getCount());
            } else {
                // The 5-arg constructor gives drops a random pop of velocity — fine for a mob
                // kill, wrong for a reward pedestal: items scattered around the room, sometimes
                // out of sight, and read as the loot not having spawned at all. Zero motion: the
                // reward stands exactly on its marker.
                es.boffmedia.teras.dungeon.gear.GearStamp.decorate(stack);
                ItemEntity item = new ItemEntity(floor.level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack, 0, 0, 0);
                item.setDefaultPickUpDelay();
                floor.level.addFreshEntity(item);
            }
        }
    }

    /**
     * Belt over adventure's braces: even a party member an op flipped to creative may not carve
     * the floor. Only run members are constrained, and only in the dungeon dimension — admins
     * outside a run (the room editor, debugging) keep their hands.
     */
    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && editRefused(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && editRefused(player)) {
            event.setCanceled(true);
        }
    }

    private static boolean editRefused(ServerPlayer player) {
        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        return run != null && FLOORS.containsKey(run.id())
                && player.serverLevel().dimension().location().toString()
                        .equals(DungeonsConfig.dimension());
    }

    /**
     * Parks a player who has dropped through the trapdoor: no gravity, no motion, no fall
     * distance. They hang in the dark for the second or two the next floor takes to build, rather
     * than keep accelerating downward. {@link #land} undoes it on arrival.
     */
    public static void holdWhileDescending(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        player.resetFallDistance();
        player.hurtMarked = true;
    }

    /** Puts a player back on their feet after any dungeon teleport: gravity, no inherited fall. */
    public static void land(ServerPlayer player) {
        player.setNoGravity(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.hurtMarked = true;
    }

    /** Raises the straggler bell on the first member through the pit. Idempotent after that. */
    private static void startDescentCountdown(ActiveFloor floor) {
        if (floor.descentDeadline != 0) {
            return;
        }
        floor.descentTotalTicks = DungeonsConfig.descentSeconds() * 20;
        floor.descentDeadline = tick + floor.descentTotalTicks;
        floor.descentBar = new net.minecraft.server.level.ServerBossEvent(
                Component.literal("§dEl sello cede…"),
                net.minecraft.world.BossEvent.BossBarColor.PURPLE,
                net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
        floor.descentBar.setPlayBossMusic(false);
        message(floor, "§d¡Alguien ha saltado al pozo! El grupo desciende en "
                + DungeonsConfig.descentSeconds() + " segundos.");
    }

    /**
     * Runs the straggler bell: bar text and progress, a tick sound over the last five seconds,
     * and the descent itself when it reaches zero. True when this scan advanced the stage — the
     * caller must stop touching a floor that is being replaced.
     */
    private static boolean tickDescent(ActiveFloor floor) {
        if (floor.descentDeadline == 0 || floor.advancing) {
            return false;
        }
        long remaining = floor.descentDeadline - tick;
        if (remaining <= 0) {
            beginAdvance(floor);
            return true;
        }
        int seconds = (int) ((remaining + 19) / 20);
        floor.descentBar.setName(Component.literal("§dEl sello cede — " + seconds + " s"));
        floor.descentBar.setProgress(Math.min(1f, (float) remaining / floor.descentTotalTicks));
        SoundEvent tickCue = soundEvent("DESCENT_TICK");
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player == null || player.serverLevel() != floor.level) {
                continue;
            }
            floor.descentBar.addPlayer(player);
            if (tickCue != null && seconds <= 5 && remaining % 20 == 0
                    && !floor.descended.contains(member)) {
                floor.level.playSound(null, player.blockPosition(), tickCue, SoundSource.PLAYERS,
                        DungeonsConfig.soundVolume(), 1.6f);
            }
        }
        return false;
    }

    /** Every member actually standing on this floor is already under it. */
    private static boolean allPresentBelow(ActiveFloor floor) {
        BlockPos origin = floor.built.origin();
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player != null && player.serverLevel() == floor.level
                    && player.getY() >= origin.getY() - 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * The party descends. Everyone under the floor is caught before the next one starts building:
     * the drop takes a second or two, and a player left falling arrives with enough accumulated
     * fall distance to die on landing — or drops far enough to reach the void. Whoever is still
     * above ground rides the ordinary stage teleport when the new floor is built.
     */
    private static void beginAdvance(ActiveFloor floor) {
        floor.advancing = true;
        clearDescentBar(floor);
        BlockPos origin = floor.built.origin();
        for (UUID falling : floor.run.party().keySet()) {
            ServerPlayer other = floor.level.getServer().getPlayerList().getPlayer(falling);
            if (other != null && other.serverLevel() == floor.level
                    && other.getY() < origin.getY() - 2) {
                holdWhileDescending(other);
            }
        }
        // Score the floor before the next one is generated — this is the one moment every input
        // exists at once: the floor has been played, any deal or refusal on it is recorded, and
        // advanceStage is about to ask who should be waiting in the next sala del sello.
        floor.run.closeFloor(floorOutcome(floor), hasAcreedor(floor));
        DungeonRunManager.advanceStage(floor.run, floor.level);
    }

    /**
     * The floor-local half of the odds, read at the descent. The two end-state signals are asked
     * here rather than tracked: "broke" and "bleeding" are about how the party <i>leaves</i> the
     * floor, not about anything that happened during it.
     */
    private static es.boffmedia.teras.dungeon.gen.SatelliteOdds.FloorOutcome floorOutcome(
            ActiveFloor floor) {
        return new es.boffmedia.teras.dungeon.gen.SatelliteOdds.FloorOutcome(
                floor.someoneDied,
                !floor.tookDamageInBossFight,
                !floor.tookDamage,
                floor.soldHearts,
                lowPartyHp(floor),
                // Cannot even afford the cash price, so the loan is the only door still open —
                // which is exactly the moment a creditor should knock.
                floor.run.wallet().coins() < CoinDrops.scaleToStage(
                        DungeonsConfig.devilCoinPrice(), floor.run.stage()));
    }

    /** Whether his room stood on this floor at all — what makes walking past it a refusal. */
    private static boolean hasAcreedor(ActiveFloor floor) {
        for (Room room : floor.built.layout().rooms()) {
            if (room.type() == RoomType.DEVIL_DEAL) {
                return true;
            }
        }
        return false;
    }

    /** The party limping: average health below a third across everyone actually on the floor. */
    private static boolean lowPartyHp(ActiveFloor floor) {
        float fraction = 0f;
        int counted = 0;
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player != null && player.getMaxHealth() > 0) {
                fraction += player.getHealth() / player.getMaxHealth();
                counted++;
            }
        }
        return counted > 0 && fraction / counted < LOW_HP_FRACTION;
    }

    private static void clearDescentBar(ActiveFloor floor) {
        if (floor.descentBar != null) {
            floor.descentBar.removeAllPlayers();
            floor.descentBar.setVisible(false);
            floor.descentBar = null;
        }
    }

    private static void scanPlayers(ActiveFloor floor) {
        if (tickDescent(floor)) {
            return;
        }
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player == null || player.serverLevel() != floor.level) {
                continue;
            }
            BlockPos origin = floor.built.origin();
            boolean belowFloor = player.getY() < origin.getY() - 2;
            if (floor.core.isTrapdoorOpen() && !floor.advancing && belowFloor) {
                // Nobody's fall advances anyone else any more: the jumper is parked under the
                // floor, the first one starts the straggler bell, and the party descends together
                // at zero — or the moment everyone has jumped (PRODUCCION §10.6). The shipped flow
                // advanced the whole run on the first member below, pedestal claimed or not.
                boolean firstDown = floor.descended.add(member);
                if (firstDown) {
                    holdWhileDescending(player);
                }
                if (allPresentBelow(floor)) {
                    beginAdvance(floor);
                    return;
                }
                if (firstDown) {
                    startDescentCountdown(floor);
                }
                continue;
            }
            if (belowFloor) {
                BlockPos start = floor.built.partySpawn(floor.built.layout().start());
                player.teleportTo(floor.level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                land(player);
                continue;
            }
            DungeonHealth.holdHunger(player);
            Afflictions.tick(floor.run, player);
            collectPickups(floor, player);
            GridPos cell = cellOf(floor, player);
            floor.core.playerEnteredCell(member, cell, isClearOfDoors(floor, cell, player));
            if (!cell.equals(floor.lastCell.put(member, cell))) {
                sendMap(floor, player);
                chargeCurseDoor(floor, player, cell);
            }
            checkPlates(floor, player, cell);
        }
    }

    /**
     * Walking over a coin is what banks it. The drops themselves refuse to be picked up into an
     * inventory, so this sweep is the only way they become money — which is what keeps the purse
     * authoritative and stops anyone carrying dungeon currency out of the dungeon.
     */
    private static void collectPickups(ActiveFloor floor, ServerPlayer player) {
        double radius = DungeonsConfig.coinPickupRadius();
        List<ItemEntity> nearby = floor.level.getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(radius, 1.0, radius),
                item -> item.isAlive()
                        && (item.getTags().contains(CoinDrops.COIN_TAG)
                            || item.getTags().contains(CoinDrops.CHARGE_TAG)));
        if (nearby.isEmpty()) {
            return;
        }
        int coins = 0;
        int charges = 0;
        for (ItemEntity item : nearby) {
            if (item.getTags().contains(CoinDrops.COIN_TAG)) {
                coins += item.getItem().getCount();
            } else {
                charges += item.getItem().getCount();
            }
            item.discard();
        }
        floor.run.wallet().add(coins);
        floor.run.wallet().addCharges(charges);
        playAt(floor, player.blockPosition(), DungeonSound.COIN_PICKUP, 1.4f);
        if (charges > 0) {
            player.displayClientMessage(Component.literal(
                    "§b+" + charges + " carga" + (charges == 1 ? "" : "s") + " rompemuros"), true);
        }
        broadcastWallet(floor);
    }

    /**
     * Whether every doorway of the room at {@code cell} can be filled without any of it landing
     * inside {@code player}. Tested as the actual overlap between the player's bounding box and
     * the blocks {@link DoorCarver} is about to write, so stepping one block clear of the gap is
     * enough — a distance check from the doorway's middle looks equivalent and is not: it keeps
     * the room open until the player is several blocks in, long after they have committed.
     */
    /**
     * The grid cell a player stands in. One derivation, used by the tick loop and by the map: two
     * copies of this arithmetic drifting is how the minimap came to mark a cell the player had
     * already left.
     */
    private static GridPos cellOf(ActiveFloor floor, ServerPlayer player) {
        BlockPos origin = floor.built.origin();
        int roomSize = floor.built.roomSize();
        return new GridPos(
                Math.floorDiv(player.blockPosition().getX() - origin.getX(), roomSize),
                Math.floorDiv(player.blockPosition().getZ() - origin.getZ(), roomSize));
    }

    /**
     * The blood price for stepping into a curse room.
     *
     * <p>Charged on <b>crossing</b>, per player, rather than on discovery to whoever happened to be
     * first — the spikes over the doorway are a promise made to everyone who walks under them, and
     * a room that bills one member for four people's entry is not the deal it advertised.</p>
     *
     * <p>Once per player per floor: stepping out to finish a fight and coming back is not a second
     * decision, and charging for it would turn the room into somewhere you dare not leave.</p>
     *
     * <p><b>It can never kill.</b> The toll is floored at half a heart remaining. A price that can
     * end the run is one no party ever pays, which would make the whole room dead content — and
     * dying to a doorway reads as a bug however it is documented.</p>
     */
    private static void chargeCurseDoor(ActiveFloor floor, ServerPlayer player, GridPos cell) {
        Room room = floor.built.layout().grid().roomAt(cell);
        if (room == null || room.type() != RoomType.CURSE) {
            return;
        }
        if (!floor.curseTollPaid.add(player.getUUID())) {
            return;
        }
        float toll = DungeonsConfig.curseDoorTollHearts() * 2.0f;
        if (toll <= 0) {
            return;
        }
        float survivable = Math.max(0f, player.getHealth() - 1.0f);
        float damage = Math.min(toll, survivable);
        if (damage <= 0) {
            player.displayClientMessage(Component.literal(
                    "§4Los pinchos te dejan pasar — no te queda sangre que cobrar."), true);
            return;
        }
        // magic() is in BYPASSES_ARMOR, so the toll is the toll — a party in full plate does not
        // get in cheaper. Same source the sacrifice plate uses.
        chargeToll(floor, player, damage);
        playAt(floor, player.blockPosition(), DungeonSound.SACRIFICE, 1.0f);
        player.displayClientMessage(Component.literal(
                "§4Los pinchos cobran su peaje."), true);
    }

    /** What the party's afflictions do to a wave it is about to meet. */
    private static EnemySpawner.WaveModifier swarm(ActiveFloor floor) {
        return new EnemySpawner.WaveModifier(
                Afflictions.waveCountMultiplier(floor.run),
                Afflictions.waveStatMultiplier(floor.run));
    }

    private static boolean isClearOfDoors(ActiveFloor floor, GridPos cell, ServerPlayer player) {
        Room room = floor.built.layout().grid().roomAt(cell);
        return room != null && doorwayHolding(floor, room, player) == null;
    }

    /** The first doorway of {@code room} the player is standing in, or null if they are clear. */
    private static DoorEdge doorwayHolding(ActiveFloor floor, Room room, ServerPlayer player) {
        AABB body = player.getBoundingBox();
        for (DoorEdge door : floor.built.layout().doorsOf(room)) {
            if (!door.kind().walkable()) {
                continue;
            }
            if (body.intersects(doorwayBox(floor.built, door))) {
                return door;
            }
        }
        return null;
    }

    /**
     * The exact volume {@link DoorCarver#fillDoorway} writes: two block layers deep across the
     * shared wall, {@code doorWidth} across and {@code doorHeight} tall from one block above the
     * floor. Inflated a touch so a player flush against the opening still counts as in it.
     */
    private static AABB doorwayBox(BuiltDungeon built, DoorEdge door) {
        int roomSize = built.roomSize();
        int width = DungeonsConfig.doorWidth();
        int height = DungeonsConfig.doorHeight();
        int inset = (roomSize - width) / 2;
        BlockPos origin = built.origin();
        double baseX = origin.getX() + door.cell().x() * roomSize;
        double baseZ = origin.getZ() + door.cell().y() * roomSize;
        double minY = origin.getY() + 1;
        double maxY = minY + height;
        AABB box = door.dir() == GridDir.EAST
                ? new AABB(baseX + roomSize - 1, minY, baseZ + inset,
                           baseX + roomSize + 1, maxY, baseZ + inset + width)
                : new AABB(baseX + inset, minY, baseZ + roomSize - 1,
                           baseX + inset + width, maxY, baseZ + roomSize + 1);
        return box.inflate(DOOR_MARGIN, 0, DOOR_MARGIN);
    }

    /**
     * Moves anyone still standing in a doorway of {@code room} into it before the bars go in — the
     * player who triggered the seal is clear by construction, but a second party member might not
     * be.
     */
    private static void clearDoorways(ActiveFloor floor, Room room) {
        BlockPos centre = floor.built.roomCenter(room);
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player == null || player.serverLevel() != floor.level) {
                continue;
            }
            if (doorwayHolding(floor, room, player) != null) {
                player.teleportTo(floor.level, centre.getX() + 0.5, centre.getY(),
                        centre.getZ() + 0.5, player.getYRot(), player.getXRot());
                land(player);
            }
        }
    }

    /**
     * The player's full minimap snapshot: discovered rooms plus dim outlines behind their doors.
     *
     * <p>The player's position is read here rather than passed in. It used to be a parameter, and
     * {@code syncMapFor} supplied {@code lastCell} — which {@code scanPlayers} updates <i>after</i>
     * discovery, so any resync triggered by walking into a room carried the cell the player had
     * just left. Deriving it from the player is the only way the two cannot disagree.</p>
     */
    private static void sendMap(ActiveFloor floor, ServerPlayer player) {
        GridPos playerCell = cellOf(floor, player);
        Room playerRoom = floor.built.layout().grid().roomAt(playerCell);
        List<DungeonMapPayload.Cell> cells = new java.util.ArrayList<>();
        var discovered = floor.core.discovered();
        java.util.Set<GridPos> outlined = new java.util.HashSet<>();
        for (Room room : discovered) {
            // Every cell of the room the player stands in is marked current, not just the one they
            // occupy: a 2x2 chamber outlined on one quadrant reads as a grid line rather than as a
            // position, which is why multi-cell rooms looked unmarked while single ones did not.
            boolean here = room == playerRoom;
            // Placement index identifies the room: two adjacent cells sharing it are one chamber,
            // which is how the map draws a 2x2 as a 2x2 rather than as four squares.
            int id = floor.built.layout().rooms().indexOf(room);
            for (GridPos cell : room.cells()) {
                cells.add(new DungeonMapPayload.Cell(cell.x(), cell.y(),
                        room.type().ordinal(), floor.core.state(room).ordinal(),
                        cell.equals(room.cells().get(0)), here, id));
            }
        }
        for (Room room : discovered) {
            for (var door : floor.built.layout().doorsOf(room)) {
                if (door.kind() == es.boffmedia.teras.dungeon.model.DoorKind.SECRET_CRACK
                        || door.kind() == es.boffmedia.teras.dungeon.model.DoorKind.HIDDEN) {
                    continue;
                }
                Room other = door.from() == room ? door.to() : door.from();
                if (!discovered.contains(other)) {
                    // La sala del sello does not exist until the seal re-pins: solid wall in the
                    // world, nothing on the wire. The moment the boss falls it pops onto the map
                    // fully typed — the reveal is part of the ceremony.
                    if (other.type() == RoomType.EXIT) {
                        if (floor.core.isTrapdoorOpen()) {
                            for (GridPos cell : other.cells()) {
                                if (outlined.add(cell)) {
                                    cells.add(new DungeonMapPayload.Cell(cell.x(), cell.y(),
                                            other.type().ordinal(), 0,
                                            cell.equals(other.cells().get(0)), false,
                                            floor.built.layout().rooms().indexOf(other)));
                                }
                            }
                        }
                        continue;
                    }
                    GridPos cell = door.from() == room ? door.neighborCell() : door.cell();
                    if (outlined.add(cell)) {
                        cells.add(new DungeonMapPayload.Cell(cell.x(), cell.y(),
                                DungeonMapPayload.TYPE_UNKNOWN, 0, false, false,
                                DungeonMapPayload.ROOM_NONE));
                    }
                }
            }
        }
        // Bought at the shop. The map draws the floor's shape; the compass names its special
        // rooms. Neither ever reveals a secret room — those are the one thing the payload keeps
        // back on purpose, and a purchasable X-ray would undo the whole point of hunting walls.
        if (floor.mapRevealed || floor.compassRevealed) {
            for (Room room : floor.built.layout().rooms()) {
                if (discovered.contains(room) || isSecret(room)) {
                    continue;
                }
                // No purchase reveals the sala del sello early: like the secrets it is withheld
                // on purpose, until the boss's death makes it exist.
                if (room.type() == RoomType.EXIT && !floor.core.isTrapdoorOpen()) {
                    continue;
                }
                boolean named = floor.compassRevealed && room.type() != RoomType.NORMAL;
                if (!named && !floor.mapRevealed) {
                    continue;
                }
                for (GridPos cell : room.cells()) {
                    if (!outlined.add(cell)) {
                        continue;
                    }
                    cells.add(new DungeonMapPayload.Cell(cell.x(), cell.y(),
                            named ? room.type().ordinal() : DungeonMapPayload.TYPE_UNKNOWN, 0,
                            named && cell.equals(room.cells().get(0)), false,
                            // A revealed room is still a room: its footprint reads as one shape.
                            floor.built.layout().rooms().indexOf(room)));
                }
            }
        }
        // Two ways to lose the map: the floor was generated LOST, or the party sold its sight to
        // the curse room. Same darkness, different reasons, so the same flag carries both.
        boolean mapHidden = floor.run.curses().contains(es.boffmedia.teras.dungeon.model.Curse.LOST)
                || Afflictions.mapHidden(floor.run);
        PacketDistributor.sendToPlayer(player, new DungeonMapPayload(true,
                floor.built.layout().grid().size(), floor.run.stage(), mapHidden, cells));
    }

    private static boolean isSecret(Room room) {
        return room.type() == RoomType.SECRET || room.type() == RoomType.SUPER_SECRET;
    }

    /**
     * A sealed fight with no living player left inside resets rather than resolves: wave
     * discarded, doors open, room back to DISCOVERED for a fresh attempt. Without this, dying in
     * combat either soft-locked the room (sealed forever, entry ignores IN_COMBAT) or — the
     * playtest case — falsely cleared it: the respawned player's departure let the room's chunks
     * unload, the sweep read the frozen enemies as removed, and the trapdoor opened over a boss
     * nobody killed, clear reward included.
     */
    private static void abandonDeserted(ActiveFloor floor) {
        for (Room room : floor.built.layout().rooms()) {
            if (floor.core.state(room) != RoomState.IN_COMBAT || anyAliveInside(floor, room)) {
                continue;
            }
            // Discard before the state change, while the deserter's chunks are typically still
            // loaded (the corpse keeps them so until the respawn click). Anything that unloaded
            // first is caught by the spawn-time purge on the next attempt.
            for (Map.Entry<UUID, Room> entry : List.copyOf(floor.enemyRooms.entrySet())) {
                if (entry.getValue() != room) {
                    continue;
                }
                Entity enemy = floor.level.getEntity(entry.getKey());
                if (enemy != null) {
                    enemy.discard();
                }
                floor.enemyRooms.remove(entry.getKey());
            }
            floor.core.abandonCombat(room);
        }
    }

    private static boolean anyAliveInside(ActiveFloor floor, Room room) {
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player == null || !player.isAlive() || player.serverLevel() != floor.level) {
                continue;
            }
            if (roomAt(floor, player.blockPosition()) == room) {
                return true;
            }
        }
        return false;
    }

    private static void sweepEnemies(ActiveFloor floor) {
        for (Map.Entry<UUID, Room> entry : List.copyOf(floor.enemyRooms.entrySet())) {
            // An enemy in an unloaded chunk is frozen, not gone — getEntity cannot tell the two
            // apart, and counting a frozen wave as dead is exactly the false clear the desertion
            // reset exists to prevent. Skip until the room is simulated again.
            if (!floor.level.hasChunkAt(floor.built.roomCenter(entry.getValue()))) {
                continue;
            }
            Entity entity = floor.level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive()) {
                floor.enemyRooms.remove(entry.getKey());
                floor.core.enemyRemoved(entry.getValue());
            }
        }
    }

    private static void removeRemainingEnemies(ActiveFloor floor) {
        for (UUID id : floor.enemyRooms.keySet()) {
            Entity entity = floor.level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        floor.enemyRooms.clear();
        // The ledger only ever held the fighting wave; atmosphere (bats and the like) is spawned
        // into the floor volume but never tracked, so a tag sweep over the whole footprint is what
        // takes it with everything else — the slot's next build would eventually, but not before
        // the pad is discarded a moment later.
        int span = floor.built.layout().grid().size() * floor.built.roomSize();
        BlockPos o = floor.built.origin();
        AABB box = new AABB(o.getX(), o.getY(), o.getZ(),
                o.getX() + span, o.getY() + floor.built.roomHeight(), o.getZ() + span);
        for (Entity tagged : floor.level.getEntities((Entity) null, box,
                e -> e.getTags().contains(EnemySpawner.DUNGEON_TAG))) {
            tagged.discard();
        }
    }

    private static BlockState sealState() {
        return BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse(DungeonsConfig.sealBlock()))
                .defaultBlockState();
    }

    /**
     * Plays a cue for a room: once at every doorway, and once from the middle of the room. Both
     * halves matter — the doorway copies are what make a seal read as <i>these</i> doors shutting
     * on you rather than an ambient noise, and the body sound carries the weight of it. Only
     * walkable edges are used, the same set {@link DoorCarver} fills.
     */
    private static void playCue(ActiveFloor floor, Room room, String cue, float pitch) {
        SoundEvent event = soundEvent(cue);
        if (event == null) {
            return;
        }
        float volume = DungeonsConfig.soundVolume();
        for (DoorEdge door : floor.built.layout().doorsOf(room)) {
            if (!door.kind().walkable()) {
                continue;
            }
            Vec3 at = doorwayBox(floor.built, door).getCenter();
            floor.level.playSound(null, at.x, at.y, at.z, event, SoundSource.BLOCKS, volume, pitch);
        }
    }

    private static void playBody(ActiveFloor floor, Room room, String cue, float pitch) {
        SoundEvent event = soundEvent(cue);
        if (event == null) {
            return;
        }
        BlockPos centre = floor.built.roomCenter(room);
        floor.level.playSound(null, centre, event, SoundSource.BLOCKS,
                DungeonsConfig.soundVolume(), pitch);
    }

    /** Null when the configured id names no registered sound — a bad cue must not break the run. */
    private static SoundEvent soundEvent(String cue) {
        String id = DungeonsConfig.sound(cue);
        if (id == null || id.isBlank()) {
            return null;
        }
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(id));
        if (event == null) {
            Teras.LOGGER.warn("Dungeons: sound cue '{}' names an unknown sound '{}'", cue, id);
        }
        return event;
    }

    private static final class FloorCallbacks implements RunCallbacks {
        private final ActiveFloor floor;

        FloorCallbacks(ActiveFloor floor) {
            this.floor = floor;
        }

        @Override
        public void roomDiscovered(Room room, UUID discoverer) {
            // Which rooms pay out is MarkerContract's answer, not a chain of ifs here. Both secret
            // templates carried a `loot` marker that nothing read, so breaking in — after buying a
            // wall charge, since shop slot 1 is always ROMPEMUROS — paid nothing. Dispatching on the
            // contract is what makes that unrepeatable: a room type absent from it cannot pay, and
            // one present in it cannot be forgotten here.
            var loot = es.boffmedia.teras.dungeon.piso.MarkerContract.lootSource(room.type());
            if (loot != null) {
                // Common rewards are per-player and rare ones are one-of-N: if it is shiny, there
                // is one of it. The super secret costs a wall charge and is found, not given, so it
                // is the one discovery-time reward worth arguing over.
                floor.pedestals.arm(floor, room,
                        room.type() == RoomType.SUPER_SECRET
                                ? ClaimPolicy.Kind.ONE_OF_N : ClaimPolicy.Kind.PER_PLAYER,
                        lootTable(loot));
            }
            if (room.type() == RoomType.CURSE) {
                // No toll here any more: the price is a heart at the spiked doorway, paid by
                // everyone who walks under it. Inside, nothing is taken that was not offered.
                floor.market.open(floor, room);
            }
        }

        /** Exhaustive on purpose: adding a source without giving it a table is a compile error. */
        private static String lootTable(
                es.boffmedia.teras.dungeon.piso.MarkerContract.LootSource source) {
            return switch (source) {
                case TESORO -> DungeonsConfig.treasureLootTable();
                case SECRETA -> DungeonsConfig.secretLootTable();
                case SUPERSECRETA -> DungeonsConfig.superSecretLootTable();
            };
        }

        @Override
        public void sealRoom(Room room) {
            clearDoorways(floor, room);
            DoorCarver.setRoomDoors(floor.level, floor.built, room, sealState());
            var piso = floor.run.plan().piso();
            // One call for every mechanic there will ever be. Routing through the registry rather
            // than naming a class is the point: a second mechanic is a registration, not an edit
            // here, and a piso that runs none gets a no-op instead of a branch.
            es.boffmedia.teras.dungeon.mecanica.Mechanics.of(piso).onRoomSealed(
                    floor.level, floor.built, room,
                    es.boffmedia.teras.dungeon.mecanica.Mechanics.defOf(piso));
            es.boffmedia.teras.dungeon.mecanica.Nests.warnIfUnhatchable(floor.built, room, piso);
        }

        @Override
        public void openRoom(Room room) {
            DoorCarver.setRoomDoors(floor.level, floor.built, room, Blocks.AIR.defaultBlockState());
        }

        @Override
        public void sound(DungeonSound sound, Room room) {
            switch (sound) {
                case ROOM_SEALED -> {
                    playCue(floor, room, "ROOM_SEALED", 0.7f);
                    playBody(floor, room, "ROOM_SEALED_BODY", 0.5f);
                }
                case BOSS_SEALED -> {
                    playCue(floor, room, "BOSS_SEALED", 0.6f);
                    playBody(floor, room, "BOSS_SEALED_BODY", 1.4f);
                }
                case ROOM_OPENED -> {
                    playCue(floor, room, "ROOM_OPENED", 1.1f);
                    playBody(floor, room, "ROOM_OPENED_BODY", 1.2f);
                }
                case BOSS_DEFEATED -> playBody(floor, room, "BOSS_DEFEATED", 1.0f);
                case TRAPDOOR_OPEN -> playBody(floor, room, "TRAPDOOR_OPEN", 1.4f);
                case SEAL_RESTORED -> {
                    playCue(floor, room, "ROOM_OPENED", 0.8f);
                    playBody(floor, room, "SEAL_RESTORED", 1.0f);
                }
                case CHALLENGE_STARTED -> {
                    playCue(floor, room, "ROOM_SEALED", 0.7f);
                    playBody(floor, room, "CHALLENGE_STARTED", 1.0f);
                }
                case WAVE_CLEARED -> playBody(floor, room, "WAVE_CLEARED", 1.2f);
                // Everything below is fired at a block by the room fixtures rather than through
                // the core's callback; the cases keep this switch total over the enum.
                case SECRET_OPENED -> playBody(floor, room, "SECRET_OPENED", 1.0f);
                case ENEMY_ENRAGED -> playBody(floor, room, "ENEMY_ENRAGED", 1.1f);
                case COIN_PICKUP, PURCHASE, PURCHASE_DENIED, SACRIFICE, SACRIFICE_REWARD,
                     ARCADE_PLAY, ARCADE_WIN, ARCADE_BREAK, DEVIL_OPENED, DEVIL_DEAL, PHOENIX,
                     DESCENT_TICK ->
                        playBody(floor, room, sound.name(), 1.0f);
            }
        }

        @Override
        public int spawnEncounter(Room room) {
            int roomIndex = floor.built.layout().rooms().indexOf(room);
            List<Entity> spawned = EnemySpawner.spawn(floor.level, floor.built, room, roomIndex,
                    1.0f, floor.run.party().size(), swarm(floor));
            for (Entity enemy : spawned) {
                floor.enemyRooms.put(enemy.getUUID(), room);
            }
            raiseBossBar(room, spawned);
            return spawned.size();
        }

        /**
         * A boss fight used to look exactly like an ordinary one — same seal, same sound, and a mob
         * you had no way to read. The bar is what makes it a fight with an arc.
         */
        private void raiseBossBar(Room room, List<Entity> spawned) {
            boolean boss = room.type() == RoomType.BOSS;
            boolean mini = room.type() == RoomType.MINI_BOSS;
            if (!boss && !mini) {
                return;
            }
            for (Entity entity : spawned) {
                if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                    floor.bossBars.add(floor, living,
                            EnemySpawner.authoredIdOf(entity), mini);
                }
            }
            if (boss) {
                // The title lands with the doors, which is the moment the room stops being a room.
                for (UUID member : floor.run.party().keySet()) {
                    ServerPlayer player =
                            floor.level.getServer().getPlayerList().getPlayer(member);
                    if (player != null && player.serverLevel() == floor.level) {
                        DungeonTitles.send(player, "§4§l¡JEFE!", spawned.isEmpty() ? ""
                                : "§7" + es.boffmedia.teras.dungeon.encounter.EnemyNames
                                        .of(EnemySpawner.authoredIdOf(spawned.get(0)))
                                        .getString());
                    }
                }
            }
        }

        /**
         * Later waves are bigger. Salted by wave index so a challenge is not the same wave three
         * times over, and sized by a growth factor so the third one is the one that hurts.
         */
        @Override
        public int spawnChallengeWave(Room room, int wave) {
            int roomIndex = floor.built.layout().rooms().indexOf(room);
            float growth = (float) Math.pow(
                    1.0 + DungeonsConfig.challengeWaveGrowthPct() / 100.0, wave);
            List<Entity> spawned = EnemySpawner.spawn(floor.level, floor.built, room,
                    roomIndex + wave * 1000, growth, floor.run.party().size(), swarm(floor));
            for (Entity enemy : spawned) {
                floor.enemyRooms.put(enemy.getUUID(), room);
            }
            return spawned.size();
        }

        @Override
        public int challengeWaves(Room room) {
            int waves = DungeonsConfig.challengeWaves();
            int extraFrom = DungeonsConfig.challengeExtraWaveStage();
            return extraFrom > 0 && floor.run.stage() >= extraFrom ? waves + 1 : waves;
        }

        @Override
        public void challengeCompleted(Room room) {
            BlockPos plate = markerPos(floor, room, "challenge");
            int reward = CoinDrops.scaleToStage(DungeonsConfig.challengeReward(), floor.run.stage());
            CoinDrops.spawnCoins(floor.level, plate, reward);
            rollLootAt(floor, plate, DungeonsConfig.treasureLootTable());
            for (UUID member : floor.run.party().keySet()) {
                DungeonTitles.send(floor.level.getServer().getPlayerList().getPlayer(member),
                        "§6Desafío superado", "§7+" + reward + " monedas");
            }
        }

        @Override
        public void roomCleared(Room room) {
            BigDecimal reward = BigDecimal.valueOf(DungeonsConfig.clearReward());
            if (reward.signum() <= 0) {
                return;
            }
            for (UUID member : floor.run.party().keySet()) {
                ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
                if (player != null && player.serverLevel() == floor.level) {
                    EconomyStore.deposit(member, reward);
                }
            }
        }

        /**
         * The boss fell, so the way down opens. On a piso with a sala del sello that is the §3.2
         * canon beat staged in props: the seal's runes light, the grate over the pit retracts and
         * the exit room's bars give — the pit itself was authored with the floor. A piso without
         * an exit template keeps the old flow: a 2×2 opening carved in the arena.
         */
        /** How far the seal reveal clears into the boss room in front of the opening. */
        private static final int SEAL_APPROACH_DEPTH = 3;


        @Override
        public void openTrapdoor(Room bossRoom) {
            Room exitRoom = exitRoom();
            if (exitRoom == null) {
                carveArenaTrapdoor(bossRoom);
            } else {
                restoreSeal(exitRoom);
            }
            openDevilDoors();
        }

        private Room exitRoom() {
            for (Room room : floor.built.layout().rooms()) {
                if (room.type() == RoomType.EXIT) {
                    return room;
                }
            }
            return null;
        }

        /** The seal re-pins: runes lit, pit grate retracted, doorway unbarred, reward armed. */
        private void restoreSeal(Room exitRoom) {
            lightSealRunes(exitRoom);
            openPit(exitRoom);
            unbarSealDoors();
            sound(DungeonSound.SEAL_RESTORED, exitRoom);
            floor.pedestals.armAt(floor, exitRoom, ClaimPolicy.Kind.ONE_OF_N,
                    DungeonsConfig.bossLootTable(),
                    floor.built.clampInside(exitRoom, markerPos(floor, exitRoom, "premio"), 2));
            sealCeremony();
        }

        /**
         * The dull runes of the seal glyph swap to their lit block, each with an invisible light
         * stamped into the air above it — a handful of setBlocks, which is the whole prop budget
         * of staging the canon beat every floor.
         */
        private void lightSealRunes(Room exitRoom) {
            var dull = BuiltInRegistries.BLOCK
                    .get(ResourceLocation.parse(DungeonsConfig.sealRuneBlock()));
            var lit = BuiltInRegistries.BLOCK
                    .get(ResourceLocation.parse(DungeonsConfig.sealRuneLitBlock()));
            if (dull == null || lit == null || dull == Blocks.AIR) {
                return;
            }
            int roomSize = floor.built.roomSize();
            for (GridPos cell : exitRoom.cells()) {
                BlockPos origin = floor.built.cellOrigin(cell);
                for (int x = 0; x < roomSize; x++) {
                    for (int y = 0; y < floor.built.roomHeight(); y++) {
                        for (int z = 0; z < roomSize; z++) {
                            BlockPos pos = origin.offset(x, y, z);
                            if (!floor.level.getBlockState(pos).is(dull)) {
                                continue;
                            }
                            floor.level.setBlock(pos, lit.defaultBlockState(), 3);
                            BlockPos above = pos.above();
                            if (floor.level.getBlockState(above).isAir()) {
                                floor.level.setBlock(above, Blocks.LIGHT.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }

        /**
         * Retracts the authored grate: the 3×3 over the pit becomes the drop it promised.
         *
         * <p>The marker is the pit's <b>centre</b> and the hole is carved symmetrically around it,
         * because the exit template is rotated to face the boss. A corner marker rotates fine on its
         * own, but the extent taken from it does not — reading {@code +x/+z} off a rotated corner
         * put the hole two blocks off the grate on three rotations out of four.</p>
         */
        private void openPit(Room exitRoom) {
            BlockPos hole = floor.built.clampInside(exitRoom,
                    markerPos(floor, exitRoom, "trapdoor"), 4);
            int floorY = floor.built.origin().getY();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(hole.getX() + dx, floorY, hole.getZ() + dz);
                    floor.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    floor.level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        /**
         * The wall into the exit room gives way because the seal re-pinned. Nothing stood here at
         * build time — the chamber is solid rock until this carve, the same reveal a secret wall
         * makes when a charge opens it.
         *
         * <p>An aligned 2×2/2×2 attachment shares a full face — two parallel SELLO edges — and gets
         * one wide opening centered on their seam, the grand ceremonial door. A fallback single
         * edge (a 1×1 boss, or a boss too boxed in for an aligned face) gets a normal doorway.</p>
         */
        private void unbarSealDoors() {
            List<DoorEdge> sello = new java.util.ArrayList<>();
            for (DoorEdge door : floor.built.layout().doors()) {
                if (door.kind() == DoorKind.SELLO) {
                    sello.add(door);
                }
            }
            int width;
            int height;
            if (sello.size() >= 2) {
                width = DungeonsConfig.sealDoorWidth();
                height = DungeonsConfig.sealDoorHeight();
                DoorCarver.carveGrandDoor(floor.level, floor.built.origin(), sello,
                        Blocks.AIR.defaultBlockState(), floor.built.roomSize(), width, height);
            } else {
                width = DungeonsConfig.doorWidth();
                height = DungeonsConfig.doorHeight();
                for (DoorEdge door : sello) {
                    DoorCarver.fillDoorway(floor.level, floor.built.origin(), door,
                            Blocks.AIR.defaultBlockState(), floor.built.roomSize(), width, height);
                }
            }
            // Guarantee access: a shallow clear into the boss room in front of the opening, so an
            // authored arena prop against that wall can never leave the party sealed away from the
            // way down. Matches the opening's width and centre; three blocks deep never reaches the
            // boss room's far wall.
            DoorCarver.clearSealApproach(floor.level, floor.built.origin(), sello,
                    floor.built.roomSize(), width, height, SEAL_APPROACH_DEPTH);
        }

        /**
         * The first-clear ceremony, staged on the seal moment (PRODUCCION §10.6): everything the
         * floor owes the party lands as one title stack in the room built for dwelling, not as
         * scattered chat. Today that is the seal itself; esquirla first-clears and codex firsts
         * append here when the ledger exists — presentation only, grants stay where they are made.
         */
        private void sealCeremony() {
            for (UUID member : floor.run.party().keySet()) {
                ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(
                            "§dEl sello se restaura — la roca cede tras la arena."));
                    DungeonTitles.send(player, "§dSello restaurado", "§7La sala del sello se abre");
                }
            }
        }

        /**
         * The fallback for a piso with no exit template: a 2×2 opening through the arena floor,
         * ringed so it reads as a built exit rather than the bare hole the first playtest found.
         */
        private void carveArenaTrapdoor(Room bossRoom) {
            BlockPos hole = trapdoorPos(bossRoom);
            int floorY = floor.built.origin().getY();
            BlockPos corner = new BlockPos(hole.getX(), floorY, hole.getZ());
            for (int dx = -2; dx <= 3; dx++) {
                for (int dz = -2; dz <= 3; dz++) {
                    boolean opening = dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
                    boolean frame = dx >= -1 && dx <= 2 && dz >= -1 && dz <= 2;
                    BlockPos pos = corner.offset(dx, 0, dz);
                    if (opening) {
                        floor.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        floor.level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
                    } else if (frame) {
                        floor.level.setBlock(pos, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3);
                    }
                }
            }
            sound(DungeonSound.TRAPDOOR_OPEN, bossRoom);
            // The boss' own drop, on a stand beside the hole rather than thrown on the ground:
            // one reward, and the party decides who takes it. Gear that fell down the trapdoor was
            // gear nobody saw, and gear on the floor was gear whoever ran fastest got.
            floor.pedestals.armAt(floor, bossRoom, ClaimPolicy.Kind.ONE_OF_N,
                    DungeonsConfig.bossLootTable(),
                    floor.built.clampInside(bossRoom, hole.offset(3, 0, 0), 3));
            for (UUID member : floor.run.party().keySet()) {
                ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(
                            "§6La trampilla al siguiente piso se ha abierto."));
                    DungeonTitles.send(player, "§6Jefe derrotado", "§7La trampilla se abre");
                }
            }
        }

        /**
         * The boss falling is what unbars the devil room — the offer is the floor's reward for
         * finishing it, and the party has to decide before dropping through the trapdoor.
         */
        private void openDevilDoors() {
            boolean devil = false;
            boolean grace = false;
            for (DoorEdge door : floor.built.layout().doors()) {
                if (door.kind() != DoorKind.DEVIL && door.kind() != DoorKind.GRACIA) {
                    continue;
                }
                DoorCarver.fillDoorway(floor.level, floor.built.origin(), door,
                        Blocks.AIR.defaultBlockState(), floor.built.roomSize(),
                        DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
                devil |= door.kind() == DoorKind.DEVIL;
                grace |= door.kind() == DoorKind.GRACIA;
            }
            if (devil) {
                // He arrives with the door, never behind it: a character standing in a barred room
                // is one the party watches through the bars for the length of a boss fight.
                Room room = DungeonNpcs.acreedorRoom(floor);
                if (room != null) {
                    DungeonNpcs.spawnFor(floor, room, DungeonNpcs.Role.ACREEDOR);
                }
            }
            if (grace) {
                Room room = DungeonNpcs.ordenRoom(floor);
                if (room != null) {
                    DungeonNpcs.spawnFor(floor, room, DungeonNpcs.Role.ORDEN);
                }
            }
            if (devil || grace) {
                message(floor, devil && grace
                        // Both walls open at once, and only one may be walked through.
                        ? "§5A un lado el trato, §6al otro la gracia §7— sólo una puerta se cruza."
                        : devil ? "§5Los barrotes del trato ceden — algo espera al otro lado."
                                : "§6La Orden abre — algo limpio espera al otro lado.");
                playAt(floor, floor.built.partySpawn(floor.built.layout().start()),
                        DungeonSound.DEVIL_OPENED, 1.0f);
            }
        }

        @Override
        public void syncMap() {
            syncMapFor(floor);
        }

        /**
         * Clamped three blocks off the walls: the carved opening plus its frame reach two blocks
         * out from this position, and a marker authored against a wall would otherwise eat it.
         */
        private BlockPos trapdoorPos(Room bossRoom) {
            return floor.built.clampInside(bossRoom, markerPos(floor, bossRoom, "trapdoor"), 3);
        }

        /**
         * The curse room charges at the door and pays inside. The toll used to be the whole room,
         * which made walking in a straight loss — there was nothing on the other side of it. Now
         * the price buys a roll on a rare-skewed table, so it is a wager rather than a tax.
         *
         * <p>Priced in coins; the old ₽ toll still fires when a server sets one, but it defaults to
         * zero. Being unable to pay costs blood instead, and under the health lockdown that bite
         * does not heal off.</p>
         */
    }
}
