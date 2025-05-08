package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects._old.pixelmon.frentebatalla.GetEquipo;
import es.boffmedia.teras.util.file.FileHelper;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;

import java.util.ArrayList;
import java.util.List;

public class TeamManager {
    public static List<Pokemon> getCurrentTeam(ServerPlayerEntity player){
        PlayerPartyStorage storage = StorageProxy.getParty(player.getUUID());
        return storage.getTeam();
    }

    public static List<Pokemon> getTeam(ServerPlayerEntity player, String file){
        CompoundNBT nbt = FileHelper.readNBT("teras/data/" + player.getUUID() + "/" + file + ".dat");

        List<Pokemon> team = new ArrayList<>();
        for(int i = 0; i < nbt.size(); i++) {
            CompoundNBT pknbt = nbt.getCompound(i+"");
            Pokemon pokemon = PokemonFactory.create(pknbt);
            team.add(pokemon);
        }
        return team;
    }

    public static boolean existsTeam(ServerPlayerEntity player, String file){
        return FileHelper.exists("teras/data/" + player.getUUID() + "/" + file + ".dat");
    }

    public static void deleteTeam(ServerPlayerEntity player, String file){
        FileHelper.deleteFile("teras/data/" + player.getUUID() + "/" + file + ".dat");
    }

    public static void saveTeam(ServerPlayerEntity player, String file){
        Teras.LOGGER.info("Guardando equipo de " + player.getUUID() + " en " + file + ".dat");
        CompoundNBT nbt = new CompoundNBT();
        PlayerPartyStorage storage = StorageProxy.getParty(player.getUUID());
        List<Pokemon> team = storage.getTeam();
        for(int i = 0; i < team.size(); i++) {
            CompoundNBT pknbt = new CompoundNBT();
            team.get(i).writeToNBT(pknbt);
            nbt.put(i+"", pknbt);
        }
        FileHelper.writeNBT("teras/data/" + player.getUUID() + "/"+file+".dat", nbt);
    }


    public static void loadFromSlots(ServerPlayerEntity player, List<GetEquipo.PkmSlot> slots) {
        PlayerPartyStorage storage = StorageProxy.getParty(player.getUUID());
        List<Pokemon> team = storage.getTeam();

        for(int i = 0; i < 6; i++) {
            if(i >= slots.size()){
                storage.set(i, null);
                continue;
            }
            GetEquipo.PkmSlot slot = slots.get(i);
            Pokemon pkm;
            if(slot.getCaja() == -1){
                pkm = team.get(slot.getSlot());
            } else {
                PCStorage pc = StorageProxy.getPCForPlayer(player);
                pkm = pc.get(slot.getCaja(), slot.getSlot());
            }

            storage.set(i, pkm);
        }
    }


    public static void loadTeam(ServerPlayerEntity player, String file){
        Teras.LOGGER.info("Cargando equipo de " + player.getUUID() + " de " + file + ".dat");
        PlayerPartyStorage storage = StorageProxy.getParty(player.getUUID());
        List<Pokemon> team = getTeam(player, file);

        if(team == null || team.isEmpty()) {
            MessageHelper.enviarMensaje(player, "No se ha encontrado el equipo");
            return;
        };

        for(int i = 0; i < 6; i++) {
            if(i >= team.size()){
                storage.set(i, null);
                continue;
            }
            storage.set(i, team.get(i));
        }
    }
}
