package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.fluids.AguasTermalesFluid;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Hot-springs fluid. Textures and tint are client-side, in {@code AguasTermalesClient}. */
public final class FluidInit {
    private FluidInit() {}

    public static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    public static final ResourceLocation WATER_FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");
    public static final ResourceLocation WATER_OVERLAY = ResourceLocation.withDefaultNamespace("block/water_overlay");

    /** Tint applied to the vanilla water textures. */
    public static final int TINT = 0xFF40D9F7;

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Teras.MOD_ID);

    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Teras.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> AGUAS_TERMALES_TYPE = FLUID_TYPES.register(
            "aguas_termales", () -> new FluidType(FluidType.Properties.create().canConvertToSource(true)));

    public static final DeferredHolder<Fluid, FlowingFluid> AGUAS_TERMALES_SOURCE =
            FLUIDS.register("aguas_termales", () -> new AguasTermalesFluid.Source(properties()));

    public static final DeferredHolder<Fluid, FlowingFluid> AGUAS_TERMALES_FLOWING =
            FLUIDS.register("aguas_termales_flowing", () -> new AguasTermalesFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(AGUAS_TERMALES_TYPE, AGUAS_TERMALES_SOURCE, AGUAS_TERMALES_FLOWING)
                .block(BlockInit.AGUAS_TERMALES)
                .bucket(ItemInit.CUBO_AGUAS_TERMALES);
    }
}
