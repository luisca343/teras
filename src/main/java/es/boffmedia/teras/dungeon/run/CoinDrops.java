package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.encounter.EnemySpawner;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * Dungeon enemies pay in coins and in nothing else. Their vanilla drops and experience are
 * cancelled outright: what a floor's mobs happen to carry is somebody else's loot table, and
 * leaving it in meant a run's real income was rotten flesh and bones smuggled home. The coin is
 * the whole in-run economy — shop, arcade, deals and the curse toll all price in it.
 *
 * <p>Coins spawn as item entities that can never be picked up into an inventory
 * ({@link ItemEntity#setNeverPickUp()} plus {@link #COIN_TAG}); {@code RunEngine}'s magnet sweep
 * credits the party wallet and discards them. That keeps the purse authoritative — there is no
 * stack to drop, stash in a shulker or carry out of the dungeon — and it sidesteps pickup
 * mechanics entirely, which matters because the party plays in adventure mode.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class CoinDrops {
    private CoinDrops() {}

    /** Marks an item entity as wallet fodder rather than a real reward standing on the floor. */
    public static final String COIN_TAG = "teras_dungeon_coin";
    /** Same, for the wall-breaker charges a reward roll can hand out. */
    public static final String CHARGE_TAG = "teras_dungeon_charge";

    /** Tier tags stamped by {@link EnemySpawner}; absent means an ordinary wave enemy. */
    public static final String TIER_BOSS_TAG = "teras_dungeon_tier_boss";
    public static final String TIER_MINIBOSS_TAG = "teras_dungeon_tier_miniboss";


    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (isDungeonEnemy(event.getEntity())) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (isDungeonEnemy(event.getEntity())) {
            event.setDroppedExperience(0);
            event.setCanceled(true);
        }
    }

    private static boolean isDungeonEnemy(Entity entity) {
        return entity != null && entity.getTags().contains(EnemySpawner.DUNGEON_TAG);
    }

    /** What a dead enemy is worth on {@code stage}, from its tier tag and the config's ranges. */
    public static int coinsFor(Entity enemy, int stage, net.minecraft.util.RandomSource random) {
        int min;
        int max;
        if (enemy.getTags().contains(TIER_BOSS_TAG)) {
            min = DungeonsConfig.coinsBossMin();
            max = DungeonsConfig.coinsBossMax();
        } else if (enemy.getTags().contains(TIER_MINIBOSS_TAG)) {
            min = DungeonsConfig.coinsMiniBossMin();
            max = DungeonsConfig.coinsMiniBossMax();
        } else {
            min = DungeonsConfig.coinsNormalMin();
            max = DungeonsConfig.coinsNormalMax();
        }
        int base = max <= min ? min : min + random.nextInt(max - min + 1);
        return scaleToStage(base, stage);
    }

    /**
     * Deeper floors pay more, compounding per stage — otherwise shop prices (which scale the same
     * way) outrun a party's income the moment it descends.
     */
    public static int scaleToStage(int amount, int stage) {
        double scaled = amount * Math.pow(1.0 + DungeonsConfig.coinStageScalingPct() / 100.0,
                Math.max(0, stage - 1));
        return Math.max(1, (int) Math.round(scaled));
    }

    /**
     * Drops {@code amount} coins around {@code pos}, split into a few stacks so a boss reads as a
     * burst of money rather than one item. Zero and negative amounts spawn nothing.
     */
    public static void spawnCoins(ServerLevel level, Vec3 pos, int amount) {
        spawnPickups(level, pos, amount, ItemInit.MONEDA_MAZMORRA.get(), COIN_TAG);
    }

    /** Same, for wall-breaker charges — one entity per charge, they are rare enough. */
    public static void spawnCharges(ServerLevel level, Vec3 pos, int amount) {
        spawnPickups(level, pos, amount, ItemInit.CARGA_ROMPEMUROS.get(), CHARGE_TAG);
    }

    private static void spawnPickups(ServerLevel level, Vec3 pos, int amount,
                                     net.minecraft.world.item.Item item, String tag) {
        for (int count : CoinSplit.split(amount)) {
            ItemEntity entity = new ItemEntity(level, pos.x, pos.y + 0.4, pos.z,
                    new ItemStack(item, count),
                    (level.random.nextDouble() - 0.5) * 0.18,
                    0.16,
                    (level.random.nextDouble() - 0.5) * 0.18);
            // Never into an inventory: the wallet is the only place coins can land, and the sweep
            // is what puts them there.
            entity.setNeverPickUp();
            entity.setUnlimitedLifetime();
            entity.addTag(tag);
            level.addFreshEntity(entity);
        }
    }

    public static void spawnCoins(ServerLevel level, BlockPos pos, int amount) {
        spawnCoins(level, Vec3.atCenterOf(pos), amount);
    }
}
