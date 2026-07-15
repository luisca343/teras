package es.boffmedia.teras.pixelmon.battle;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory;
import com.pixelmonmod.pixelmon.api.storage.PCBox;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.file.FileHelper;
import es.boffmedia.teras.model.battle.PkmSlot;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    public static void saveTeam(UUID uuid, String file){
        PlayerPartyStorage storage = StorageProxy.getParty(uuid);
        saveTeam(storage, file, uuid);
    }

    public static void saveTeam(ServerPlayerEntity player, String file){
        PlayerPartyStorage storage = StorageProxy.getParty(player.getUUID());
        saveTeam(storage, file, player.getUUID());
    }

    public static void saveTeam(PlayerPartyStorage storage, String file, UUID uuid){
        Teras.LOGGER.info("Guardando equipo de " + uuid + " en " + file + ".dat");
        CompoundNBT nbt = new CompoundNBT();
        List<Pokemon> team = storage.getTeam();
        for(int i = 0; i < team.size(); i++) {
            CompoundNBT pknbt = new CompoundNBT();
            team.get(i).writeToNBT(pknbt);
            nbt.put(i+"", pknbt);
        }
        FileHelper.writeNBT("teras/data/" + uuid + "/"+file+".dat", nbt);
    }

    /**
     * Saves a team from specific slots (party or PC) to a file
     * @param uuid Player UUID
     * @param teamName Name of the team file to save
     * @param slots List of PkmSlot indicating where each PokÃ©mon is located
     */
    public static void saveTeamFromSlots(UUID uuid, String teamName, List<PkmSlot> slots) {
        Teras.LOGGER.info("Guardando equipo personalizado de " + uuid + " en " + teamName + ".dat con " + slots.size() + " slots");

        PlayerPartyStorage partyStorage = StorageProxy.getParty(uuid);
        PCStorage pcStorage = StorageProxy.getPCForPlayer(uuid);

        CompoundNBT nbt = new CompoundNBT();

        for(int i = 0; i < slots.size() && i < 6; i++) {
            PkmSlot slot = slots.get(i);
            Pokemon pokemon = null;

            if(slot.getCaja() == -1) {
                // Get from party
                if(slot.getSlot() >= 0 && slot.getSlot() < 6) {
                    pokemon = partyStorage.get(slot.getSlot());
                }
            } else {
                // Get from PC
                if(slot.getCaja() >= 0 && slot.getCaja() < pcStorage.getBoxCount()) {
                    PCBox box = pcStorage.getBox(slot.getCaja());
                    if(slot.getSlot() >= 0 && slot.getSlot() < PCBox.POKEMON_PER_BOX) {
                        pokemon = box.get(slot.getSlot());
                    }
                }
            }

            if(pokemon != null) {
                CompoundNBT pknbt = new CompoundNBT();
                pokemon.writeToNBT(pknbt);
                nbt.put(i+"", pknbt);
                Teras.LOGGER.info("  - Slot " + i + ": " + pokemon.getSpecies().getName() + " desde caja=" + slot.getCaja() + ", slot=" + slot.getSlot());
            } else {
                Teras.LOGGER.warn("  - Slot " + i + ": No se encontrÃ³ PokÃ©mon en caja=" + slot.getCaja() + ", slot=" + slot.getSlot());
            }
        }

        FileHelper.writeNBT("teras/data/" + uuid + "/" + teamName + ".dat", nbt);
        Teras.LOGGER.info("Equipo guardado exitosamente");
    }

    public static void loadFromSlots(UUID uuid, List<PkmSlot> slots) {
        PlayerPartyStorage storage = StorageProxy.getParty(uuid);

        loadFromSlots(storage, slots);
    }


    public static void loadFromSlots(ServerPlayerEntity player, List<PkmSlot> slots) {
        PlayerPartyStorage storage = StorageProxy.getParty(player.getUUID());

        loadFromSlots(storage, slots);
    }

    public static void loadFromSlots(PlayerPartyStorage storage, List<PkmSlot> slots) {
        List<Pokemon> team = storage.getTeam();

        for(int i = 0; i < 6; i++) {
            if(i >= slots.size()){
                storage.set(i, null);
                continue;
            }
            PkmSlot slot = slots.get(i);
            Pokemon pkm;
            if(slot.getCaja() == -1){
                pkm = team.get(slot.getSlot());
            } else {
                // No se puede cargar de la caja sin el UUID del jugador
                pkm = null;
            }

            storage.set(i, pkm);
        }
    }

    /**
     * Loads PokÃ©mon from slots into party storage, supporting both party and PC sources
     * @param uuid Player UUID
     * @param slots List of PkmSlot indicating where each PokÃ©mon is located
     */
    public static void loadFromSlotsWithPC(UUID uuid, List<PkmSlot> slots) {
        PlayerPartyStorage partyStorage = StorageProxy.getParty(uuid);
        PCStorage pcStorage = StorageProxy.getPCForPlayer(uuid);

        for(int i = 0; i < 6; i++) {
            if(i >= slots.size()){
                partyStorage.set(i, null);
                continue;
            }
            PkmSlot slot = slots.get(i);
            Pokemon pkm = null;

            if(slot.getCaja() == -1){
                // Get from party
                if(slot.getSlot() >= 0 && slot.getSlot() < 6) {
                    pkm = partyStorage.get(slot.getSlot());
                }
            } else {
                // Get from PC
                if(slot.getCaja() >= 0 && slot.getCaja() < pcStorage.getBoxCount()) {
                    PCBox box = pcStorage.getBox(slot.getCaja());
                    if(slot.getSlot() >= 0 && slot.getSlot() < PCBox.POKEMON_PER_BOX) {
                        pkm = box.get(slot.getSlot());
                    }
                }
            }

            partyStorage.set(i, pkm);
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

    /**
     * Updates a single slot in a battle team
     * @param uuid Player UUID
     * @param teamName Name of the team file
     * @param teamSlot The slot position in the team (0-5) to update
     * @param sourceBox The box where the Pokemon is located (-1 for party, >= 0 for PC box)
     * @param sourceSlot The slot within the box/party
     */
    public static void updateTeamSlot(UUID uuid, String teamName, int teamSlot, int sourceBox, int sourceSlot) {
        Teras.LOGGER.info("Actualizando slot " + teamSlot + " del equipo '" + teamName + "' de " + uuid);

        // Get the Pokemon from the specified location
        Pokemon pokemon = null;
        PlayerPartyStorage partyStorage = StorageProxy.getParty(uuid);
        PCStorage pcStorage = StorageProxy.getPCForPlayer(uuid);

        if(sourceBox == -1) {
            // Get from party
            if(sourceSlot >= 0 && sourceSlot < 6) {
                pokemon = partyStorage.get(sourceSlot);
            }
        } else {
            // Get from PC
            if(sourceBox >= 0 && sourceBox < pcStorage.getBoxCount()) {
                PCBox box = pcStorage.getBox(sourceBox);
                if(sourceSlot >= 0 && sourceSlot < PCBox.POKEMON_PER_BOX) {
                    pokemon = box.get(sourceSlot);
                }
            }
        }

        if(pokemon == null) {
            Teras.LOGGER.warn("No se encontrÃ³ PokÃ©mon en caja=" + sourceBox + ", slot=" + sourceSlot);
            return;
        }

        // Read existing team or create new one
        String filePath = "teras/data/" + uuid + "/" + teamName + ".dat";
        CompoundNBT teamNBT;

        if(FileHelper.exists(filePath)) {
            teamNBT = FileHelper.readNBT(filePath);
        } else {
            teamNBT = new CompoundNBT();
            Teras.LOGGER.info("Creando nuevo equipo: " + teamName);
        }

        // Update the specific slot
        CompoundNBT pokemonNBT = new CompoundNBT();
        pokemon.writeToNBT(pokemonNBT);
        teamNBT.put(String.valueOf(teamSlot), pokemonNBT);

        // Save the updated team
        FileHelper.writeNBT(filePath, teamNBT);

        Teras.LOGGER.info("Slot " + teamSlot + " actualizado con " + pokemon.getSpecies().getName() + 
                " desde caja=" + sourceBox + ", slot=" + sourceSlot);
    }

    /**
     * Gets all battle teams for a player
     * @param uuid Player UUID
     * @return Map containing team name as key and team data as value
     */
    public static java.util.Map<String, List<Pokemon>> getAllTeams(UUID uuid) {
        java.util.Map<String, List<Pokemon>> teams = new java.util.HashMap<>();
        
        String playerDataPath = "teras/data/" + uuid;
        java.io.File playerFolder = new java.io.File(playerDataPath);
        
        if(!playerFolder.exists() || !playerFolder.isDirectory()) {
            Teras.LOGGER.info("No se encontrÃ³ carpeta de datos para el jugador " + uuid);
            return teams;
        }
        
        // List all .dat files in the player's folder
        java.io.File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".dat"));
        
        if(files == null || files.length == 0) {
            Teras.LOGGER.info("No se encontraron equipos para el jugador " + uuid);
            return teams;
        }
        
        for(java.io.File file : files) {
            try {
                String teamName = file.getName().replace(".dat", "");
                String filePath = playerDataPath + "/" + file.getName();
                
                CompoundNBT nbt = FileHelper.readNBT(filePath);
                List<Pokemon> team = new ArrayList<>();
                
                // Read all pokemon in the team (up to 6 slots)
                for(int i = 0; i < 6; i++) {
                    if(nbt.contains(String.valueOf(i))) {
                        CompoundNBT pknbt = nbt.getCompound(String.valueOf(i));
                        Pokemon pokemon = PokemonFactory.create(pknbt);
                        if(pokemon != null) {
                            team.add(pokemon);
                        }
                    }
                }
                
                if(!team.isEmpty()) {
                    teams.put(teamName, team);
                    Teras.LOGGER.info("  - Equipo '" + teamName + "' cargado con " + team.size() + " PokÃ©mon");
                }
            } catch (Exception e) {
                Teras.LOGGER.warn("Error leyendo equipo " + file.getName() + ": " + e.getMessage());
            }
        }
        
        Teras.LOGGER.info("Se cargaron " + teams.size() + " equipos para el jugador " + uuid);
        return teams;
    }
}

