package es.boffmedia.teras.client.camera;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import es.boffmedia.teras.dex.api.DexProvider;
import es.boffmedia.teras.dex.api.DexProviders;
import es.boffmedia.teras.dex.api.DexScan;
import es.boffmedia.teras.integration.PokemonEngines;
import es.boffmedia.teras.quests.QuestBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Works out what the player's camera is pointing at: their position, the block under the crosshair, and
 * the living entities inside the view cone with line of sight — ordered most-centred first.
 *
 * <p>Client thread only (it reads the level and the local player).</p>
 *
 * <p>Pokémon are typed through {@link DexProvider}, so the camera reports them on either engine.
 * Statues and NPCs are single-mod concepts with no neutral equivalent, so each goes through its own
 * guarded class; everything else reports its entity name.</p>
 */
final class ViewScanner {
    private ViewScanner() {}

    private static final double MAX_DETECTION_DISTANCE = 50.0;

    /** Widens the cone past the raw FOV, as in 1.16.5: FOV is horizontal, the cone is a circle. */
    private static final double CONE_FUDGE = 1.5;

    /** Line-of-sight probes per entity: three heights × five horizontal offsets, first clear wins. */
    private static final double MAX_HORIZONTAL_PROBE = 0.3;

    /** {@code {playerPosition, lookingAt?}} for the current view. */
    static JsonObject location(LocalPlayer player, Level level) {
        Vec3 position = player.position();
        Vec3 eye = player.getEyePosition(1.0F);

        JsonObject json = new JsonObject();
        JsonObject playerPosition = new JsonObject();
        playerPosition.addProperty("x", round(position.x, 100.0));
        playerPosition.addProperty("y", round(eye.y, 100.0));
        playerPosition.addProperty("z", round(position.z, 100.0));
        json.add("playerPosition", playerPosition);

        Vec3 end = eye.add(player.getViewVector(1.0F).scale(MAX_DETECTION_DISTANCE));
        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            JsonObject block = new JsonObject();
            block.addProperty("x", hit.getBlockPos().getX());
            block.addProperty("y", hit.getBlockPos().getY());
            block.addProperty("z", hit.getBlockPos().getZ());
            block.addProperty("block", level.getBlockState(hit.getBlockPos()).getBlock().getDescriptionId());
            json.add("lookingAt", block);
        }
        return json;
    }

    /** The entities in view, most-centred first, each described for the web. */
    static JsonArray entities(LocalPlayer player, Level level) {
        double fov = CameraZoom.zoomedFov(Minecraft.getInstance().options.fov().get()) * CONE_FUDGE;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);

        AABB search = player.getBoundingBox().inflate(MAX_DETECTION_DISTANCE);
        List<Candidate> candidates = new ArrayList<>();
        for (Entity entity : level.getEntities(player, search)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            Vec3 center = entity.position().add(0.0, entity.getBbHeight() / 2.0, 0.0);
            Vec3 toEntity = center.subtract(eye).normalize();
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(toEntity)))));
            if (angle > fov / 2.0 || !hasLineOfSight(level, player, eye, entity)) {
                continue;
            }
            double distance = eye.distanceTo(center);
            // Angle dominates so the subject you aimed at leads; distance only breaks near-ties.
            candidates.add(new Candidate(living, angle + (distance / MAX_DETECTION_DISTANCE) * 5.0,
                    distance, screenCoverage(entity, distance, fov)));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score));

        JsonArray array = new JsonArray();
        for (Candidate candidate : candidates) {
            array.add(describe(candidate));
        }
        return array;
    }

    private record Candidate(LivingEntity entity, double score, double distance, double coverage) {}

    private static JsonObject describe(Candidate candidate) {
        Entity entity = candidate.entity();
        Vec3 position = entity.position();

        JsonObject json = new JsonObject();
        json.addProperty("distance", round(candidate.distance(), 10.0));
        json.addProperty("coverage", round(candidate.coverage(), 10.0));
        JsonObject positionJson = new JsonObject();
        positionJson.addProperty("x", round(position.x, 100.0));
        positionJson.addProperty("y", round(position.y, 100.0));
        positionJson.addProperty("z", round(position.z, 100.0));
        json.add("position", positionJson);

        DexProvider provider = DexProviders.get();
        DexScan scan = provider == null ? null : provider.scan(entity);
        if (scan != null) {
            json.addProperty("type", isStatue(entity) ? "statue" : "pokemon");
            json.addProperty("species", scan.species());
            json.addProperty("dex", scan.dex());
            json.addProperty("form", scan.form());
            json.addProperty("palette", scan.palette());
            return json;
        }

        String npcName = npcName(entity);
        if (npcName != null) {
            json.addProperty("type", "npc");
            json.addProperty("name", npcName);
            return json;
        }

        json.addProperty("type", "other");
        json.addProperty("name", entity.getType().getDescription().getString());
        return json;
    }

    /** {@code entity}'s NPC name, or {@code null} — {@code null} whenever CustomNPCs is absent. */
    private static String npcName(Entity entity) {
        if (!ModList.get().isLoaded(QuestBridge.CUSTOMNPCS_MOD_ID)) {
            return null;
        }
        return es.boffmedia.teras.client.camera.npc.CustomNpcInfo.nameOf(entity);
    }

    /** Whether this is a Pixelmon statue rather than a live Pokémon; false on any other engine. */
    private static boolean isStatue(Entity entity) {
        return PokemonEngines.isPixelmonLoaded()
                && es.boffmedia.teras.client.camera.pixelmon.PixelmonStatues.isStatue(entity);
    }

    /** Roughly what percentage of screen height {@code entity} fills, from its height and the FOV. */
    private static double screenCoverage(Entity entity, double distance, double horizontalFov) {
        if (distance <= 0.0) {
            return 100.0;
        }
        var window = Minecraft.getInstance().getWindow();
        double aspect = window.getHeight() == 0 ? 16.0 / 9.0
                : (double) window.getWidth() / window.getHeight();
        double horizontal = Math.toRadians(horizontalFov);
        double vertical = 2.0 * Math.atan(Math.tan(horizontal / 2.0) / aspect);
        double visibleHeight = 2.0 * distance * Math.tan(vertical / 2.0);
        return visibleHeight <= 0.0 ? 0.0 : (entity.getBbHeight() / visibleHeight) * 100.0;
    }

    /** Whether any probe point on {@code entity} is reachable from {@code eye} without hitting a block. */
    private static boolean hasLineOfSight(Level level, LocalPlayer player, Vec3 eye, Entity entity) {
        double[] heights = {0.1, entity.getBbHeight() / 2.0, entity.getBbHeight() * 0.9};
        double spread = Math.min(entity.getBbWidth() / 4.0, MAX_HORIZONTAL_PROBE);
        Vec3[] offsets = {
                Vec3.ZERO,
                new Vec3(spread, 0.0, 0.0), new Vec3(-spread, 0.0, 0.0),
                new Vec3(0.0, 0.0, spread), new Vec3(0.0, 0.0, -spread),
        };
        for (double height : heights) {
            for (Vec3 offset : offsets) {
                Vec3 target = entity.position().add(offset.x, height, offset.z);
                BlockHitResult hit = level.clip(new ClipContext(
                        eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() == HitResult.Type.MISS
                        || eye.distanceToSqr(target) < eye.distanceToSqr(hit.getLocation())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double round(double value, double scale) {
        return Math.round(value * scale) / scale;
    }
}
