package es.boffmedia.teras.dungeon.gear;

import es.boffmedia.teras.Teras;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's loot function types. One so far, and it exists so loot tables can ask the gear catalog
 * for a piece instead of naming one — see {@link GearRoll} for why that mattered.
 */
public final class LootInit {
    private LootInit() {}

    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Teras.MOD_ID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RandomGearFunction>>
            RANDOM_GEAR = LOOT_FUNCTIONS.register("gear_aleatorio",
                    () -> new LootItemFunctionType<>(RandomGearFunction.CODEC));
}
