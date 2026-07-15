package es.boffmedia.teras.event;

import com.pixelmonmod.pixelmon.api.events.BeatTrainerEvent;
import com.pixelmonmod.pixelmon.api.events.BeatWildPixelmonEvent;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.raids.EndRaidEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Element;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.Stats;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.handler.data.IQuestCategory;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.api.wrapper.PlayerWrapper;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber
public class MisionesCaza {
    @SubscribeEvent
    public void derrotarSalvaje(BeatWildPixelmonEvent event) {
        for (int i = 0; i < event.wpp.allPokemon.length; i++) {
            PixelmonWrapper pokemon = event.wpp.allPokemon[i];
            String nombre = pokemon.getSpecies().getName();
            Stats stats = pokemon.getForm();
            avanzarMision("DERROTA", stats, event.player);
        }
    }

    @SubscribeEvent
    public void capturarsalvaje(CaptureEvent.SuccessfulCapture event) {
        PixelmonEntity pokemon = event.getPokemon();
        String nombre = pokemon.getSpecies().getName();
        Stats stats = pokemon.getForm();
        avanzarMision("CAPTURA", stats, event.getPlayer());
    }

    @SubscribeEvent
    public void derrotarRaid(EndRaidEvent event){
        if(event.didRaidersWin()){
            String nombre = event.getRaid().getSpecies().getName();
            Stats stats = event.getRaid().getForm();

            avanzarMision("DERROTA", stats, event.getRaidParticipant().getWrapper().getPlayerOwner());
        }
    }

    @SubscribeEvent
    public void capturarRaid(CaptureEvent.SuccessfulRaidCapture event) {
        Pokemon pokemon = event.getRaidPokemon();
        String nombre = pokemon.getSpecies().getName();
        Stats stats = event.getRaid().getForm();
        avanzarMision("CAPTURA", stats, event.getPlayer());
    }

    @SubscribeEvent
    public void derrotarEntrenador(BeatTrainerEvent event){

        Pokemon[] pokemon = event.trainer.getPokemonStorage().getAll();
        for(Pokemon poke : pokemon){
            if(poke == null) continue;
            Stats stats = poke.getForm();
            avanzarMision("DERROTA", stats, event.player);
        }
    }



    public void avanzarMision(String nombreCategoria, Stats stats, ServerPlayerEntity player){
        if(player == null) return;
        IQuestCategory categoria = getCategoria(nombreCategoria);
        PlayerWrapper playerWrapper = new PlayerWrapper(player);
        if(categoria == null) {
            playerWrapper.message("No existe la categoría de misiones "+nombreCategoria);
            return;
        }
        String nombre = stats.getParentSpecies().getName();
        IQuest[] questsActivas = playerWrapper.getActiveQuests();
        for(IQuest quest : questsActivas){
            if(!quest.getCategory().getName().equalsIgnoreCase(nombreCategoria)) continue;
            for(IQuestObjective objetivo :quest.getObjectives(playerWrapper)){
                String condicion = objetivo.getText().split(":")[0];
                if(condicion.toLowerCase().contains("tipo")){
                    String tipo = condicion.split(" ")[1];
                    Element element = getTipo(tipo.toUpperCase());
                    if(stats.getTypes().contains(element)){
                        avanzar(objetivo, player);
                        player.sendMessage(new StringTextComponent("Has avanzado en la misión " + quest.getName()), UUID.randomUUID());
                    }
                }
                else if(nombre.equalsIgnoreCase(condicion)){
                    avanzar(objetivo, player);
                    player.sendMessage(new StringTextComponent("Has avanzado en la misión " + quest.getName()), UUID.randomUUID());
                }
            }
        }
    }

    private void avanzar(IQuestObjective objetivo, ServerPlayerEntity player) {
        if(objetivo.getMaxProgress() > objetivo.getProgress()){
            objetivo.setProgress(objetivo.getProgress() + 1);
        }else{
            player.sendMessage(new StringTextComponent("La misión ya está completada "),UUID.randomUUID());
        }
    }

    private Element getTipo(String tipo) {
        switch (tipo){
            case "FUEGO":
                return Element.FIRE;
            case "AGUA":
                return Element.WATER;
            case "PLANTA":
                return Element.GRASS;
            case "ELECTRICO":
                return Element.ELECTRIC;
            case "ROCA":
                return Element.ROCK;
            case "TIERRA":
                return Element.GROUND;
            case "ACERO":
                return Element.STEEL;
            case "VOLADOR":
                return Element.FLYING;
            case "PSIQUICO":
                return Element.PSYCHIC;
            case "FANTASMA":
                return Element.GHOST;
            case "SINIESTRO":
                return Element.DARK;
            case "LUCHA":
                return Element.FIGHTING;
            case "NORMAL":
                return Element.NORMAL;
            case "HIELO":
                return Element.ICE;
            case "BICHO":
                return Element.BUG;
            case "VENENO":
                return Element.POISON;
            case "DRAGON":
                return Element.DRAGON;
            case "HADA":
                return Element.FAIRY;
        }
        return null;
    }

    public IQuestCategory getCategoria(String nombre){
        NpcAPI api = NpcAPI.Instance();
        List<IQuestCategory> categories = api.getQuests().categories();
        for (IQuestCategory categoria: categories) {
            if(categoria.getName().equalsIgnoreCase(nombre)){
                return categoria;
            }
        }
        return null;
    }
}
