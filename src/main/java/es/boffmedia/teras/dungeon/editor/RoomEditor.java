package es.boffmedia.teras.dungeon.editor;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.build.RoomTemplates;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.model.DoorwayZone;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.run.RunEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The in-place room editor: {@code /teras dungeon sala editar <tipo>} teleports an admin to a
 * reserved pad in the dungeon dimension with the room's current template pasted there — markers
 * included, since unlike the materializer nothing airs them out — and a shell around it that shows
 * the limits: a bedrock apron underneath, a flame outline over every volume {@code DoorCarver}
 * reserves for a doorway, and red glass filling any cell an L-shape does not own. Edits outside the
 * room's box are refused at the block events, so a saved room can never bleed into a neighboring
 * cell.
 *
 * <p>Saving goes through the world's {@code generated} structure folder — the same override path
 * DUNGEONS.md §5 already designates — so {@code guardar} with no name replaces the template the
 * dungeon builds from (in memory immediately: the manager's cached instance is the one filled), and
 * {@code guardar <nombre>} writes a new template and registers it as a weighted variant on
 * the piso being edited.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class RoomEditor {
    private RoomEditor() {}

    /** Marker kinds {@code TemplateMarkers} understands — what {@code sala marcar} accepts. */
    private static final Set<String> MARKER_KINDS =
            Set.of("spawn", "loot", "boss", "trapdoor", "shopslot", "door", "challenge",
                    "sacrifice", "arcade", "deal", "nido", "ambiente", "decoracion");

    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();

    /**
     * How far past the editable box a pad is swept, so a template taller than this one's does not
     * linger overhead. Anything above the box is only ever visual — {@code fillFromWorld} reads the
     * box alone — but a floating slab of someone else's room reads as a bug.
     */
    private static final int PAD_HEADROOM = 8;

    /**
     * The largest footprint any {@link RoomShape} has, in cells. Pads are swept to this rather than
     * to the room being opened: they are handed out by index and reused, so opening a 2×2 and later
     * a 1×1 on the same pad would leave three quarters of the old room standing beside the new one —
     * inside the next capture, since a room's box is the grid, not what was pasted into it.
     */
    private static final int PAD_CELLS = 2;

    /** How often the hint outlines are redrawn. Flame particles outlive this, so it reads as steady. */
    private static final int HINT_INTERVAL_TICKS = 20;

    private static final class Session {
        final String pisoId;
        final String poolKey;
        final RoomShape shape;
        final ResourceLocation templateId;
        final BlockPos origin;
        final int pad;
        final DungeonRun.ReturnPoint back;
        /**
         * The editable box, fixed when the session opens rather than read from the config on every
         * query. Two reasons it is not simply the configured cell: a config edited mid-session would
         * move the walls under the admin's feet, and a template whose geometry disagrees with the
         * config has to stay fully editable — see {@link RoomEditor#boxOf}.
         */
        final int spanX;
        final int spanZ;
        final int height;

        Session(String pisoId, String poolKey, RoomShape shape, ResourceLocation templateId,
                BlockPos origin, int pad, DungeonRun.ReturnPoint back,
                int spanX, int spanZ, int height) {
            this.pisoId = pisoId;
            this.poolKey = poolKey;
            this.shape = shape;
            this.templateId = templateId;
            this.origin = origin;
            this.pad = pad;
            this.back = back;
            this.spanX = spanX;
            this.spanZ = spanZ;
            this.height = height;
        }

        int spanX() {
            return spanX;
        }

        int spanZ() {
            return spanZ;
        }

        int height() {
            return height;
        }

        /** The room's first owned cell — where an admin is put down, and never an L's empty hole. */
        GridPos firstCell() {
            return shape.offsets().get(0);
        }

        /**
         * Whether a position sits in a doorway volume — inside the flame outline. Nothing may be
         * marked there: the layout decides which sides get doors, so all four are reserved.
         */
        boolean inDoorway(BlockPos pos) {
            int size = DungeonsConfig.roomSize();
            int rx = pos.getX() - origin.getX();
            int ry = pos.getY() - origin.getY();
            int rz = pos.getZ() - origin.getZ();
            GridPos cell = new GridPos(rx / size, rz / size);
            return DoorwayZone.contains(cell, shape, rx % size, ry, rz % size, size,
                    DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
        }

        /** Whether a world position is inside a cell this room actually owns. */
        boolean owns(BlockPos pos) {
            int rx = pos.getX() - origin.getX();
            int ry = pos.getY() - origin.getY();
            int rz = pos.getZ() - origin.getZ();
            if (rx < 0 || rz < 0 || rx >= spanX || rz >= spanZ || ry < 0 || ry >= height) {
                return false;
            }
            int size = DungeonsConfig.roomSize();
            GridPos cell = new GridPos(rx / size, rz / size);
            return shape.offsets().contains(cell);
        }
    }

    /** The editable box of a session: {@code [spanX, height, spanZ]}. */
    private record Box(int spanX, int height, int spanZ) {}

    /**
     * The box an editing session opens with: the configured cell, widened to whatever the template
     * on disk actually measures.
     *
     * <p>Taking the maximum rather than the config alone is what keeps the editor from destroying
     * work. The two numbers drift independently — {@code config.yml} is only written when it does
     * not exist, so a file from before {@code alturaSala} changed keeps the old value forever, and a
     * template in the world's {@code generated} folder shadows the jar with its own geometry. Trust
     * the config and a 12-block-tall room opens as an 8-block box: the top four layers are visible,
     * refuse every edit, and are <b>dropped from the capture on the next save</b>. One save and the
     * room is permanently four blocks shorter, with nothing on screen to say why.</p>
     *
     * <p>Widening is safe in the other direction too: a template authored smaller than the current
     * cell opens at the cell's full size, so growing {@code alturaSala} and re-saving is how a room
     * is brought up to it.</p>
     */
    private static Box boxOf(RoomShape shape, StructureTemplate template) {
        int size = DungeonsConfig.roomSize();
        int spanX = shape.cellsWide() * size;
        int spanZ = shape.cellsDeep() * size;
        int height = DungeonsConfig.roomHeight();
        if (template == null) {
            return new Box(spanX, height, spanZ);
        }
        Vec3i actual = template.getSize();
        return new Box(Math.max(spanX, actual.getX()),
                Math.max(height, actual.getY()),
                Math.max(spanZ, actual.getZ()));
    }

    // --- session lifecycle ---------------------------------------------------------------------

    /**
     * Starts an editing session; the returned string is an error to show, or null on success.
     *
     * <p>{@code pisoArg} is what makes a second place authorable at all: without it the editor
     * could only ever open whichever piso came first, so a new one could never be given its rooms.
     * A piso whose templates are still missing is deliberately editable — that is the state a piso
     * is in right after {@code piso crear}.</p>
     */
    public static String start(ServerPlayer player, String poolKey, int variantIndex,
                               String pisoArg) {
        if (SESSIONS.containsKey(player.getUUID())) {
            return "Ya estás editando una sala — usa 'sala guardar' o 'sala salir' primero.";
        }
        if (DungeonRunManager.runOf(player.getUUID()) != null) {
            return "No puedes editar salas dentro de una mazmorra.";
        }
        if (!RoomTemplates.knownPoolKeys().contains(poolKey)) {
            return "Tipo de sala desconocido: " + poolKey;
        }
        ServerLevel level = dungeonLevel(player);
        if (level == null) {
            return "La dimensión " + DungeonsConfig.dimension() + " no existe.";
        }
        String pisoId = pisoArg == null || pisoArg.isBlank()
                ? es.boffmedia.teras.dungeon.piso.PisoCatalog.defaultPisoId() : pisoArg;
        // declaredPiso, not piso: a piso missing its rooms is exactly the one someone needs to
        // open in order to author them.
        es.boffmedia.teras.dungeon.piso.FloorDef piso =
                es.boffmedia.teras.dungeon.piso.PisoCatalog.declaredPiso(pisoId);
        if (piso == null) {
            return "No existe el piso '" + pisoId + "'.";
        }
        List<RoomTemplates.TemplateEntry> pool = RoomTemplates.pool(piso, poolKey);
        if (pool.isEmpty()) {
            return "El tipo " + poolKey + " no tiene plantillas.";
        }
        if (variantIndex < 0 || variantIndex >= pool.size()) {
            return "Variante fuera de rango: el tipo tiene " + pool.size() + " (0.."
                    + (pool.size() - 1) + ").";
        }
        RoomTemplates.TemplateEntry entry = pool.get(variantIndex);

        int pad = 0;
        Set<Integer> used = new LinkedHashSet<>();
        for (Session other : SESSIONS.values()) {
            used.add(other.pad);
        }
        while (used.contains(pad)) {
            pad++;
        }

        // The template is resolved before the session exists: its real geometry is half of what
        // decides the box, and a room that opens smaller than the template standing in it loses the
        // difference on the next save.
        StructureTemplate template =
                level.getServer().getStructureManager().get(entry.template()).orElse(null);
        RoomShape shape = es.boffmedia.teras.dungeon.piso.RoomKeys.shapeFor(poolKey);
        Box box = boxOf(shape, template);

        Session session = new Session(pisoId, poolKey, shape, entry.template(),
                editorOrigin(pad), pad, returnPointOf(player),
                box.spanX(), box.spanZ(), box.height());
        clearPad(level, session);
        pasteTemplate(level, session, template);
        buildShell(level, session);
        SESSIONS.put(player.getUUID(), session);

        // The middle of the first owned cell, not the middle of the bounding box — on an L the box
        // centre is the corner of the quadrant the room does not own, so an admin opening an L room
        // was dropped onto the red glass outside it.
        int size = DungeonsConfig.roomSize();
        BlockPos centre = session.origin.offset(
                session.firstCell().x() * size + size / 2, 1,
                session.firstCell().y() * size + size / 2);
        player.teleportTo(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        RunEngine.land(player);
        player.sendSystemMessage(Component.literal(
                "§aEditando " + poolKey + " (" + entry.template() + ") — piso §f" + pisoId + "§a."));
        player.sendSystemMessage(Component.literal("§7Caja editable: §f" + session.spanX() + "×"
                + session.height() + "×" + session.spanZ() + "§7 (" + shape.name().toLowerCase(
                        Locale.ROOT) + ")."));
        if (template == null) {
            player.sendSystemMessage(Component.literal("§eLa plantilla " + entry.template()
                    + " no existe todavía — estás construyendo la sala desde cero."));
        } else {
            reportGeometry(player, session, template);
        }
        if (entry.rotation() != Rotation.NONE) {
            player.sendSystemMessage(Component.literal(
                    "§eEsta variante es una rotación de otra plantilla; aquí se edita la "
                            + "plantilla base, sin rotar."));
        }
        player.sendSystemMessage(Component.literal(
                "§7Las llamas marcan el espacio que debe quedar libre para las puertas. "
                        + "'sala marcar' coloca "
                        + "marcadores, 'sala guardar [nombre]' guarda, 'sala salir' descarta."));
        return null;
    }

    /**
     * Says out loud when the template on disk and {@code config.yml} disagree about the cell.
     *
     * <p>Silence here is what made the mismatch so hard to see: the room simply opened at the wrong
     * size, and the number to change lives in a file the admin is not looking at. The session stays
     * open at the larger box either way — this is the explanation, not a refusal.</p>
     */
    private static void reportGeometry(ServerPlayer player, Session session,
                                       StructureTemplate template) {
        Vec3i actual = template.getSize();
        if (actual.getX() == session.spanX() && actual.getY() == session.height()
                && actual.getZ() == session.spanZ()) {
            return;
        }
        player.sendSystemMessage(Component.literal("§ePlantilla de " + actual.getX() + "×"
                + actual.getY() + "×" + actual.getZ() + ", pero config.yml dice tamanoSala "
                + DungeonsConfig.roomSize() + " y alturaSala " + DungeonsConfig.roomHeight()
                + "§e."));
        player.sendSystemMessage(Component.literal(
                "§7Se edita con la caja más grande de las dos, para no recortar nada al guardar. "
                        + "Ajusta config.yml o vuelve a guardar la sala para igualarlas."));
        // A room wider than its cells is the one mismatch that breaks other rooms rather than just
        // this one: the materializer pastes on the grid pitch, so the excess lands inside whatever
        // room occupies the next cell.
        int size = DungeonsConfig.roomSize();
        if (actual.getX() > session.shape.cellsWide() * size
                || actual.getZ() > session.shape.cellsDeep() * size) {
            player.sendSystemMessage(Component.literal("§cEsta sala es más ancha que sus celdas: "
                    + "al construir el piso sobrescribirá la sala de al lado. Sube tamanoSala o "
                    + "recorta la sala antes de guardarla."));
        }
    }

    /** Places a DATA structure block with {@code metadata} at the player's feet. */
    public static String placeMarker(ServerPlayer player, String metadata) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return "No estás editando ninguna sala.";
        }
        String normalized = metadata.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        String kind = colon < 0 ? normalized : normalized.substring(0, colon);
        if (!MARKER_KINDS.contains(kind)) {
            return "Marcador desconocido '" + kind + "'. Válidos: " + String.join(", ", MARKER_KINDS);
        }
        BlockPos pos = player.blockPosition();
        if (!session.owns(pos)) {
            return "Estás fuera de los límites de la sala.";
        }
        if (session.inDoorway(pos)) {
            return "Ahí se abrirá una puerta (la zona marcada con llamas). Un marcador en una "
                    + "entrada la "
                    + "bloquea o deja al enemigo dentro del muro — muévete unos bloques adentro.";
        }
        ServerLevel level = player.serverLevel();
        level.setBlock(pos, Blocks.STRUCTURE_BLOCK.defaultBlockState()
                .setValue(StructureBlock.MODE, StructureMode.DATA), 3);
        if (level.getBlockEntity(pos) instanceof StructureBlockEntity be) {
            be.setMode(StructureMode.DATA);
            be.setMetaData(normalized);
            be.setChanged();
        }
        player.sendSystemMessage(Component.literal("§aMarcador '" + normalized + "' colocado."));
        return null;
    }

    /**
     * Captures the room and saves it — over the source template when {@code name} is null,
     * as a new registered variant otherwise. The session stays open either way, so an admin can
     * keep iterating or save several variants from one visit.
     */
    public static String save(ServerPlayer player, String name) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return "No estás editando ninguna sala.";
        }
        ResourceLocation id;
        if (name == null) {
            id = session.templateId;
        } else {
            String clean = name.trim().toLowerCase(Locale.ROOT);
            if (!clean.matches("[a-z0-9_]+")) {
                return "Nombre inválido '" + name + "' — solo minúsculas, dígitos y '_'.";
            }
            id = ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID,
                    "dungeon/" + session.pisoId + "/" + clean);
        }

        ServerLevel level = player.serverLevel();
        StructureTemplateManager manager = level.getServer().getStructureManager();
        StructureTemplate template = manager.getOrCreate(id);
        // With entities, matching the paste: pasteTemplate places them, so capturing without them
        // meant every save quietly emptied a room of whatever it was authored with.
        template.fillFromWorld(level, session.origin,
                new Vec3i(session.spanX(), session.height(), session.spanZ()),
                true, Blocks.STRUCTURE_VOID);
        template.setAuthor(player.getGameProfile().getName());
        stripUnownedCells(level, template, session);
        if (!manager.save(id)) {
            return "No se pudo escribir la plantilla " + id + " en la carpeta 'generated'.";
        }

        if (name != null) {
            String error = es.boffmedia.teras.dungeon.piso.PisoCatalog.addVariant(
                    session.pisoId, session.poolKey, id.toString(), 1);
            if (error != null) {
                return "Plantilla guardada como " + id + " pero no se registró: " + error;
            }
        }
        player.sendSystemMessage(Component.literal("§aSala guardada como " + id
                + (name == null ? " (reemplaza a la original)."
                        : " y registrada como variante de " + session.poolKey + ".")));
        reportMarkers(player, session, template);
        return null;
    }

    /** Ends the session: sweeps the pad and sends the admin back where they came from. */
    public static String exit(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return "No estás editando ninguna sala.";
        }
        ServerLevel level = dungeonLevel(player);
        if (level != null) {
            clearPad(level, session);
        }
        teleportBack(player, session.back);
        player.sendSystemMessage(Component.literal("§7Edición terminada."));
        return null;
    }

    // --- bounds enforcement ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (refused(event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && refused(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Only session holders are constrained, and only inside the dungeon dimension. */
    private static boolean refused(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Session session = SESSIONS.get(serverPlayer.getUUID());
        if (session == null || !inDungeonDimension(serverPlayer.serverLevel())) {
            return false;
        }
        if (session.owns(pos)) {
            return false;
        }
        serverPlayer.displayClientMessage(
                Component.literal("§cFuera de los límites de la sala."), true);
        return true;
    }

    /** A logout mid-edit ends the session; the login safety net will put the player somewhere sane. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            ServerLevel level = dungeonLevel(player);
            if (level != null) {
                clearPad(level, session);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
    }

    // --- pad construction -----------------------------------------------------------------------

    /** Editor pads come from the void's zone map, on their own strip north of the run lattice. */
    private static BlockPos editorOrigin(int pad) {
        return es.boffmedia.teras.world.VoidZones.roomEditorPad(pad);
    }

    private static void pasteTemplate(ServerLevel level, Session session,
                                      StructureTemplate template) {
        if (template == null) {
            Teras.LOGGER.warn("Dungeons: template {} missing — editing an empty pad",
                    session.templateId);
            return;
        }
        // Rotation NONE always: guardar writes the raw template back, so what stands here must be
        // the raw orientation. Markers are deliberately left standing, unlike the materializer —
        // seeing and moving them is half the point of the editor.
        template.placeInWorld(level, session.origin, session.origin,
                new StructurePlaceSettings().setRotation(Rotation.NONE).setIgnoreEntities(false),
                level.getRandom(), 2);
    }

    /**
     * The parts of the shell that are blocks. The doorway zones and the ceiling limit are not —
     * they are drawn as particles by {@link #onServerTick}, so nothing that is only a hint can be
     * broken, built over, or captured into a template.
     */
    private static void buildShell(ServerLevel level, Session session) {
        BlockPos origin = session.origin;
        int spanX = session.spanX();
        int spanZ = session.spanZ();
        int size = DungeonsConfig.roomSize();

        // Bedrock apron one below the floor, one block proud on every side: breaking through the
        // room's own floor must never drop the editor into the void.
        for (int x = -1; x <= spanX; x++) {
            for (int z = -1; z <= spanZ; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }

        // L-shapes: the quadrant the room does not own gets a red glass floor. Unlike the doorway
        // zones this one stays a block — it marks a region that genuinely is not part of the room,
        // and a floor you cannot stand on reads better than an outline you can walk through. It sits
        // inside the capture box, but stripUnownedCells drops it from every save and the bounds
        // guard refuses edits there.
        for (int cx = 0; cx <= (spanX / size) - 1; cx++) {
            for (int cz = 0; cz <= (spanZ / size) - 1; cz++) {
                if (session.shape.offsets().contains(new GridPos(cx, cz))) {
                    continue;
                }
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        level.setBlock(origin.offset(cx * size + x, 0, cz * size + z),
                                Blocks.RED_STAINED_GLASS.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // --- hints ----------------------------------------------------------------------------------

    /**
     * Redraws every open session's hints, once a second, for the admin editing it.
     *
     * <p>These used to be glass. Glass is a block: it stood in the room being authored, had to be
     * placed and swept, could be broken or built over, and on an L two of the panels landed inside
     * the capture box — kept out of saves only because {@code stripUnownedCells} happened to cover
     * them. It also showed the three-block opening rather than the volume that must stay clear,
     * which is the part that actually matters: {@link DoorwayZone#DEPTH} blocks inward, not one.</p>
     *
     * <p>Particles are nothing at all — no block state, no cleanup, and no way for a hint to reach a
     * template. Drawn only for the session's own player, so two admins on neighbouring pads do not
     * see each other's outlines.</p>
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty() || event.getServer().getTickCount() % HINT_INTERVAL_TICKS != 0) {
            return;
        }
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && inDungeonDimension(player.serverLevel())) {
                drawHints(player, entry.getValue());
            }
        }
    }

    private static void drawHints(ServerPlayer player, Session session) {
        int size = DungeonsConfig.roomSize();
        BlockPos origin = session.origin;
        // The reserved doorway volumes, straight from the geometry the bounds guard and the
        // materializer both use — so what is drawn is exactly what is refused.
        for (GridPos cell : session.shape.offsets()) {
            for (DoorwayZone.Zone zone : DoorwayZone.zonesOf(cell, session.shape, size,
                    DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight())) {
                outline(player, ParticleTypes.FLAME,
                        origin.getX() + cell.x() * size + zone.minX(),
                        origin.getY() + zone.minY(),
                        origin.getZ() + cell.y() * size + zone.minZ(),
                        origin.getX() + cell.x() * size + zone.maxX() + 1,
                        origin.getY() + zone.maxY() + 1,
                        origin.getZ() + cell.y() * size + zone.maxZ() + 1);
            }
        }
        // The ceiling limit. Without it the vertical bound is invisible — the box simply stops
        // accepting blocks at some height with nothing on screen to say where, which reads as the
        // editor being broken rather than as a limit.
        outline(player, ParticleTypes.SOUL_FIRE_FLAME,
                origin.getX(), origin.getY() + session.height(), origin.getZ(),
                origin.getX() + session.spanX(), origin.getY() + session.height(),
                origin.getZ() + session.spanZ());
    }

    /**
     * The twelve edges of a box, a particle per block along each. A box that is flat on an axis
     * collapses to its four remaining edges rather than drawing each of them twice.
     */
    private static void outline(ServerPlayer player, ParticleOptions type,
                                int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int[] xs = ends(minX, maxX);
        int[] ys = ends(minY, maxY);
        int[] zs = ends(minZ, maxZ);
        for (int y : ys) {
            for (int z : zs) {
                for (int x = minX; x <= maxX; x++) {
                    particle(player, type, x, y, z);
                }
            }
        }
        for (int x : xs) {
            for (int z : zs) {
                for (int y = minY; y <= maxY; y++) {
                    particle(player, type, x, y, z);
                }
            }
        }
        for (int x : xs) {
            for (int y : ys) {
                for (int z = minZ; z <= maxZ; z++) {
                    particle(player, type, x, y, z);
                }
            }
        }
    }

    private static int[] ends(int min, int max) {
        return min == max ? new int[] {min} : new int[] {min, max};
    }

    private static void particle(ServerPlayer player, ParticleOptions type,
                                 double x, double y, double z) {
        // Count zero is the precise form: the offsets become a velocity rather than a scatter, so
        // all-zero puts exactly one stationary particle on the corner. An outline that drifts is not
        // an outline.
        player.serverLevel().sendParticles(player, type, true, x, y, z, 0, 0, 0, 0, 0);
    }

    /**
     * Air-fills the pad (shell included) and discards anything standing on it — swept to the whole
     * reservation rather than to the room being opened, so a reused pad can never hand the next
     * session a piece of the last one. See {@link #PAD_CELLS} and {@link #PAD_HEADROOM}.
     */
    private static void clearPad(ServerLevel level, Session session) {
        int reserved = PAD_CELLS * DungeonsConfig.roomSize();
        int spanX = Math.max(session.spanX(), reserved);
        int spanZ = Math.max(session.spanZ(), reserved);
        int height = session.height() + PAD_HEADROOM;
        BlockPos origin = session.origin;
        AABB box = new AABB(origin.getX() - 1, origin.getY() - 2, origin.getZ() - 1,
                origin.getX() + spanX + 1, origin.getY() + height + 2, origin.getZ() + spanZ + 1);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            if (entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
                continue;
            }
            entity.discard();
        }
        for (int x = -1; x <= spanX; x++) {
            for (int y = -1; y <= height; y++) {
                for (int z = -1; z <= spanZ; z++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    // --- save plumbing --------------------------------------------------------------------------

    /**
     * Drops every captured block and entity that lies in a cell the shape does not own. Without
     * this, saving an L room would capture air across its empty quadrant and the materializer would
     * later paste that air into whatever room legitimately occupies the cell — the shipped L
     * templates omit those entries for exactly this reason, and saved ones must too.
     *
     * <p>The red glass floor over that quadrant sits inside the capture box, so this is also what
     * keeps a save from baking it into the room.</p>
     */
    private static void stripUnownedCells(ServerLevel level, StructureTemplate template,
                                          Session session) {
        if (!session.shape.isLShaped()) {
            return;
        }
        int size = DungeonsConfig.roomSize();
        CompoundTag tag = template.save(new CompoundTag());
        ListTag blocks = tag.getList("blocks", CompoundTag.TAG_COMPOUND);
        ListTag kept = new ListTag();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            ListTag pos = block.getList("pos", CompoundTag.TAG_INT);
            GridPos cell = new GridPos(pos.getInt(0) / size, pos.getInt(2) / size);
            if (session.shape.offsets().contains(cell)) {
                kept.add(block);
            }
        }
        tag.put("blocks", kept);
        // Entities carry a floating-point 'pos' rather than the block list's integer one, and are
        // captured now that the save asks for them; an entity left in the quadrant would be pasted
        // into the neighbouring room exactly like a stray block.
        ListTag entities = tag.getList("entities", CompoundTag.TAG_COMPOUND);
        ListTag keptEntities = new ListTag();
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = entities.getCompound(i);
            ListTag pos = entity.getList("blockPos", CompoundTag.TAG_INT);
            if (pos.size() < 3) {
                continue;
            }
            GridPos cell = new GridPos(pos.getInt(0) / size, pos.getInt(2) / size);
            if (session.shape.offsets().contains(cell)) {
                keptEntities.add(entity);
            }
        }
        tag.put("entities", keptEntities);
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    /** What a finished room of this type is expected to declare, checked on save as a warning. */
    private static void reportMarkers(ServerPlayer player, Session session,
                                      StructureTemplate template) {
        List<TemplateMarkers.Marker> markers = TemplateMarkers.extract(template,
                new StructurePlaceSettings(), BlockPos.ZERO);
        Map<String, Integer> byKind = new LinkedHashMap<>();
        for (TemplateMarkers.Marker marker : markers) {
            byKind.merge(marker.kind(), 1, Integer::sum);
        }
        player.sendSystemMessage(Component.literal("§7Marcadores guardados: "
                + (byKind.isEmpty() ? "ninguno" : byKind.toString())));
        for (String required : es.boffmedia.teras.dungeon.piso.RoomKeys
                .requiredMarkers(session.poolKey)) {
            if (!byKind.containsKey(required)) {
                player.sendSystemMessage(Component.literal(
                        "§eAviso: una sala '" + session.poolKey + "' debería tener un marcador '"
                                + required + "' — sin él se usa una posición calculada."));
            }
        }
    }


    // --- small helpers --------------------------------------------------------------------------

    private static ServerLevel dungeonLevel(ServerPlayer player) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(DungeonsConfig.dimension()));
        return player.getServer().getLevel(key);
    }

    private static boolean inDungeonDimension(ServerLevel level) {
        return level.dimension().location().toString().equals(DungeonsConfig.dimension());
    }

    private static DungeonRun.ReturnPoint returnPointOf(ServerPlayer player) {
        // The editor never changes the admin's game mode; the capture just keeps the record honest.
        return new DungeonRun.ReturnPoint(
                player.serverLevel().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer().getName());
    }

    private static void teleportBack(ServerPlayer player, DungeonRun.ReturnPoint back) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(back.dimension()));
        ServerLevel level = player.getServer().getLevel(key);
        if (level == null) {
            level = player.getServer().overworld();
        }
        player.teleportTo(level, back.x(), back.y(), back.z(), back.yaw(), back.pitch());
        RunEngine.land(player);
    }
}
