package es.boffmedia.teras.client.combat;

import es.boffmedia.teras.Teras;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * The esquiva's mod-bus registration, split from {@link DodgeKeys} to match the shape
 * {@code CameraKeys} / {@code CameraSetup} already use.
 *
 * <p><b>The split is a convention, not a requirement.</b> {@code CameraSetup}'s javadoc says "one
 * class cannot do both", and that is <i>not true</i> on this NeoForge — verified in the bytecode of
 * {@code AutomaticEventSubscriber} (FML loader 4.0.43): it sorts each {@code @SubscribeEvent} method
 * by whether its event implements {@code IModBusEvent}, and when a class holds some of each it logs
 * <i>"Found mix of game bus and mod bus listeners… registering them separately"</i> and registers
 * them method by method. So a single class would have worked; this is split for consistency with the
 * only other keybind in the mod, and the note exists so nobody spends the same hour twice.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class DodgeSetup {
    private DodgeSetup() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DodgeKeys.DODGE);
    }
}
