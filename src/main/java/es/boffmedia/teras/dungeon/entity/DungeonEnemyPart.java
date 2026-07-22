package es.boffmedia.teras.dungeon.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One hittable box of a multipart enemy. A single square hitbox cannot cover an elongated body
 * without swallowing the empty air beside it, so the long arachnids carry these: invisible boxes,
 * positioned each tick over the head and abdomen, that route every hit back to the parent. The
 * parent keeps its own door-sized box for collision and pathing.
 *
 * <p>Not spawned, saved or ticked on its own — the parent moves and resizes it. Not pushable or
 * collidable, so it never touches movement; only attacks.</p>
 */
public class DungeonEnemyPart extends PartEntity<DungeonGeoEnemy> {

    private final DungeonGeoEnemy parent;
    private EntityDimensions dimensions;

    public DungeonEnemyPart(DungeonGeoEnemy parent) {
        super(parent);
        this.parent = parent;
        this.dimensions = EntityDimensions.scalable(0.1f, 0.1f);
        this.noPhysics = true;
        refreshDimensions();
    }

    /** Resize and reposition to a world box centred horizontally on {@code (x, z)}, bottom at {@code y}. */
    public void place(double x, double y, double z, float width, float height) {
        if (width != dimensions.width() || height != dimensions.height()) {
            dimensions = EntityDimensions.scalable(width, height);
            refreshDimensions();
        }
        setPos(x, y, z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return dimensions;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return isInvulnerableTo(source) ? false : parent.hurtPart(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || parent == entity;
    }

    @Override
    public ItemStack getPickResult() {
        return parent.getPickResult();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        throw new UnsupportedOperationException();
    }
}
