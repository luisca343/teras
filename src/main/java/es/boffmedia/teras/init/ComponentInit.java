package es.boffmedia.teras.init;

import com.mojang.serialization.Codec;
import es.boffmedia.teras.Teras;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/**
 * Custom item data components.
 *
 * <p>{@link #SMARTROTOM_ID} is the per-item SmartRotom instance id: a persistent,
 * network-synchronised {@link UUID} assigned <b>server-side</b> (see {@code SmartRotom#inventoryTick})
 * so a client cannot spoof which browser is which. Each SmartRotom item therefore has its own stable
 * id and, on the client, its own {@link com.cinemamod.mcef.MCEFBrowser}.</p>
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

    /**
     * A funko's skin PNG inside {@code Teras/skins/}. The other skin mode (a player) needs no
     * component of its own: it reuses vanilla's
     * {@link net.minecraft.core.component.DataComponents#PROFILE}, exactly as player heads do.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> FUNKO_SKIN_FILE =
            COMPONENTS.register("funko_skin_file", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());
}
