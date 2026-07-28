package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Teras's own sound events. The first registry the mod has had — 1.16.5 declared its sounds in
 * {@code sounds.json} and played them by {@link ResourceLocation} without ever registering a
 * {@link SoundEvent}, which worked there and does not here: {@code SoundEvent} is a registry object
 * on 1.21, so an unregistered id plays nothing and logs nothing.
 */
public final class SoundInit {
    private SoundInit() {}

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Teras.MOD_ID);

    /**
     * The shiny chime. Attenuates with distance like any world sound, so a shiny spotted at the edge
     * of the tracking range is audibly further away than one at your feet.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARKLE = register("sparkle");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
