package es.boffmedia.teras.dungeon.editor;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.build.RoomTemplates;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.run.RunEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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

import java.util.ArrayList;
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
 * the limits: a bedrock apron underneath, orange glass marking where {@code DoorCarver} will cut
 * each doorway, and red glass filling any cell an L-shape does not own. Edits outside the room's
 * box are refused at the block events, so a saved room can never bleed into a neighboring cell.
 *
 * <p>Saving goes through the world's {@code generated} structure folder — the same override path
 * DUNGEONS.md §5 already designates — so {@code guardar} with no name replaces the template the
 * dungeon builds from (in memory immediately: the manager's cached instance is the one filled), and
 * {@code guardar <nombre>} writes a new template and registers it as a weighted variant in
 * {@code rooms.json}.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RoomEditor {
    private RoomEditor() {}

    /** Marker kinds {@code TemplateMarkers} understands — what {@code sala marcar} accepts. */
    private static final Set<String> MARKER_KINDS =
            Set.of("spawn", "loot", "boss", "trapdoor", "shopslot", "door", "challenge");

    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();

    private static final class Session {
        final String theme;
        final String poolKey;
        final RoomShape shape;
        final ResourceLocation templateId;
        final BlockPos origin;
        final int pad;
        final DungeonRun.ReturnPoint back;

        Session(String theme, String poolKey, RoomShape shape, ResourceLocation templateId,
                BlockPos origin, int pad, DungeonRun.ReturnPoint back) {
            this.theme = theme;
            this.poolKey = poolKey;
            this.shape = shape;
            this.templateId = templateId;
            this.origin = origin;
            this.pad = pad;
            this.back = back;
        }

        int spanX() {
            return (maxOffset(GridPos::x) + 1) * DungeonsConfig.roomSize();
        }

        int spanZ() {
            return (maxOffset(GridPos::y) + 1) * DungeonsConfig.roomSize();
        }

        private int maxOffset(java.util.function.ToIntFunction<GridPos> axis) {
            return shape.offsets().stream().mapToInt(axis).max().orElse(0);
        }

        /** Whether a world position is inside a cell this room actually owns. */
        boolean owns(BlockPos pos) {
            int rx = pos.getX() - origin.getX();
            int ry = pos.getY() - origin.getY();
            int rz = pos.getZ() - origin.getZ();
            if (rx < 0 || rz < 0 || rx >= spanX() || rz >= spanZ()
                    || ry < 0 || ry >= DungeonsConfig.roomHeight()) {
                return false;
            }
            int size = DungeonsConfig.roomSize();
            GridPos cell = new GridPos(rx / size, rz / size);
            return shape.offsets().contains(cell);
        }
    }

    // --- session lifecycle ---------------------------------------------------------------------

    /** Starts an editing session; the returned string is an error to show, or null on success. */
    public static String start(ServerPlayer player, String poolKey, int variantIndex) {
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
        String theme = DungeonsConfig.theme();
        List<RoomTemplates.TemplateEntry> pool = RoomTemplates.pool(theme, poolKey);
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

        Session session = new Session(theme, poolKey, shapeForKey(poolKey), entry.template(),
                editorOrigin(pad), pad, returnPointOf(player));
        clearPad(level, session);
        pasteTemplate(level, session);
        buildShell(level, session);
        SESSIONS.put(player.getUUID(), session);

        BlockPos centre = session.origin.offset(session.spanX() / 2, 1, session.spanZ() / 2);
        player.teleportTo(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        RunEngine.land(player);
        player.sendSystemMessage(Component.literal(
                "§aEditando " + poolKey + " (" + entry.template() + ")."));
        if (entry.rotation() != Rotation.NONE) {
            player.sendSystemMessage(Component.literal(
                    "§eEsta variante es una rotación de otra plantilla; aquí se edita la "
                            + "plantilla base, sin rotar."));
        }
        player.sendSystemMessage(Component.literal(
                "§7El cristal naranja marca dónde se abrirán las puertas. 'sala marcar' coloca "
                        + "marcadores, 'sala guardar [nombre]' guarda, 'sala salir' descarta."));
        return null;
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
                    "dungeon/" + session.theme + "/" + clean);
        }

        ServerLevel level = player.serverLevel();
        StructureTemplateManager manager = level.getServer().getStructureManager();
        StructureTemplate template = manager.getOrCreate(id);
        template.fillFromWorld(level, session.origin,
                new Vec3i(session.spanX(), DungeonsConfig.roomHeight(), session.spanZ()),
                false, Blocks.STRUCTURE_VOID);
        template.setAuthor(player.getGameProfile().getName());
        stripUnownedCells(level, template, session);
        if (!manager.save(id)) {
            return "No se pudo escribir la plantilla " + id + " en la carpeta 'generated'.";
        }

        if (name != null && !RoomTemplates.addTemplate(session.theme, session.poolKey, id, 1)) {
            return "Plantilla guardada como " + id + " pero no se pudo registrar en rooms.json.";
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

    private static void pasteTemplate(ServerLevel level, Session session) {
        StructureTemplate template =
                level.getServer().getStructureManager().get(session.templateId).orElse(null);
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

    private static void buildShell(ServerLevel level, Session session) {
        BlockPos origin = session.origin;
        int spanX = session.spanX();
        int spanZ = session.spanZ();
        int size = DungeonsConfig.roomSize();
        int doorWidth = DungeonsConfig.doorWidth();
        int doorHeight = DungeonsConfig.doorHeight();
        int inset = (size - doorWidth) / 2;

        // Bedrock apron one below the floor, one block proud on every side: breaking through the
        // room's own floor must never drop the editor into the void.
        for (int x = -1; x <= spanX; x++) {
            for (int z = -1; z <= spanZ; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }

        for (GridPos cell : session.shape.offsets()) {
            int baseX = cell.x() * size;
            int baseZ = cell.y() * size;
            // Doorway hints on every exterior side of every owned cell, one block outside the
            // wall — outside the capture box, so they can never end up inside a saved template.
            if (!session.shape.offsets().contains(new GridPos(cell.x(), cell.y() - 1))) {
                hintColumns(level, origin, baseX + inset, baseZ - 1, doorWidth, doorHeight, true);
            }
            if (!session.shape.offsets().contains(new GridPos(cell.x(), cell.y() + 1))) {
                hintColumns(level, origin, baseX + inset, baseZ + size, doorWidth, doorHeight, true);
            }
            if (!session.shape.offsets().contains(new GridPos(cell.x() - 1, cell.y()))) {
                hintColumns(level, origin, baseX - 1, baseZ + inset, doorWidth, doorHeight, false);
            }
            if (!session.shape.offsets().contains(new GridPos(cell.x() + 1, cell.y()))) {
                hintColumns(level, origin, baseX + size, baseZ + inset, doorWidth, doorHeight, false);
            }
        }

        // L-shapes: the quadrant the room does not own gets a red glass floor. It sits inside the
        // capture box but stripUnownedCells drops it from every save, and the bounds guard refuses
        // edits there — the color is the explanation.
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

    /** A doorway-sized panel of orange glass, {@code alongX} choosing which axis the width runs. */
    private static void hintColumns(ServerLevel level, BlockPos origin, int x, int z,
                                    int doorWidth, int doorHeight, boolean alongX) {
        for (int w = 0; w < doorWidth; w++) {
            for (int h = 1; h <= doorHeight; h++) {
                BlockPos pos = alongX
                        ? origin.offset(x + w, h, z)
                        : origin.offset(x, h, z + w);
                level.setBlock(pos, Blocks.ORANGE_STAINED_GLASS.defaultBlockState(), 2);
            }
        }
    }

    /** Air-fills the pad (shell included) and discards anything standing on it. */
    private static void clearPad(ServerLevel level, Session session) {
        BlockPos origin = session.origin;
        int spanX = session.spanX();
        int spanZ = session.spanZ();
        int height = DungeonsConfig.roomHeight();
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
     * Drops every captured block that lies in a cell the shape does not own. Without this, saving
     * an L room would capture air across its empty quadrant and the materializer would later paste
     * that air into whatever room legitimately occupies the cell — the shipped L templates omit
     * those entries for exactly this reason, and saved ones must too.
     */
    private static void stripUnownedCells(ServerLevel level, StructureTemplate template,
                                          Session session) {
        if (session.shape.cellCount() == (session.spanX() / DungeonsConfig.roomSize())
                * (session.spanZ() / DungeonsConfig.roomSize())) {
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
        for (String required : requiredMarkers(session.poolKey)) {
            if (!byKind.containsKey(required)) {
                player.sendSystemMessage(Component.literal(
                        "§eAviso: una sala '" + session.poolKey + "' debería tener un marcador '"
                                + required + "' — sin él se usa una posición calculada."));
            }
        }
    }

    private static List<String> requiredMarkers(String poolKey) {
        if (poolKey.startsWith("boss")) {
            return List.of("boss", "trapdoor");
        }
        return switch (poolKey) {
            case "mini_boss" -> List.of("boss");
            case "treasure" -> List.of("loot");
            case "shop" -> List.of("shopslot");
            case "challenge", "normal", "normal_horizontal", "normal_vertical", "normal_quad",
                 "normal_l_top_left", "normal_l_top_right",
                 "normal_l_bottom_left", "normal_l_bottom_right" -> List.of("spawn");
            default -> List.of();
        };
    }

    // --- small helpers --------------------------------------------------------------------------

    /** The grid footprint a pool key's rooms occupy, read off the key's shape suffix. */
    static RoomShape shapeForKey(String poolKey) {
        for (RoomShape shape : RoomShape.values()) {
            if (poolKey.endsWith("_" + shape.name().toLowerCase(Locale.ROOT))) {
                return shape;
            }
        }
        return RoomShape.SINGLE;
    }

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
