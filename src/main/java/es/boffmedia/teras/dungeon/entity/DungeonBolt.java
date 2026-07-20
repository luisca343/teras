package es.boffmedia.teras.dungeon.entity;

import es.boffmedia.teras.init.EntityInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * What a dungeon enemy throws. One entity with a kind on it rather than three: an archer's bolt and
 * a webber's shot differ in what they do on impact, not in how they fly, and three entity types
 * would mean three renderers and three registrations for one behaviour.
 *
 * <p>Deliberately not an {@code AbstractArrow}: arrows can be picked up, blocked by shields as
 * arrows, and enchanted against. This is a dungeon enemy's attack, not ammunition.</p>
 */
public class DungeonBolt extends ThrowableProjectile
        implements net.minecraft.world.entity.projectile.ItemSupplier {

    /**
     * What the renderer draws. Borrowing an item sprite rather than authoring a model keeps a
     * projectile from costing an art asset, and the two kinds read apart at a glance.
     */
    @Override
    public net.minecraft.world.item.ItemStack getItem() {
        return new net.minecraft.world.item.ItemStack(kind() == Kind.WEB
                ? net.minecraft.world.item.Items.STRING
                : net.minecraft.world.item.Items.AMETHYST_SHARD);
    }

    /** What the bolt does where it lands. */
    public enum Kind { BOLT, WEB }

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(DungeonBolt.class, EntityDataSerializers.INT);

    private float damage = 3f;

    public DungeonBolt(EntityType<? extends DungeonBolt> type, Level level) {
        super(type, level);
    }

    public DungeonBolt(Level level, LivingEntity owner, Kind kind, float damage) {
        super(EntityInit.DUNGEON_BOLT.get(), owner, level);
        this.damage = damage;
        setKind(kind);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, Kind.BOLT.ordinal());
    }

    public Kind kind() {
        Kind[] values = Kind.values();
        int index = entityData.get(KIND);
        return index >= 0 && index < values.length ? values[index] : Kind.BOLT;
    }

    public void setKind(Kind kind) {
        entityData.set(KIND, kind.ordinal());
    }

    /** Light: these are magic and webbing, not stones. Gravity that arcs steeply reads as a lob. */
    @Override
    protected double getDefaultGravity() {
        return kind() == Kind.WEB ? 0.03 : 0.01;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        if (kind() == Kind.WEB) {
            // Webbing is terrain, not a hit: it lands where it lands and slows whoever is standing
            // there. Damaging on contact as well would make a webber strictly better than an archer.
            webAt(target.blockPosition());
            return;
        }
        Entity owner = getOwner();
        target.hurt(owner instanceof LivingEntity living
                ? damageSources().mobProjectile(this, living)
                : damageSources().magic(), damage);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide) {
            return;
        }
        if (kind() == Kind.WEB) {
            webAt(result.getBlockPos().relative(result.getDirection()));
        }
        discard();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            discard();
        }
    }

    /**
     * Lays thin webbing where the shot landed. Thin, never dense: dense webbing blocks movement,
     * sight and projectiles, and is the queen's alone (DUNGEONS_PISOS.md §10) — a webber able to
     * wall a room off at range would decide the fight on its own.
     */
    private void webAt(BlockPos pos) {
        if (level() instanceof ServerLevel server) {
            es.boffmedia.teras.dungeon.mecanica.WebPlacer.placeThin(server, pos, getOwner());
        }
    }

    /** Projectiles are the owner's business; nothing about a bolt should hurt whoever fired it. */
    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != getOwner();
    }

    public static Projectile shoot(LivingEntity shooter, LivingEntity target, Kind kind,
                                   float damage, float velocity) {
        DungeonBolt bolt = new DungeonBolt(shooter.level(), shooter, kind, damage);
        double dx = target.getX() - shooter.getX();
        double dy = target.getY(0.5) - bolt.getY();
        double dz = target.getZ() - shooter.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        // The vertical lead is what makes a lobbed web reach a target across a room rather than
        // burying itself in the floor halfway there.
        bolt.shoot(dx, dy + flat * 0.15, dz, velocity, 1.0f);
        shooter.level().addFreshEntity(bolt);
        return bolt;
    }
}
