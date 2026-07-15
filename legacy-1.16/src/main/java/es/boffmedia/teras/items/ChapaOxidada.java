package es.boffmedia.teras.items;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.pokemon.stats.IVStore;
import com.pixelmonmod.pixelmon.comm.ChatHandler;
import com.pixelmonmod.pixelmon.comm.EnumUpdateType;
import com.pixelmonmod.pixelmon.enums.items.EnumBottleCap;
import com.pixelmonmod.pixelmon.items.BottlecapItem;
import net.minecraft.entity.player.ServerPlayerEntity;

public class ChapaOxidada extends BottlecapItem {

    public ChapaOxidada() {
        super(EnumBottleCap.SILVER);
    }

    public static boolean onSilverSelection(ServerPlayerEntity player, Pokemon pokemon, BattleStatsType type) {
        IVStore store = pokemon.getIVs();
        if (!store.isHyperTrained(type)) {
            store.setStat(type, 0);
            pokemon.getStats().setLevelStats(pokemon.getNature(), pokemon.getForm(), pokemon.getPokemonLevel());
            pokemon.markDirty(new EnumUpdateType[]{EnumUpdateType.HP, EnumUpdateType.Stats});
            ChatHandler.sendChat(player, "pixelmon.interaction.bottlecap.silvercap", new Object[]{pokemon.getDisplayName(), type.getTranslatedName()});
            return true;
        } else {
            return false;
        }
    }


}
