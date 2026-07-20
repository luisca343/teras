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

    /**
     * Which dungeon-gear definition a stack embodies, for gear built on a <b>vanilla</b> base item
     * (a diamond sword, not a registered {@code teras:} item). Registered gear carries its id in
     * the item class; vanilla-based gear can only carry it here. Written by loot tables via
     * {@code minecraft:set_components} and read by {@code GearHolder.defOf}, which makes it the
     * one component an admin ever sets by hand — everything else is derived from it by the stamp.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> GEAR_ID =
            COMPONENTS.register("gear_id", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /**
     * Which revision of the gear catalog a piece was last stamped against. The catalog is config
     * owned and only ever loaded on the server, so a piece has to carry its own numbers: without
     * this the client would render whatever the built-in defaults said and quietly disagree with
     * the damage the server was actually applying. Bumped by every load, so
     * {@code GearRefresh} can spot a stale stack and re-stamp it.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GEAR_GENERATION =
            COMPONENTS.register("gear_generation", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** The stamped ability magnitude, so the tooltip reads the server's number, not the client's. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> GEAR_MAGNITUDE =
            COMPONENTS.register("gear_magnitude", () -> DataComponentType.<Double>builder()
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                    .build());
}
