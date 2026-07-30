package es.boffmedia.teras.mixin;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.comm.packetHandlers.SelectStatPacket;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import es.boffmedia.teras.init.ItemInit;
import es.boffmedia.teras.pixelmon.chapa.ChapaRuin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes Pixelmon's stat-selection screen serve the rusty cap.
 *
 * <p>Needed because {@code handlePacket} gates on
 * {@code stack.getItem() == ItemRegistration.SILVER_BOTTLE_CAP.value()} — an <b>identity</b> check
 * against Pixelmon's own item — and then calls {@code BottlecapItem.onSilverSelection} by
 * {@code invokestatic}. Neither is reachable from a first-party item: subclassing does not satisfy
 * an identity check, and a static is not dispatched to a subclass. So the chapa case is handled
 * whole at HEAD and the original is cancelled; a real silver cap falls through untouched.</p>
 *
 * <p>An {@code @Inject}, not an {@code @Overwrite}: everything Pixelmon does for its own cap keeps
 * working even if that method changes, and the worst case for us is our branch stops matching.</p>
 */
@Mixin(SelectStatPacket.class)
public class SelectStatPacketMixin {

    @Shadow(remap = false)
    private int entityId;

    @Shadow(remap = false)
    private BattleStatsType type;

    @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true, remap = false)
    private void teras$ruinWithChapaOxidada(IPayloadContext context, CallbackInfo ci) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!held.is(ItemInit.CHAPA_OXIDADA.get())) {
            return;
        }

        // From here on this packet is ours whatever happens: cancel first, so a chapa that fails a
        // check below cannot fall through into Pixelmon's silver-cap path and raise the IV to 31.
        ci.cancel();

        Entity target = player.level().getEntity(entityId);
        if (!(target instanceof PixelmonEntity pixelmon)) {
            return;
        }
        Pokemon pokemon = pixelmon.getPokemon();
        // Re-checked here, not trusted from the screen: the client chooses the stat and the entity,
        // so ownership has to hold at the moment the change is applied.
        if (pokemon.getOwnerPlayer() != player) {
            return;
        }

        if (ChapaRuin.ruin(player, pokemon, type) && !player.isCreative()) {
            held.shrink(1);
        }
    }
}
