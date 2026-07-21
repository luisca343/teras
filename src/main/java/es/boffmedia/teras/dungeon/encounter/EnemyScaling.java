package es.boffmedia.teras.dungeon.encounter;

import es.boffmedia.teras.Teras;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;

/**
 * Applies a spawn's depth and its table's overrides to an entity that has already joined the level.
 *
 * <p>This is what closes the gap that made a plain entity id untunable: {@code spawnEntity} builds
 * a vanilla mob and calls {@code finalizeSpawn}, so a {@code minecraft:slime} arrived with vanilla
 * numbers and nothing in any table could say otherwise. A spider queen could not be "a big cave
 * spider", and a tramo's dificultad had nowhere to land.</p>
 *
 * <p>Multipliers, not absolutes, and applied as {@code ADD_MULTIPLIED_BASE} modifiers rather than
 * by setting base values. Two reasons: a definition's own stats stay the single place its numbers
 * live, and depth composes with a table override instead of one silently replacing the other — a
 * {@code vida: 1.5} entry on a 1.4 tramo is 2.1&times;, which is what both authors meant.</p>
 */
public final class EnemyScaling {
    private EnemyScaling() {}

    private static final ResourceLocation HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_depth_health");
    private static final ResourceLocation DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_depth_damage");
    private static final ResourceLocation SCALE_ID =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_depth_scale");

    /**
     * @param health multiplier on max health; 1.0 leaves the attribute untouched
     * @param damage multiplier on attack damage
     * @param scale  multiplier on render/hitbox scale
     */
    public static void apply(Entity entity, double health, double damage, double scale) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        boolean healthChanged = modify(living, Attributes.MAX_HEALTH, HEALTH_ID, health);
        modify(living, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damage);
        modify(living, Attributes.SCALE, SCALE_ID, scale);
        if (healthChanged) {
            // Max health rose but current health did not follow it, so an unhealed spawn would
            // arrive already damaged — and a boss scaled to 2x would show a half-empty bar before
            // anyone hit it.
            living.setHealth(living.getMaxHealth());
        }
    }

    /**
     * True when a modifier was actually added. A missing attribute is normal rather than an error:
     * plenty of mobs have no ATTACK_DAMAGE (a slime deals damage without it, a bat deals none), and
     * warning about each one would bury the log in noise for every wave.
     */
    private static boolean modify(LivingEntity living, Holder<Attribute> attribute,
                                  ResourceLocation id, double multiplier) {
        if (multiplier == 1.0) {
            return false;
        }
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        instance.removeModifier(id);
        instance.addPermanentModifier(new AttributeModifier(id, multiplier - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        return true;
    }
}
