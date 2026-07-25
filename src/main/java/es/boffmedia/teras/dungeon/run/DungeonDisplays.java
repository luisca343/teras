package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * Floating item and label displays, for shop pedestals and devil offers.
 *
 * <p>Built from NBT rather than by calling setters: {@code Display}'s accessors are all private in
 * vanilla, and its {@code readAdditionalSaveData} is the supported way in — so a tag through
 * {@link EntityType#create(CompoundTag, net.minecraft.world.level.Level)} is both public API and
 * exactly what the game itself does when it loads one from disk.</p>
 *
 * <p>Every display made here is tagged, which is what lets a floor tear its own down without
 * having to have kept the ids.</p>
 */
public final class DungeonDisplays {
    private DungeonDisplays() {}

    /** Marks a display as a run's, so teardown can find it without a bookkeeping list. */
    public static final String DISPLAY_TAG = "teras_dungeon_display";

    /**
     * The tag that says <b>which fixture</b> a display belongs to.
     *
     * <p>Without it a sweep can only ask "is this one of ours", which is a question about the floor
     * and not about the pedestal — so clearing one bought shop slot discarded the displays of every
     * neighbouring slot inside the sweep box. A six-pedestal counter stands two blocks apart and the
     * box was three, so buying at one end blanked the whole counter: the wares were still for sale
     * and nothing could be seen. The treasure room's three stands wiped each other the same way, one
     * refresh at a time, and the chest stands inherited it (PISOS §70).</p>
     */
    public static String fixtureTag(BlockPos fixture) {
        return "teras_fixture_" + fixture.getX() + "_" + fixture.getY() + "_" + fixture.getZ();
    }

    /** A slowly spinning item hanging over a pedestal. Returns null if the level refuses it. */
    public static Entity spawnItem(ServerLevel level, BlockPos pos, double yOffset, ItemStack stack) {
        CompoundTag tag = base("minecraft:item_display", pos, yOffset);
        CompoundTag item = new CompoundTag();
        item.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString());
        item.putInt("count", Math.max(1, stack.getCount()));
        tag.put("item", item);
        tag.putString("item_display", "ground");
        tag.putString("billboard", "vertical");
        // A quarter turn a second: enough to read as an offer rather than a dropped item.
        tag.put("transformation", scaleTag(0.75f));
        return spawn(level, tag);
    }

    /** A floating label — a price, a name — that always faces the reader. */
    public static Entity spawnText(ServerLevel level, BlockPos pos, double yOffset, Component text) {
        CompoundTag tag = base("minecraft:text_display", pos, yOffset);
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        tag.putString("billboard", "center");
        tag.putBoolean("see_through", false);
        tag.putBoolean("shadow", true);
        tag.putInt("background", 0x40000000);
        tag.put("transformation", scaleTag(0.8f));
        return spawn(level, tag);
    }

    private static CompoundTag base(String id, BlockPos pos, double yOffset) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(pos.getX() + 0.5));
        position.add(DoubleTag.valueOf(pos.getY() + yOffset));
        position.add(DoubleTag.valueOf(pos.getZ() + 0.5));
        tag.put("Pos", position);
        tag.putFloat("view_range", 1.0f);
        ListTag tags = new ListTag();
        tags.add(net.minecraft.nbt.StringTag.valueOf(DISPLAY_TAG));
        tags.add(net.minecraft.nbt.StringTag.valueOf(fixtureTag(pos)));
        tag.put("Tags", tags);
        return tag;
    }

    private static CompoundTag scaleTag(float scale) {
        CompoundTag transformation = new CompoundTag();
        transformation.put("scale", floats(scale, scale, scale));
        transformation.put("translation", floats(0, 0, 0));
        transformation.put("left_rotation", floats(0, 0, 0, 1));
        transformation.put("right_rotation", floats(0, 0, 0, 1));
        return transformation;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    private static Entity spawn(ServerLevel level, CompoundTag tag) {
        Entity entity = EntityType.create(tag, level).orElse(null);
        if (entity == null) {
            Teras.LOGGER.warn("Dungeons: could not create display entity {}", tag.getString("id"));
            return null;
        }
        entity.addTag(DISPLAY_TAG);
        if (!level.addFreshEntity(entity)) {
            Teras.LOGGER.warn("Dungeons: level refused a display entity at {}", entity.blockPosition());
            return null;
        }
        return entity;
    }

    /**
     * Discards every display this system put up inside a box, by tag.
     *
     * <p>The tag has been applied since displays existed and <b>nothing read it</b> — the javadoc
     * above claimed it was what let a floor tear its displays down, and that was simply untrue. This
     * makes it true, and closes a real hole while it is at it: {@link #discard} looks an entity up
     * by UUID, and {@code getEntity} returns null for anything in an unloaded chunk, so a shop whose
     * pad had gone quiet kept its pedestals. That is the same unloaded-chunk blindness §38 fixed in
     * the materializer, in a second place.</p>
     *
     * <p>Chunks are touched first for exactly that reason. Boxes here are a few blocks across, so
     * the cost is a handful of loads.</p>
     */
    /**
     * Discards the displays of <b>one fixture</b>, wherever they drifted to.
     *
     * <p>This is what every caller wants: a pedestal clearing its own stand. Matching on the
     * fixture's tag rather than on a box means two pedestals may stand side by side — or on the same
     * block — and still never touch each other's displays. The box is kept only as a broad phase, so
     * the entity query stays cheap; it is deliberately generous, because correctness now comes from
     * the tag and not from the geometry.</p>
     */
    public static void sweep(ServerLevel level, BlockPos fixture) {
        sweepTagged(level, fixture, 3, fixtureTag(fixture));
    }

    /**
     * Discards every display of this system inside a box, whichever fixture it belongs to. For a
     * teardown that has no fixture list to walk — a floor being discarded, not a stand being cleared.
     */
    public static void sweep(ServerLevel level, BlockPos centre, int radius) {
        sweepTagged(level, centre, radius, DISPLAY_TAG);
    }

    private static void sweepTagged(ServerLevel level, BlockPos centre, int radius, String tag) {
        int minChunkX = net.minecraft.core.SectionPos.blockToSectionCoord(centre.getX() - radius);
        int maxChunkX = net.minecraft.core.SectionPos.blockToSectionCoord(centre.getX() + radius);
        int minChunkZ = net.minecraft.core.SectionPos.blockToSectionCoord(centre.getZ() - radius);
        int maxChunkZ = net.minecraft.core.SectionPos.blockToSectionCoord(centre.getZ() + radius);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                centre.getX() - radius, centre.getY() - radius, centre.getZ() - radius,
                centre.getX() + radius + 1, centre.getY() + radius + 1, centre.getZ() + radius + 1);
        for (Entity entity : level.getEntities((Entity) null, box,
                e -> e.getTags().contains(tag))) {
            entity.discard();
        }
    }

    /** Discards a display spawned here; tolerates a null or already-gone entity. */
    public static void discard(ServerLevel level, java.util.UUID id) {
        if (id == null) {
            return;
        }
        Entity entity = level.getEntity(id);
        if (entity != null) {
            entity.discard();
        }
    }
}
