package es.boffmedia.teras.items;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * "What am I pointing at" for the hand-held items that reach past melee range.
 *
 * <p>Replaces the 1.16.5 {@code util/math/vector/RayTrace}, which was vendored Forge code. Vanilla
 * ships the same walk as {@code ProjectileUtil.getEntityHitResult}, so this is only the call shape:
 * the eye-to-aim segment, a bounding box grown along it, and a filter.</p>
 */
final class ItemRayTrace {
    private ItemRayTrace() {}

    /**
     * The first entity {@code player} is aiming at within {@code range}, or {@code null}.
     *
     * <p>Deliberately <b>not</b> occlusion-tested against blocks: both callers are short-range
     * roleplay items where a whip cracking through a fence post is a smaller problem than one that
     * mysteriously does nothing. Add a {@code level.clip} if that ever stops being true.</p>
     */
    static Entity entity(Player player, double range, Predicate<Entity> filter) {
        Vec3 eyes = player.getEyePosition(1.0F);
        Vec3 reach = player.getViewVector(1.0F).scale(range);
        Vec3 end = eyes.add(reach);
        AABB search = player.getBoundingBox().expandTowards(reach).inflate(1.0);

        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player.level(), player, eyes, end, search,
                candidate -> !candidate.isSpectator() && candidate.isPickable() && filter.test(candidate));
        return hit == null ? null : hit.getEntity();
    }
}
