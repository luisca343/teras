package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * El ascensor: the shaft head that stands on the back wall of la sala del sello.
 *
 * <h2>Why it is a fixture and not part of the room template</h2>
 *
 * <p>An ascensor belongs on the floor that closes a tramo and <b>nowhere else</b>, and a room
 * template is the one thing in the pipeline that cannot be conditional — every floor drawing
 * {@code exit} draws the same bytes. Authored into {@code exit.nbt} it put a lift on floor 1 of a
 * two-floor dungeon. So {@code exit.nbt} carries a bare {@code ascensor} marker over plain floor,
 * and this pastes the structure at it only where
 * {@link es.boffmedia.teras.dungeon.piso.FloorPlan#tramoBoundary()} says.</p>
 *
 * <p>It is its own {@code .nbt} rather than blocks set from code so it can be edited the way every
 * other piece of dungeon geometry is: authored in {@code tools/author_cuevas_rooms.py --fixtures},
 * openable in a structure block, diffable as bytes. The structure is <b>fully explicit</b>,
 * including its air — it has to clear the floor and the trophy gallery band it lands on, and a
 * template only touches the positions it lists.</p>
 *
 * <h2>What it is for, and what it therefore looks like</h2>
 *
 * <p><b>Nothing ever uses it inside a run.</b> It wakes when the boss falls and the tramo is banked
 * when the party drops through the trampilla; it is never clicked. Its whole job is to be understood
 * at a glance from the grand door, which is why it is placed for the sightline rather than the walk
 * and why it is the only thing in the room that reaches the ceiling — the Poneglyph tops out at
 * y=5, the premio dais at y=1, the pilasters at y=2.</p>
 *
 * <p>Copper and iron against the room's deepslate and basalt on purpose: the seal is la Orden's and
 * this is not — it is something people built to get in and out. The cage is see-through above the
 * grate line because el Acreedor and la Orden stand on the flanks behind it.</p>
 */
public final class DungeonElevator {
    private DungeonElevator() {}

    /** Live, and the dead twin — one flag away if dormant heads on every floor are ever wanted. */
    public static final ResourceLocation AWAKE =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon/fixture/ascensor");
    public static final ResourceLocation DORMANT =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon/fixture/ascensor_dormido");

    /**
     * Half the fixture's footprint. It must match the authored structure — {@code ASC_HALF} in the
     * room tool — because it is how the marker is turned back into the paste corner.
     */
    private static final int HALF = 3;

    /**
     * Pastes the fixture centred on {@code marker}.
     *
     * <p>The footprint is <b>square</b>, which is what makes the centring work under rotation: a
     * square bounding box maps onto itself however it is turned, so pinning the box's minimum
     * corner to {@code marker − (HALF, 1, HALF)} always lands the middle column on the marker. The
     * min-corner correction is the same one the room paste makes, and for the same reason —
     * rotation happens about the template origin, so the turned box can run into negative
     * coordinates.</p>
     *
     * @param marker   where the {@code ascensor} marker was extracted: the middle of the car, one
     *                 block above its floor
     * @param rotation the rotation the exit template was placed with, so the mouth ends up facing
     *                 the way the party walks in whichever side the boss attached on
     */
    public static void stamp(ServerLevel level, BlockPos marker, Rotation rotation, boolean awake) {
        ResourceLocation id = awake ? AWAKE : DORMANT;
        StructureTemplate structure = level.getStructureManager().get(id).orElse(null);
        if (structure == null) {
            // Loud rather than silent: the salida would look finished and the tramo boundary the
            // party just earned would be marked by nothing at all.
            Teras.LOGGER.error("Dungeons: the ascensor structure '{}' is missing — the tramo "
                    + "boundary at {} has no lift", id, marker);
            level.setBlock(marker, Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setIgnoreEntities(true);
        BlockPos nominal = marker.offset(-HALF, -1, -HALF);
        BoundingBox box = structure.getBoundingBox(settings, nominal);
        BlockPos corrected = nominal.offset(
                nominal.getX() - box.minX(),
                nominal.getY() - box.minY(),
                nominal.getZ() - box.minZ());
        structure.placeInWorld(level, corrected, corrected, settings, level.getRandom(), 2);
    }
}
