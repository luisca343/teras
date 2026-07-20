package es.boffmedia.teras.dungeon.mecanica;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;

/**
 * Combat webbing: what a {@code tejedora} sprays and what the queen walls a room with.
 *
 * <h2>Why this is not {@code minecraft:cobweb}</h2>
 *
 * Two reasons, both structural. The party plays in <b>adventure mode</b> and cannot break blocks, so
 * vanilla cobweb across a route is a permanent trap with no way out. And the properties asked of
 * combat webbing are mutually exclusive in one block: {@code LivingEntity.hasLineOfSight} and
 * projectile raycasts both clip against {@code ClipContext.Block.COLLIDER}, so blocking sight or
 * arrows requires a collision shape — which is exactly what "walk through it slowly" forbids.
 *
 * <p>Hence two densities on one block:</p>
 *
 * <ul>
 *   <li><b>thin</b> — no collision. Slows whoever stands in it. What a webber sprays.</li>
 *   <li><b>dense</b> — full collision, so it stops movement, projectiles and line of sight alike.
 *       The queen's, and never a webber's: a mob able to wall off a room at range would decide the
 *       fight by itself.</li>
 * </ul>
 *
 * <p>{@code venenosa} is orthogonal to density, so the poisonous variant costs no extra block.</p>
 *
 * <p>Both decay. That is the hard guarantee that a room can never web itself impassable — and dense
 * webbing decays faster, because it is a temporary wall rather than a hazard.</p>
 */
public class TelaranaBlock extends Block {

    public static final MapCodec<TelaranaBlock> CODEC = simpleCodec(TelaranaBlock::new);

    public static final BooleanProperty DENSA = BooleanProperty.create("densa");
    public static final BooleanProperty VENENOSA = BooleanProperty.create("venenosa");

    /** Ticks a thin web lasts. Long enough to shape a fight, short enough to never strand anyone. */
    public static final int THIN_DECAY = 400;
    /** Dense webbing is a wall; it earns far less time. */
    public static final int DENSE_DECAY = 140;

    public TelaranaBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(DENSA, false)
                .setValue(VENENOSA, false));
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DENSA, VENENOSA);
    }

    /**
     * The whole trick: the shape depends on the state, so one block is both a hazard you wade
     * through and a wall you cannot see past.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return state.getValue(DENSA) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // Dense webbing is collided with, not stood in; only the thin kind slows.
        if (!state.getValue(DENSA)) {
            entity.makeStuckInBlock(state, new Vec3(0.3, 0.09, 0.3));
        }
        if (state.getValue(VENENOSA) && entity instanceof LivingEntity living
                && !level.isClientSide && living.tickCount % 20 == 0) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
        }
    }

    /** Scheduled by {@link WebPlacer} when the web is placed; firing means its time is up. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }
}
