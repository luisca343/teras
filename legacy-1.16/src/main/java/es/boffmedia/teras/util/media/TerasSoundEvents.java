package es.boffmedia.teras.util.media;

import es.boffmedia.teras.Teras;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class TerasSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
                DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Teras.MOD_ID);

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> new SoundEvent(new ResourceLocation(Teras.MOD_ID, name)));

    }

    public static final RegistryObject<SoundEvent> SMALL_EXLPOSION = registerSoundEvent("test");
    public static final RegistryObject<SoundEvent> LATIGO_NUMERIL = registerSoundEvent("latigo_numeril");
    public static final RegistryObject<SoundEvent> TASER = registerSoundEvent("taser");
    public static final RegistryObject<SoundEvent> BONK = registerSoundEvent("bonk");

    public static void register(IEventBus eventBus) {SOUND_EVENTS.register(eventBus);}

}
