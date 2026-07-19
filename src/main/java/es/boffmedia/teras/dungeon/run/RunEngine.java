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
 * <p>The trapdoor is a real hole: boss clear carves it open and whoever drops through is caught
 * below floor level and advanced to the next stage. Player deaths respawn at the floor's start
 * room with the configured money penalty.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
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

    private static final class ActiveFloor {
        final DungeonRun run;
        final BuiltDungeon built;
        final ServerLevel level;
        final RunCore core;
        final Map<UUID, Room> enemyRooms = new HashMap<>();
        final Map<UUID, GridPos> lastCell = new HashMap<>();
        final java.util.Set<DoorEdge> openedSecrets = new java.util.HashSet<>();
        boolean advancing;

        ActiveFloor(DungeonRun run, BuiltDungeon built, ServerLevel level) {
            this.run = run;
            this.built = built;
            this.level = level;
            this.core = new RunCore(built.layout(), new FloorCallbacks(this));
        }
    }

    /** Starts (or replaces, on stage advance) the loop for a run's built floor. */
    public static void register(DungeonRun run, BuiltDungeon built, ServerLevel level) {
        ActiveFloor floor = new ActiveFloor(run, built, level);
        FLOORS.put(run.id(), floor);
        floor.core.start();
    }

    public static void unregister(int runId) {
        ActiveFloor floor = FLOORS.remove(runId);
        if (floor != null) {
            removeRemainingEnemies(floor);
            for (UUID member : floor.run.party().keySet()) {
                ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, DungeonMapPayload.hidden());
                }
            }
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

    /** Plays a cue positioned on an enemy, for the ability layer's telegraphs. */
    public static void playAbilityCue(Entity source, DungeonSound cue) {
        SoundEvent event = soundEvent(cue.name());
        if (event != null && source.level() instanceof ServerLevel level) {
            level.playSound(null, source.getX(), source.getY(), source.getZ(), event,
                    SoundSource.HOSTILE, DungeonsConfig.soundVolume(), 1.0f);
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
        FLOORS.remove(floor.run.id());
        try {
            DungeonRunManager.end(floor.level.getServer(), floor.run.id());
        } catch (Throwable t) {
            Teras.LOGGER.error("Dungeons: could not cleanly end run {}", floor.run.id(), t);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonRun run = DungeonRunManager.runOf(player.getUUID());
            if (run != null && FLOORS.containsKey(run.id())) {
                RESPAWN_AT_START.put(player.getUUID(), run.id());
            }
            return;
        }
        UUID id = event.getEntity().getUUID();
        for (ActiveFloor floor : FLOORS.values()) {
            Room room = floor.enemyRooms.remove(id);
            if (room != null) {
                floor.core.enemyRemoved(room);
                return;
            }
        }
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
        BlockPos start = floor.built.roomCenter(floor.built.layout().start());
        player.teleportTo(floor.level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        land(player);
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
     * Secret and super-secret rooms open by <b>interacting</b> with the wall, not by breaking it —
     * the party plays in adventure mode, so breaking was never available, and Isaac's bomb becomes
     * a touch: the cracked bricks of a SECRET edge are the visible invitation, while a HIDDEN edge
     * looks like any other wall and rewards players who press the suspicious ones. When the shop
     * arrives, a purchasable detector/charge can gate this same routine; the opening itself stays
     * exactly {@link DoorCarver}'s doorway volume, so what gives way is precisely the passage.
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
        for (DoorEdge door : floor.built.layout().doors()) {
            if (door.kind() != DoorKind.SECRET_CRACK && door.kind() != DoorKind.HIDDEN) {
                continue;
            }
            if (!DoorCarver.doorwayContains(floor.built.origin(), door, pos, floor.built.roomSize(),
                    DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight())) {
                continue;
            }
            if (!floor.openedSecrets.add(door)) {
                return;
            }
            DoorCarver.fillDoorway(floor.level, floor.built.origin(), door,
                    Blocks.AIR.defaultBlockState(), floor.built.roomSize(),
                    DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
            SoundEvent sound = soundEvent("SECRET_OPENED");
            if (sound != null) {
                floor.level.playSound(null, pos, sound, SoundSource.BLOCKS,
                        DungeonsConfig.soundVolume(), 1.0f);
            }
            player.displayClientMessage(
                    Component.literal("§7El muro cede — un pasaje se abre."), true);
            event.setCanceled(true);
            return;
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

    private static void scanPlayers(ActiveFloor floor) {
        for (UUID member : floor.run.party().keySet()) {
            ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
            if (player == null || player.serverLevel() != floor.level) {
                continue;
            }
            BlockPos origin = floor.built.origin();
            boolean belowFloor = player.getY() < origin.getY() - 2;
            if (floor.core.isTrapdoorOpen() && !floor.advancing && belowFloor) {
                floor.advancing = true;
                // Everyone under the floor is caught before the next one starts building: the drop
                // takes a second or two, and a player left falling arrives with enough accumulated
                // fall distance to die on landing — or drops far enough to reach the void.
                for (UUID falling : floor.run.party().keySet()) {
                    ServerPlayer other = floor.level.getServer().getPlayerList().getPlayer(falling);
                    if (other != null && other.serverLevel() == floor.level
                            && other.getY() < origin.getY() - 2) {
                        holdWhileDescending(other);
                    }
                }
                DungeonRunManager.advanceStage(floor.run, floor.level);
                return;
            }
            if (belowFloor) {
                BlockPos start = floor.built.roomCenter(floor.built.layout().start());
                player.teleportTo(floor.level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                land(player);
                continue;
            }
            int cellX = Math.floorDiv(player.blockPosition().getX() - origin.getX(), floor.built.roomSize());
            int cellY = Math.floorDiv(player.blockPosition().getZ() - origin.getZ(), floor.built.roomSize());
            GridPos cell = new GridPos(cellX, cellY);
            floor.core.playerEnteredCell(member, cell, isClearOfDoors(floor, cell, player));
            if (!cell.equals(floor.lastCell.put(member, cell))) {
                sendMap(floor, player, cell);
            }
        }
    }

    /**
     * Whether every doorway of the room at {@code cell} can be filled without any of it landing
     * inside {@code player}. Tested as the actual overlap between the player's bounding box and
     * the blocks {@link DoorCarver} is about to write, so stepping one block clear of the gap is
     * enough — a distance check from the doorway's middle looks equivalent and is not: it keeps
     * the room open until the player is several blocks in, long after they have committed.
     */
    private static boolean isClearOfDoors(ActiveFloor floor, GridPos cell, ServerPlayer player) {
        Room room = floor.built.layout().grid().roomAt(cell);
        return room != null && doorwayHolding(floor, room, player) == null;
    }

    /** The first doorway of {@code room} the player is standing in, or null if they are clear. */
    private static DoorEdge doorwayHolding(ActiveFloor floor, Room room, ServerPlayer player) {
        AABB body = player.getBoundingBox();
        for (DoorEdge door : floor.built.layout().doorsOf(room)) {
            if (door.kind() != DoorKind.OPEN && door.kind() != DoorKind.BOSS) {
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

    /** The player's full minimap snapshot: discovered rooms plus dim outlines behind their doors. */
    private static void sendMap(ActiveFloor floor, ServerPlayer player, GridPos playerCell) {
        List<DungeonMapPayload.Cell> cells = new java.util.ArrayList<>();
        var discovered = floor.core.discovered();
        java.util.Set<GridPos> outlined = new java.util.HashSet<>();
        for (Room room : discovered) {
            for (GridPos cell : room.cells()) {
                cells.add(new DungeonMapPayload.Cell(cell.x(), cell.y(),
                        room.type().ordinal(), floor.core.state(room).ordinal()));
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
                    GridPos cell = door.from() == room ? door.neighborCell() : door.cell();
                    if (outlined.add(cell)) {
                        cells.add(new DungeonMapPayload.Cell(cell.x(), cell.y(),
                                DungeonMapPayload.TYPE_UNKNOWN, 0));
                    }
                }
            }
        }
        boolean mapHidden = floor.run.curses().contains(es.boffmedia.teras.dungeon.model.Curse.LOST);
        PacketDistributor.sendToPlayer(player, new DungeonMapPayload(true,
                floor.built.layout().grid().size(), floor.run.stage(), mapHidden,
                playerCell.x(), playerCell.y(), cells));
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
            int cellX = Math.floorDiv(player.blockPosition().getX() - floor.built.origin().getX(),
                    floor.built.roomSize());
            int cellY = Math.floorDiv(player.blockPosition().getZ() - floor.built.origin().getZ(),
                    floor.built.roomSize());
            if (floor.built.layout().grid().roomAt(new GridPos(cellX, cellY)) == room) {
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
    }

    private static BlockState sealState() {
        return BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse(DungeonsConfig.sealBlock()))
                .defaultBlockState();
    }

    /**
     * Plays a cue for a room: once at every doorway, and once from the middle of the room. Both
     * halves matter — the doorway copies are what make a seal read as <i>these</i> doors shutting
     * on you rather than an ambient noise, and the body sound carries the weight of it. Only OPEN
     * and BOSS edges are used, the same set {@link DoorCarver} fills.
     */
    private static void playCue(ActiveFloor floor, Room room, String cue, float pitch) {
        SoundEvent event = soundEvent(cue);
        if (event == null) {
            return;
        }
        float volume = DungeonsConfig.soundVolume();
        for (DoorEdge door : floor.built.layout().doorsOf(room)) {
            if (door.kind() != DoorKind.OPEN && door.kind() != DoorKind.BOSS) {
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
            if (room.type() == RoomType.TREASURE) {
                rollTreasure(room);
            } else if (room.type() == RoomType.CURSE && discoverer != null) {
                chargeToll(discoverer);
            }
        }

        @Override
        public void sealRoom(Room room) {
            clearDoorways(floor, room);
            DoorCarver.setRoomDoors(floor.level, floor.built, room, sealState());
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
                // Fired directly at the clicked wall by onRightClickBlock, not through the
                // callback; the case keeps this switch total over the enum.
                case SECRET_OPENED -> playBody(floor, room, "SECRET_OPENED", 1.0f);
                case ENEMY_ENRAGED -> playBody(floor, room, "ENEMY_ENRAGED", 1.1f);
            }
        }

        @Override
        public int spawnEncounter(Room room) {
            int roomIndex = floor.built.layout().rooms().indexOf(room);
            List<Entity> spawned = EnemySpawner.spawn(floor.level, floor.built, room, roomIndex);
            for (Entity enemy : spawned) {
                floor.enemyRooms.put(enemy.getUUID(), room);
            }
            return spawned.size();
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
         * A 2×2 opening through the floor, ringed so it reads as a built exit rather than the
         * bare hole the first playtest found. Falling through it is what advances the stage.
         */
        @Override
        public void openTrapdoor(Room bossRoom) {
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
            for (UUID member : floor.run.party().keySet()) {
                ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(
                            "§6La trampilla al siguiente piso se ha abierto."));
                }
            }
        }

        @Override
        public void syncMap() {
            for (UUID member : floor.run.party().keySet()) {
                ServerPlayer player = floor.level.getServer().getPlayerList().getPlayer(member);
                if (player != null && player.serverLevel() == floor.level) {
                    sendMap(floor, player, floor.lastCell.getOrDefault(member,
                            floor.built.layout().start().cells().get(0)));
                }
            }
        }

        /**
         * Clamped three blocks off the walls: the carved opening plus its frame reach two blocks
         * out from this position, and a marker authored against a wall would otherwise eat it.
         */
        private BlockPos trapdoorPos(Room bossRoom) {
            return floor.built.clampInside(bossRoom, markerPos(bossRoom, "trapdoor"), 3);
        }

        private void rollTreasure(Room room) {
            BlockPos pos = floor.built.clampInside(room, markerPos(room, "loot"), 1);
            LootTable table = floor.level.getServer().reloadableRegistries().getLootTable(
                    ResourceKey.create(Registries.LOOT_TABLE,
                            ResourceLocation.parse(DungeonsConfig.treasureLootTable())));
            LootParams params = new LootParams.Builder(floor.level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params)) {
                // The 5-arg constructor gives drops a random pop of velocity — fine for a mob
                // kill, wrong for a reward pedestal: items scattered around the room, sometimes
                // out of sight, and read as the loot not having spawned at all. Zero motion: the
                // reward stands exactly on its marker.
                ItemEntity item = new ItemEntity(floor.level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack, 0, 0, 0);
                item.setDefaultPickUpDelay();
                floor.level.addFreshEntity(item);
            }
        }

        /** The room's marker of {@code kind}, else its exact center — loudly, not silently. */
        private BlockPos markerPos(Room room, String kind) {
            for (TemplateMarkers.Marker marker : floor.built.markers().getOrDefault(room, List.of())) {
                if (marker.kind().equals(kind)) {
                    return marker.pos();
                }
            }
            Teras.LOGGER.warn("Dungeons: {} has no '{}' marker — using the room center. "
                    + "Add one to its template with the room editor.", room, kind);
            return floor.built.roomCenter(room);
        }

        private void chargeToll(UUID player) {
            BigDecimal toll = BigDecimal.valueOf(DungeonsConfig.curseToll());
            if (toll.signum() <= 0) {
                return;
            }
            ServerPlayer online = floor.level.getServer().getPlayerList().getPlayer(player);
            if (EconomyStore.withdraw(player, toll)) {
                if (online != null) {
                    online.sendSystemMessage(Component.literal(
                            "§5La sala maldita cobra su peaje: " + toll + " ₽."));
                }
            } else if (online != null) {
                online.hurt(online.damageSources().magic(), 6.0f);
                online.sendSystemMessage(Component.literal(
                        "§5No puedes pagar el peaje — la maldición muerde."));
            }
        }
    }
}
