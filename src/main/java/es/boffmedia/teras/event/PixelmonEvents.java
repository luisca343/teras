package es.boffmedia.teras.event;

import com.pixelmonmod.pixelmon.api.events.ExperienceGainEvent;
import com.pixelmonmod.pixelmon.api.events.PokedexEvent;
import com.pixelmonmod.pixelmon.api.events.ShopkeeperEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import com.pixelmonmod.pixelmon.api.pokedex.PokedexRegistrationStatus;
import com.pixelmonmod.pixelmon.entities.npcs.NPCShopkeeper;
import com.pixelmonmod.pixelmon.entities.npcs.registry.ShopItemWithVariation;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.data.smartrotom.SmartRotomService;
import es.boffmedia.teras.util.objects.ShopTransaction;
import es.boffmedia.teras.util.objects.dex.ActualizarDex;
import es.boffmedia.teras.pixelmon.battle.TerasBattle;
import es.boffmedia.teras.pixelmon.battle.TerasBattleController;
import es.boffmedia.teras.pixelmon.battle.TerasBattleRuleRegistry;
import es.boffmedia.teras.util.string.MessageHelper;
import es.boffmedia.teras.util.data.PersistentDataFields;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

@Mod.EventBusSubscriber
public class PixelmonEvents {

    @SubscribeEvent
    public void cancelarXP(ExperienceGainEvent event){
        try {
            if (!event.pokemon.hasOwner()) return;
        }catch (NullPointerException e){
            Teras.LOGGER.warn("Error al cancelar XP. Ignorando...");
            return;
        }
        ServerPlayerEntity player = event.pokemon.getPlayerOwner();
        if(!event.pokemon.getPlayerOwner().getPersistentData().getBoolean(PersistentDataFields.FB_ACTIVO.label)) return;
        MessageHelper.enviarMensaje(player, "No puedes ganar experiencia en el Frente Batalla");
        event.setCanceled(true);
    }



    @OnlyIn(Dist.DEDICATED_SERVER)
    @SubscribeEvent
    public void guardarDex(PokedexEvent.Post event){
        String uuid = event.getPlayer().getStringUUID();
        int idPokemon = event.getPokemon().getSpecies().getDex();

        String form = event.getPokemon().getForm().getName();
        String palette = event.getPokemon().getPalette().getName();
        int estado = event.getNewStatus().equals(PokedexRegistrationStatus.SEEN) ? 0 : 1;

        ActualizarDex dex = new ActualizarDex(uuid, idPokemon, estado, form, palette);
        SmartRotomService.postRegistry(dex);
    }
    
    @SubscribeEvent
    public void pokedex(PokedexEvent event){
        event.getOldStatus();
    }

    @SubscribeEvent
    public void compraVenta(ShopkeeperEvent event){
        NPCShopkeeper npc = (NPCShopkeeper) event.getNpc();
        ItemStack itemStack;

        boolean encontrado = false;
        String currentLanguage = event.getEntityPlayer().getLanguage();
        ArrayList<ShopItemWithVariation> objetos;

        if(event instanceof ShopkeeperEvent.Purchase){
            itemStack = ((ShopkeeperEvent.Purchase) event).getItem();
            ((ShopkeeperEvent.Purchase) event).getItem();
            objetos = npc.getItemList();

        }else{
            itemStack = ((ShopkeeperEvent.Sell) event).getItem();
            objetos = npc.getSellList(event.getEntityPlayer());
        }

        for (ShopItemWithVariation objeto : objetos) {
            if(Objects.equals(objeto.getItemStack().getDisplayName().getString(), itemStack.getDisplayName().getString())){
                String npcName = ((NPCShopkeeper) event.getNpc()).getShopkeeperName(currentLanguage);
                String itemName = itemStack.getDisplayName().getString();
                String operation = event instanceof ShopkeeperEvent.Purchase ? "COMPRA" : "VENTA";

                int unitPrice = event instanceof ShopkeeperEvent.Purchase ? objeto.getBuyCost() : objeto.getSellCost();
                int count = itemStack.getCount();

                ShopTransaction shopTransaction = new ShopTransaction(event.getEntityPlayer().getStringUUID(), npcName, itemName, operation, unitPrice, count);
                SmartRotomService.saveShopTransaction(shopTransaction);

                encontrado = true;
            }
        }

        if(!encontrado) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBattleStart(BattleStartedEvent event){
        Teras.getLogger().warn("========== COMBATE INICIADO ==========");
        Optional<Boolean> isSpecial = event.getBattleController().rules.get(TerasBattleRuleRegistry.SPECIAL_BATTLE);
        if(!isSpecial.isPresent() || !isSpecial.get()) {
            Teras.getLogger().warn("No es un combate especial");
            TerasBattleController lbc = Teras.getLBC();
            int battleIndex = event.getBattleController().battleIndex;
            if(lbc.existsTerasBattle(battleIndex)){
                Teras.getLogger().warn("Existe el combate, no se ha creado");
            } else {
                Teras.getLogger().warn("No se ha encontrado el combate, se crea");
                lbc.addTerasBattle(battleIndex, new TerasBattle(event.getBattleController()));
            }
        } else {
            Teras.getLogger().warn("Es un combate especial");
        }
    }
}
