package es.boffmedia.teras.util.game;

import es.boffmedia.teras.Teras;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Teras's own damage types.
 *
 * <p>1.16.5 declared these as {@code static final DamageSource} constants ({@code TerasDamageSource.TASER}).
 * On 1.21 a {@link DamageType} is a <b>datapack registry</b> entry, so the Java side can only hold the
 * key — the numbers and the death message live in {@code data/teras/damage_type/}. A source is built
 * per hit against the level's registry, which is also why these take a {@link Level}.</p>
 */
public final class TerasDamageTypes {
    private TerasDamageTypes() {}

    /** The taser's shock. See {@code data/teras/damage_type/taser.json}. */
    public static final ResourceKey<DamageType> TASER = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "taser"));

    /** A source with no attacker — the world dealt it. */
    public static DamageSource source(Level level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().holderOrThrow(type));
    }

    /**
     * A source attributed to {@code attacker}, so the death message names them and the kill counts
     * as theirs. Both entity slots are the attacker: the taser has no separate projectile.
     */
    public static DamageSource source(Level level, ResourceKey<DamageType> type, Entity attacker) {
        return new DamageSource(level.registryAccess().holderOrThrow(type), attacker, attacker);
    }
}
