package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomType;
import net.minecraft.world.level.block.Rotation;
import es.boffmedia.teras.dungeon.piso.DecorTables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Materializes {@link DungeonLayout}s into a world, one room per server tick — the legacy command
 * queued every WorldEdit paste onto the same tick and stalled the server for the whole floor. A
 * 20-room floor lands in about a second; doors are carved in one final step because they are a few
 * hundred blocks, not thousands.
 *
 * <p>Door edges follow their {@link es.boffmedia.teras.dungeon.model.DoorKind}: OPEN and BOSS are
 * carved through both wall layers, SECRET_CRACK is filled with a visibly cracked block (the
 * bomb-replacement entrance), HIDDEN is left untouched — the super-secret wall must not exist as
 * far as anyone can see. The legacy paster skipped secret doors entirely and produced sealed,
 * unreachable rooms.</p>
 *
 * <p>Discards clear one cell box per tick from the recorded footprint. Neither build nor discard
 * state survives a restart in this stage; the instance journal is stage 3 (DUNGEONS.md §11).</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DungeonMaterializer {
    private DungeonMaterializer() {}

    private static final Map<Integer, BuiltDungeon> BUILT = new LinkedHashMap<>();
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static int nextId = 1;

    private interface Job {
        /** One tick of work; true when the job is finished. */
        boolean step();

        /**
         * The job died and was dropped. A build that dies leaves a run that can never activate and
         * a pad with rooms already pasted into it, so somebody has to be told; a discard that dies
         * leaves the journal in place for the boot sweep and needs nobody.
         */
        default void onFailed() {}
    }

    /**
     * @param plan what this floor is: the piso supplying every template the build pastes, and the
     *             depth its enemies and decoration are scaled by. Carried through to the
     *             {@link BuiltDungeon} so the run stage reads one decided answer.
     */
    public static int enqueueBuild(ServerLevel level, DungeonLayout layout,
                                   es.boffmedia.teras.dungeon.piso.FloorPlan plan, BlockPos origin,
                                   Consumer<BuiltDungeon> onComplete) {
        return enqueueBuild(level, layout, plan, origin, onComplete, () -> { });
    }

    /**
     * @param onFailed run when the build dies and is dropped
     * @param draw     the run's position in the once-per-floor variant bags. A floor built outside a
     *                 run passes {@link es.boffmedia.teras.dungeon.piso.VariantDraw#single} and
     *                 deals position 0, which is what a run's own first floor gets
     */
    public static int enqueueBuild(ServerLevel level, DungeonLayout layout,
                                   es.boffmedia.teras.dungeon.piso.FloorPlan plan, BlockPos origin,
                                   Consumer<BuiltDungeon> onComplete, Runnable onFailed,
                                   es.boffmedia.teras.dungeon.piso.VariantDraw draw) {
        int id = nextId++;
        JOBS.add(new BuildJob(id, level, layout, plan, origin, onComplete, onFailed, draw));
        return id;
    }

    /**
     * @param onFailed run when the build dies and is dropped. Without it a caller waits forever on
     *                 a completion that is never coming — which is how a failed build used to leave
     *                 a run stuck mid-construction, holding its slot with rooms already on the pad
     */
    public static int enqueueBuild(ServerLevel level, DungeonLayout layout,
                                   es.boffmedia.teras.dungeon.piso.FloorPlan plan, BlockPos origin,
                                   Consumer<BuiltDungeon> onComplete, Runnable onFailed) {
        return enqueueBuild(level, layout, plan, origin, onComplete, onFailed,
                es.boffmedia.teras.dungeon.piso.VariantDraw.single(layout.seedString()));
    }

    /** False when no built dungeon has that id. */
    public static boolean enqueueDiscard(int id, ServerLevel level, Runnable onComplete) {
        BuiltDungeon built = BUILT.get(id);
        if (built == null || !built.dimension().equals(level.dimension())) {
            return false;
        }
        BUILT.remove(id);
        List<BlockPos> cells = new ArrayList<>();
        for (Room room : built.layout().rooms()) {
            for (GridPos cell : room.cells()) {
                cells.add(built.cellOrigin(cell));
            }
        }
        enqueueClear(level, cells, built.roomSize(), built.roomHeight(), onComplete);
        return true;
    }

    /**
     * Air-fills the given cell boxes, one per tick. Public on its own because the boot sweep
     * clears floors known only from the run journal, where no {@link BuiltDungeon} exists anymore.
     */
    public static void enqueueClear(ServerLevel level, List<BlockPos> cellOrigins,
                                    int roomSize, int roomHeight, Runnable onComplete) {
        JOBS.add(new DiscardJob(level, cellOrigins, roomSize, roomHeight, onComplete));
    }

    public static Collection<BuiltDungeon> built() {
        return List.copyOf(BUILT.values());
    }

    public static BuiltDungeon get(int id) {
        return BUILT.get(id);
    }

    /** A failed job is dropped, never retried: the queue must keep draining for other runs. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Job job = JOBS.peek();
        if (job == null) {
            return;
        }
        try {
            if (job.step()) {
                JOBS.poll();
            }
        } catch (Throwable t) {
            JOBS.poll();
            Teras.LOGGER.error("Dungeons: build/discard job failed and was dropped", t);
            try {
                job.onFailed();
            } catch (Throwable inner) {
                Teras.LOGGER.error("Dungeons: cleanup for the failed job also failed", inner);
            }
        }
    }

    /**
     * Shutdown finishes the tearing-down and abandons the building-up.
     *
     * <p>This used to be {@code JOBS.clear()}, which dropped discards that were halfway through a
     * floor: the blocks stayed, the shop displays and reward pedestals stayed, and the world was
     * saved that way. It only ever looked fine because the run journal made the next boot sweep it.
     * A server that is stopped cleanly should not need a boot sweep to be clean.</p>
     *
     * <p>Draining synchronously is safe and is the whole point of doing it here: NeoForge fires this
     * event from the tick loop, <b>before</b> {@code stopServer()} saves players and chunks, so the
     * cleared cells are what gets written to disk. Builds are dropped rather than finished — nobody
     * is going to play that floor, and its cells are journalled either way.</p>
     *
     * <p>Runs at {@link EventPriority#LOWEST} so {@code DungeonRunManager} has already ended every
     * live run and queued its discard by the time this drains.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        int drained = 0;
        for (Job job : List.copyOf(JOBS)) {
            if (!(job instanceof DiscardJob)) {
                continue;
            }
            try {
                while (!job.step()) {
                    // one cell per step, bounded by the floor's own footprint
                }
                drained++;
            } catch (Throwable t) {
                Teras.LOGGER.error("Dungeons: could not finish a discard at shutdown; the run "
                        + "journal will have the boot sweep clear it", t);
            }
        }
        if (drained > 0) {
            Teras.LOGGER.info("Dungeons: cleared {} pending floor(s) before shutdown", drained);
        }
        JOBS.clear();
        BUILT.clear();
    }

    /**
     * Air-fills a whole run pad, whether or not anything is known to be there.
     *
     * <p>The last resort behind the journal: an orphan whose journal was lost, hand-built geometry,
     * anything from before the journal existed. Sized to the largest floor the generator can produce
     * rather than to a layout, because by definition there is no layout to ask.</p>
     *
     * @return how many cell boxes were queued
     */
    public static int enqueuePadClear(ServerLevel level, BlockPos padOrigin, int gridSize,
                                      Runnable onComplete) {
        int roomSize = DungeonsConfig.roomSize();
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                cells.add(padOrigin.offset(x * roomSize, 0, z * roomSize));
            }
        }
        enqueueClear(level, cells, roomSize, DungeonsConfig.roomHeight(), onComplete);
        return cells.size();
    }

    private static final class BuildJob implements Job {
        private final int id;
        private final ServerLevel level;
        private final DungeonLayout layout;
        private final BlockPos origin;
        private final Consumer<BuiltDungeon> onComplete;
        private final Runnable onFailed;
        private final List<Room> rooms;
        private final es.boffmedia.teras.dungeon.piso.FloorPlan plan;
        private final Map<Room, List<TemplateMarkers.Marker>> markers = new HashMap<>();
        /** How many rooms of each key have been placed, which is the bag's deal position. */
        private final Map<String, Integer> drawn = new HashMap<>();
        private final es.boffmedia.teras.dungeon.piso.VariantDraw draw;
        /**
         * The keys this floor asks for exactly once. Those draw from the run bag instead of the
         * floor's, so the room a party stands in on every single floor is not re-rolled from
         * scratch on every single floor — see {@code VariantDraw}. Counted up front because the
         * answer has to be the same for the first room placed and the last.
         */
        private final java.util.Set<String> soloKeys;
        private final int roomSize = DungeonsConfig.roomSize();
        private final int roomHeight = DungeonsConfig.roomHeight();
        private int index;

        BuildJob(int id, ServerLevel level, DungeonLayout layout,
                 es.boffmedia.teras.dungeon.piso.FloorPlan plan, BlockPos origin,
                 Consumer<BuiltDungeon> onComplete, Runnable onFailed,
                 es.boffmedia.teras.dungeon.piso.VariantDraw draw) {
            this.id = id;
            this.level = level;
            this.layout = layout;
            this.plan = plan;
            this.origin = origin;
            this.onComplete = onComplete;
            this.onFailed = onFailed;
            this.draw = draw;
            this.rooms = layout.rooms();
            this.soloKeys = soloKeys(this.rooms);
        }

        /** Room keys exactly one room on this floor resolves to. */
        private static java.util.Set<String> soloKeys(List<Room> rooms) {
            Map<String, Integer> counts = new HashMap<>();
            for (Room room : rooms) {
                counts.merge(RoomTemplates.keyFor(room), 1, Integer::sum);
            }
            java.util.Set<String> solo = new java.util.HashSet<>();
            counts.forEach((key, count) -> {
                if (count == 1) {
                    solo.add(key);
                }
            });
            return solo;
        }

        @Override
        public void onFailed() {
            // Whatever was pasted before the failure is still standing, and BUILT never learned
            // about it — so the caller's cleanup is the only thing that can reach those cells.
            BUILT.remove(id);
            onFailed.run();
        }

        @Override
        public boolean step() {
            if (index == 0) {
                // Nothing may already be standing where the floor goes. A CustomNPCs corpse
                // waiting out a respawn timer survives the run teardown (it left the kill ledger
                // when it died), and pasting rooms around it is how playtest 3 got enemies
                // embedded in the ground of the next floor's treasure rooms.
                sweepEntities(level, origin, layout.grid().size() * roomSize, roomHeight);
            }
            if (index < rooms.size()) {
                Room room = rooms.get(index);
                // Per key, not per floor: the variant bag deals one cycle per room key, so a
                // floor-wide index would jump around inside the cycle and cluster again.
                String key = RoomTemplates.keyFor(room);
                placeRoom(room, drawn.merge(key, 1, Integer::sum) - 1);
                index++;
                return false;
            }
            carveDoors();
            BuiltDungeon built = new BuiltDungeon(id, level.dimension(), origin, layout, plan,
                    roomSize, roomHeight, Map.copyOf(markers));
            BUILT.put(id, built);
            onComplete.accept(built);
            return true;
        }

        private void placeRoom(Room room, int ordinalInKey) {
            // Two bags, and which one a key uses is the whole of the fix: a key this floor asks for
            // once deals from the run's stage-free bag at this piso's floor ordinal, everything else
            // from the floor's own. Both are pure functions of the seed and the run's shape, so a
            // floor still rebuilds identically from the same inputs.
            boolean solo = soloKeys.contains(RoomTemplates.keyFor(room));
            long seed = solo ? draw.seed() : layout.baseSeed();
            int ordinal = solo ? draw.pisoOrdinal() : ordinalInKey;
            RoomTemplates.TemplateEntry entry =
                    RoomTemplates.select(plan.piso(), room, seed, ordinal);
            if (room.type() == RoomType.EXIT) {
                // The exit is authored with a fixed front (the boss-facing wall the grand door
                // carves) and back (the trophy gallery); unlike every other room its facing is not
                // its own — it is turned to point its entrance at whichever side the boss landed on.
                entry = new RoomTemplates.TemplateEntry(entry.name(), entry.template(),
                        entry.weight(), exitFacing(room));
            } else if (room.type() == RoomType.SHOP) {
                // Directional like the exit, for the same reason: the shop is authored with its
                // counter on the north wall, and it is turned so that wall lands on a doorless side
                // (PISOS §66) — a shopfront the party walks up to, not wares scattered around the
                // doorway they entered through.
                entry = new RoomTemplates.TemplateEntry(entry.name(), entry.template(),
                        entry.weight(), shopFacing(room));
            }
            StructureTemplate template = level.getStructureManager().get(entry.template()).orElse(null);
            if (template == null) {
                Teras.LOGGER.error("Dungeons: missing template {} for {} — leaving the cell empty",
                        entry.template(), room);
                markers.put(room, List.of());
                return;
            }
            if (!fitsItsCells(room, template, entry)) {
                markers.put(room, List.of());
                return;
            }
            BlockPos nominal = origin.offset(
                    room.anchor().x() * roomSize, 0, room.anchor().y() * roomSize);
            // Before the paste, not after: the template brings its own entities and sweeping
            // afterwards would delete them. Before is also the only moment the volume is still
            // the *old* floor's, which is what has to go.
            for (GridPos cell : room.shape().offsets()) {
                sweepLoadedEntities(level, nominal.offset(cell.x() * roomSize, 0,
                        cell.y() * roomSize), roomSize, roomHeight);
            }
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(entry.rotation())
                    .setIgnoreEntities(false);
            // Rotation happens around the template origin, so the rotated box can extend into
            // negative coordinates; shifting by (nominal − box min) pins the min corner to the
            // cell regardless of size or rotation — the general fix for the legacy roomSize−1
            // offset patchwork.
            BoundingBox box = template.getBoundingBox(settings, nominal);
            BlockPos corrected = nominal.offset(
                    nominal.getX() - box.minX(),
                    nominal.getY() - box.minY(),
                    nominal.getZ() - box.minZ());
            template.placeInWorld(level, corrected, corrected, settings, level.getRandom(), 2);

            List<TemplateMarkers.Marker> roomMarkers =
                    TemplateMarkers.extract(template, settings, corrected);
            for (TemplateMarkers.Marker marker : roomMarkers) {
                if (Decorator.isDecorMarker(marker.kind())) {
                    decorate(room, marker.pos(), marker.kind());
                    continue;
                }
                level.setBlock(marker.pos(), markerFloor(room, marker.kind(), marker.pos()), 2);
            }
            markers.put(room, roomMarkers);
        }

        /**
         * The rotation that turns the exit template's authored entrance — the {@code +z} (south)
         * wall — to face the boss. Every other room's rotation serves its shape; the exit's serves
         * its position, so the grand door always carves the front wall and the flank walls always
         * end up on the sides, wherever the boss attached.
         *
         * <p>Read off the boss's centre relative to the exit's. A rotation maps a south-facing
         * feature to {@code R(south)}: CLOCKWISE_90 sends south→west, so a boss to the west wants
         * that turn, and so on around.</p>
         */
        private Rotation exitFacing(Room exit) {
            Room boss = null;
            for (Room room : layout.rooms()) {
                if (room.type() == RoomType.BOSS) {
                    boss = room;
                    break;
                }
            }
            if (boss == null) {
                return Rotation.NONE;
            }
            double dx = avgX(boss) - avgX(exit);
            double dz = avgZ(boss) - avgZ(exit);
            if (Math.abs(dx) > Math.abs(dz)) {
                return dx < 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
            }
            return dz >= 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        }

        /**
         * Turns a shop so its authored counter (on the north wall) lands on a wall with no door,
         * preferring the wall opposite a doorway so the party enters facing the wares. Falls back to
         * the authored orientation only when all four walls carry doors — then the counter stays
         * split around a doorway, as it always did.
         */
        private Rotation shopFacing(Room room) {
            java.util.EnumSet<es.boffmedia.teras.dungeon.model.GridDir> doors =
                    java.util.EnumSet.noneOf(es.boffmedia.teras.dungeon.model.GridDir.class);
            for (es.boffmedia.teras.dungeon.model.DoorEdge door : layout.doors()) {
                if (door.from() == room) {
                    doors.add(door.dir());
                } else if (door.to() == room) {
                    doors.add(door.dir().opposite());
                }
            }
            for (es.boffmedia.teras.dungeon.model.GridDir door : doors) {
                if (!doors.contains(door.opposite())) {
                    return counterToward(door.opposite());
                }
            }
            for (es.boffmedia.teras.dungeon.model.GridDir dir
                    : es.boffmedia.teras.dungeon.model.GridDir.values()) {
                if (!doors.contains(dir)) {
                    return counterToward(dir);
                }
            }
            return Rotation.NONE;
        }

        /** The rotation that carries the authored north counter to {@code target}. */
        private static Rotation counterToward(es.boffmedia.teras.dungeon.model.GridDir target) {
            return switch (target) {
                case NORTH -> Rotation.NONE;
                case EAST -> Rotation.CLOCKWISE_90;
                case SOUTH -> Rotation.CLOCKWISE_180;
                case WEST -> Rotation.COUNTERCLOCKWISE_90;
            };
        }

        private static double avgX(Room room) {
            return room.cells().stream().mapToInt(GridPos::x).average().orElse(0);
        }

        private static double avgZ(Room room) {
            return room.cells().stream().mapToInt(GridPos::y).average().orElse(0);
        }

        /**
         * Fills a decoration marker from the piso's surface table — the {@code decoracion:*} markers
         * authored into a room become dripstone, moss, webbing, whatever the piso declares, and a
         * different roll each run. Silent for a piso with no decoration table: the marker clears the
         * same as any unhandled one, so a room may carry the markers before the piso has the tables.
         */
        private void decorate(Room room, BlockPos pos, String kind) {
            DecorTables tables = plan == null || plan.piso() == null
                    ? DecorTables.EMPTY : plan.piso().decoracion();
            String structure = Decorator.structureFor(tables, kind, pos, layout.baseSeed());
            if (structure != null) {
                placeDecorStructure(pos, structure);
                return;
            }
            level.setBlock(pos, Decorator.blockFor(level, tables, kind, pos, layout.baseSeed()), 2);
        }

        /** A decoration that is a small structure rather than a block, pasted at the marker. */
        private void placeDecorStructure(BlockPos pos, String structureId) {
            StructureTemplate structure = level.getStructureManager()
                    .get(net.minecraft.resources.ResourceLocation.parse(structureId)).orElse(null);
            if (structure == null) {
                Teras.LOGGER.warn("Dungeons: decoracion structure '{}' is missing — cleared", structureId);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                return;
            }
            structure.placeInWorld(level, pos, pos, new StructurePlaceSettings(),
                    level.getRandom(), 2);
        }

        /**
         * Whether {@code template}, once turned, covers exactly the cells {@code room} owns — the
         * one thing the paste itself cannot check, because it pins the rotated bounding box to the
         * anchor and writes whatever size the template happens to be.
         *
         * <p>A template larger than its room does not merely look wrong: the excess lands inside
         * whichever room occupies the next cell, and since rooms are pasted in placement order, the
         * damage depends on which was built first. That is unfixable from the floor's side, so an
         * oversized room is refused outright and the cells are left empty — one visibly missing room
         * beats a floor whose neighbours have been quietly overwritten.</p>
         *
         * <p>A template smaller than its room is pasted anyway: it cannot touch anything else, and
         * the room is at least partly there. Both cases log the same actionable line, because both
         * have the same two causes — a stale {@code tamanoSala}, or templates in the world's
         * {@code generated} folder (saved by the room editor, or stamped by {@code piso crear})
         * shadowing the jar's with an older geometry.</p>
         */
        private boolean fitsItsCells(Room room, StructureTemplate template,
                                     RoomTemplates.TemplateEntry entry) {
            var size = template.getSize();
            boolean quarterTurn = entry.rotation() == net.minecraft.world.level.block.Rotation.CLOCKWISE_90
                    || entry.rotation() == net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            int pastedX = quarterTurn ? size.getZ() : size.getX();
            int pastedZ = quarterTurn ? size.getX() : size.getZ();
            int wantX = room.shape().cellsWide() * roomSize;
            int wantZ = room.shape().cellsDeep() * roomSize;
            if (pastedX == wantX && pastedZ == wantZ) {
                return true;
            }
            boolean oversized = pastedX > wantX || pastedZ > wantZ;
            Teras.LOGGER.error("Dungeons: {} covers {}x{} but its room owns {}x{} — {}. Either "
                            + "config.yml's tamanoSala is stale, or a copy in the world's "
                            + "'generated' folder is shadowing the jar's template. "
                            + "'/teras dungeon piso info <piso>' lists both; "
                            + "'/teras dungeon piso limpiar <piso>' drops the local copies.",
                    entry.template(), pastedX, pastedZ, wantX, wantZ,
                    oversized ? "the room was skipped so it cannot overwrite its neighbours"
                            : "the room was placed and leaves part of its footprint empty");
            return !oversized;
        }

        /**
         * What a marker block leaves behind. Every marker is consumed into air except the ones a
         * player is meant to find and stand on: a challenge or sacrifice plate is triggered by
         * position, so the block is only decoration — but without it the trigger is an invisible
         * tile on an empty floor.
         *
         * <p>Only laid on something solid. A plate authored a block off the ground would pop off
         * as an item on the first block update, leaving litter in the room and no visible plate.</p>
         */
        private BlockState markerFloor(Room room, String kind, BlockPos pos) {
            // A nest has to be visible on entry — seeing it is the decision the mechanic is built
            // on, so unlike every other marker it leaves a block behind rather than being aired out.
            // Never in a doorway, though: a template authored with one there would brick its own
            // entrance, and the block outlives the marker.
            if (kind.equals("nido")) {
                if (inDoorway(room, pos)) {
                    Teras.LOGGER.warn("Dungeons: a 'nido' in {} sits in a doorway and was dropped — "
                            + "move it a few blocks inside the room", room);
                    return Blocks.AIR.defaultBlockState();
                }
                return Blocks.SNIFFER_EGG.defaultBlockState();
            }
            boolean plate = kind.equals("challenge") || kind.equals("sacrifice");
            if (plate && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                return Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        }

        /** Whether a world position falls in one of {@code room}'s reserved doorway volumes. */
        private boolean inDoorway(Room room, BlockPos pos) {
            for (GridPos cell : room.shape().offsets()) {
                BlockPos cellOrigin = origin.offset(
                        (room.anchor().x() + cell.x()) * roomSize, 0,
                        (room.anchor().y() + cell.y()) * roomSize);
                int lx = pos.getX() - cellOrigin.getX();
                int ly = pos.getY() - cellOrigin.getY();
                int lz = pos.getZ() - cellOrigin.getZ();
                if (lx < 0 || lz < 0 || lx >= roomSize || lz >= roomSize) {
                    continue;
                }
                if (es.boffmedia.teras.dungeon.model.DoorwayZone.contains(cell, room.shape(),
                        lx, ly, lz, roomSize,
                        DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight())) {
                    return true;
                }
            }
            return false;
        }

        private void carveDoors() {
            for (DoorEdge door : layout.doors()) {
                switch (door.kind()) {
                    case OPEN, BOSS -> DoorCarver.fillDoorway(level, origin, door,
                            Blocks.AIR.defaultBlockState(), roomSize,
                            DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
                    case SECRET_CRACK -> DoorCarver.fillDoorway(level, origin, door,
                            crackState(), roomSize,
                            DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
                    // Barred rather than walled: both satellite doors of the sala del sello stand
                    // visible from inside it, and the boss falling is what opens them. The Orden's
                    // is only ever built on a floor that earned her, so a barred GRACIA door is
                    // always a door that will open.
                    case DEVIL, GRACIA -> DoorCarver.fillDoorway(level, origin, door,
                            sealState(), roomSize,
                            DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
                    // Open, then fanged. The curse room charges blood to enter, and a price you
                    // cannot see before you pay it is an ambush; the spikes are the warning, which
                    // is the whole reason this is a door kind and not a rule hidden in the run loop.
                    case CURSE -> {
                        DoorCarver.fillDoorway(level, origin, door,
                                Blocks.AIR.defaultBlockState(), roomSize,
                                DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
                        DoorCarver.fillDoorwayRow(level, origin, door, DoorCarver.spikeState(),
                                roomSize, DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
                    }
                    // Both stay untouched wall: the super-secret must not exist as far as anyone
                    // can see, and the sala del sello is revealed by the boss's death — the seal
                    // re-pins and the run engine carves the rock open, like a secret giving way.
                    case HIDDEN, SELLO -> { }
                }
            }
        }

        /** The dedicated cracked wall, falling back to vanilla if the config names an unknown block. */
        private static BlockState crackState() {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(net.minecraft.resources.ResourceLocation.parse(DungeonsConfig.crackBlock()));
            return block == null || block == Blocks.AIR
                    ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : block.defaultBlockState();
        }

        private static BlockState sealState() {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(net.minecraft.resources.ResourceLocation.parse(DungeonsConfig.sealBlock()));
            return block == null ? Blocks.IRON_BARS.defaultBlockState() : block.defaultBlockState();
        }
    }

    private static final class DiscardJob implements Job {
        private final ServerLevel level;
        private final Runnable onComplete;
        private final ArrayDeque<BlockPos> cellOrigins = new ArrayDeque<>();
        private final int roomSize;
        private final int roomHeight;

        DiscardJob(ServerLevel level, List<BlockPos> cells, int roomSize, int roomHeight,
                   Runnable onComplete) {
            this.level = level;
            this.onComplete = onComplete;
            this.roomSize = roomSize;
            this.roomHeight = roomHeight;
            this.cellOrigins.addAll(cells);
        }

        @Override
        public boolean step() {
            BlockPos cell = cellOrigins.poll();
            if (cell == null) {
                onComplete.run();
                return true;
            }
            // Per cell, so one bad box cannot abandon the rest of the floor. The queue drops a job
            // that throws, and a discard that dies at its third cell out of forty used to leave the
            // other thirty-seven standing — with the pedestals and shop displays inside them.
            try {
                // Entities before blocks: a lingering mob — or a hidden CustomNPCs corpse waiting to
                // respawn — must go with the floor it stood in, or it turns up inside the next one.
                // Loaded, because the pad this is clearing is one the party has already left.
                sweepLoadedEntities(level, cell, roomSize, roomHeight);
                for (int x = 0; x < roomSize; x++) {
                    for (int y = 0; y < roomHeight; y++) {
                        for (int z = 0; z < roomSize; z++) {
                            level.setBlock(cell.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            } catch (Throwable t) {
                Teras.LOGGER.error("Dungeons: could not clear cell {}; continuing with the rest of "
                        + "the floor", cell, t);
            }
            return false;
        }
    }

    /**
     * Discards every entity in a box of {@code span}×{@code span} at {@code origin} — except
     * players, and except pets someone owns (a sent-out Pokémon is the player's, and its engine
     * handles recall). Everything else standing in a dungeon volume belongs to the floor and
     * leaves with it. The vertical slack matches {@code InstanceGuard}'s reasoning: a mob nudged
     * on top of the roof is still the floor's problem.
     */
    private static void sweepEntities(ServerLevel level, BlockPos origin, int span, int height) {
        AABB box = new AABB(origin.getX(), origin.getY() - 8, origin.getZ(),
                origin.getX() + span, origin.getY() + height + 8, origin.getZ() + span);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            if (entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
                continue;
            }
            entity.discard();
        }
    }

    /**
     * The same sweep, but over chunks it has made sure are loaded first.
     *
     * <p><b>This is the fix for shop displays turning up inside a later floor.</b>
     * {@code getEntities} only ever sees loaded chunks, and every sweep in this class used to run
     * <i>before</i> the block writes that load them: the discard job swept a cell and then cleared
     * it, and the build job swept the whole footprint on its first tick. On a pad the party had
     * already left, those chunks were unloaded, so the sweep found nothing, the blocks were then
     * cleared around entities that were never removed, and the next floor built on that pad pasted
     * its rooms around a floating shop pedestal that no longer belonged to anything.</p>
     *
     * <p>Nothing said a word, because from the sweep's point of view it succeeded — it discarded
     * every entity it could see.</p>
     *
     * <p>Only used on cell-sized boxes. Touching every chunk of a whole 13×13-cell footprint would
     * be some 289 forced loads in one tick, which is why the broad pass stays best-effort and the
     * per-cell passes are the ones made exact.</p>
     */
    private static void sweepLoadedEntities(ServerLevel level, BlockPos origin, int span,
                                            int height) {
        int minChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int maxChunkX = SectionPos.blockToSectionCoord(origin.getX() + span);
        int minChunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(origin.getZ() + span);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
        sweepEntities(level, origin, span, height);
    }
}
