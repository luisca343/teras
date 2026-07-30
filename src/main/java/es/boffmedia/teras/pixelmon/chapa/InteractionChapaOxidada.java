package es.boffmedia.teras.pixelmon.chapa;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.config.PixelmonConfigProxy;
import com.pixelmonmod.pixelmon.api.events.pokemon.BottleCapEvent;
import com.pixelmonmod.pixelmon.api.interactions.IInteraction;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.pokemon.stats.IVStore;
import com.pixelmonmod.pixelmon.comm.ChatHandler;
import com.pixelmonmod.pixelmon.comm.packetHandlers.OpenScreenPacket;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.enums.EnumGuiScreen;
import com.pixelmonmod.pixelmon.enums.items.EnumBottleCap;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Right-clicking your own Pokémon with a {@link es.boffmedia.teras.items.ChapaOxidada}: opens
 * Pixelmon's stat-selection screen, previewing what each stat would fall to.
 *
 * <p>The choice comes back as a {@code SelectStatPacket}, which Pixelmon would reject for a
 * non-Pixelmon item — {@code SelectStatPacketMixin} is what turns it into a ruining.</p>
 */
public class InteractionChapaOxidada implements IInteraction {

    @Override
    public boolean processInteract(PixelmonEntity pixelmon, Player player, InteractionHand hand, ItemStack stack) {
        // Server side only. The 1.16.5 original had this test inverted (`!isClientSide`), so the
        // whole interaction ran on the client and could not persist anything it did.
        if (player.level().isClientSide || hand == InteractionHand.OFF_HAND
                || !stack.is(ItemInit.CHAPA_OXIDADA.get())) {
            return false;
        }

        Pokemon pokemon = pixelmon.getPokemon();
        // Yours only. Ruining someone else's Pokémon is the one thing this item must never do.
        if (pokemon.getOwnerPlayer() != player) {
            return false;
        }

        // Same level gate as a real bottle cap: capped Pokémon only. Keeps the cap from being the
        // cheapest way to sabotage something still growing.
        if (pokemon.getPokemonLevel() < PixelmonConfigProxy.getGeneral().getMaxLevel()) {
            ChatHandler.sendChat(player, "pixelmon.interaction.bottlecap.level", pixelmon.getNickname());
            return true;
        }

        if (!hasAnythingToRuin(pokemon)) {
            ChatHandler.sendChat(player, "teras.chapa_oxidada.nothing", pixelmon.getNickname());
            return true;
        }

        // Posted as SILVER: the event exists so other mods can veto hyper-training an IV, and a
        // rusty cap is that same operation pointed downwards.
        if (Pixelmon.EVENT_BUS.post(new BottleCapEvent(pixelmon, player, EnumBottleCap.SILVER, stack)).isCanceled()) {
            return false;
        }

        OpenScreenPacket.open(player, EnumGuiScreen.BottleCap, screenData(pixelmon, pokemon));
        return true;
    }

    /** False when every stat is already 0 or hyper trained, so the screen would offer nothing. */
    private static boolean hasAnythingToRuin(Pokemon pokemon) {
        IVStore ivs = pokemon.getIVs();
        for (BattleStatsType type : BattleStatsType.getEVIVStatValues()) {
            if (!ivs.isHyperTrained(type) && ivs.getStat(type) != ChapaRuin.RUINED) {
                return true;
            }
        }
        return false;
    }

    /** Six previewed stat values then the entity id, which is the layout the screen decodes. */
    private static int[] screenData(PixelmonEntity pixelmon, Pokemon pokemon) {
        BattleStatsType[] types = BattleStatsType.getEVIVStatValues();
        int[] data = new int[types.length + 1];
        for (int i = 0; i < types.length; i++) {
            data[i] = ChapaRuin.preview(pokemon, types[i]);
        }
        data[types.length] = pixelmon.getId();
        return data;
    }
}
