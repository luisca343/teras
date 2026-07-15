package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/**
 * Custom item data components.
 *
 * <p>{@link #SMARTROTOM_ID} is the per-item SmartRotom instance id. It replaces the fragile 1.16.5
 * {@code PadID} scheme (a client-side {@code padList.size()+1} counter written into item NBT): here
 * it is a persistent, network-synchronised {@link UUID} assigned <b>server-side</b> (see
 * {@code SmartRotom#inventoryTick}). Each individual SmartRotom item therefore has its own stable id
 * and, on the client, its own {@link com.cinemamod.mcef.MCEFBrowser} — so two SmartRotoms show two
 * independent pages, exactly like the original per-item pads.</p>
 */
public final class ComponentInit {
    private ComponentInit() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Teras.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> SMARTROTOM_ID =
            COMPONENTS.register("smartrotom_id", () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());
}
