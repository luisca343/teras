package es.boffmedia.teras.util;

import com.pixelmonmod.api.pokemon.PokemonSpecification;
import com.pixelmonmod.api.pokemon.PokemonSpecificationProxy;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;

import java.util.UUID;

public class PokemonHelper {

    /**
     * Creates and gives a Pokémon to a player based on the provided PokeSpec string
     * @param player The player to receive the Pokémon
     * @param pokespecString The PokeSpec string describing the Pokémon
     * @param sendMessage Whether to send a message to the player
     * @return true if successful, false otherwise
     */
    public static boolean givePokemonToPlayer(ServerPlayerEntity player, String pokespecString, boolean sendMessage) {
        if (player == null || pokespecString == null || pokespecString.isEmpty()) {
            Teras.LOGGER.warn("Invalid player or PokeSpec string provided to givePokemonToPlayer");
            return false;
        }
        
        try {
            PokemonSpecification spec = PokemonSpecificationProxy.create(pokespecString);
            Pokemon pokemon = PokemonFactory.create(spec);
            if (pokemon == null) {
                Teras.LOGGER.error("Failed to create Pokemon from spec: {}", pokespecString);
                return false;
            }
            
            // Get the player's storage
            PlayerPartyStorage party = StorageProxy.getParty(player.getUUID());
            boolean success;
            
            // Try to add to party first
            if (party.add(pokemon)) {
                success = true;
            } else {
                // If party is full, add to PC
                PCStorage pc = StorageProxy.getPCForPlayer(player.getUUID());
                success = pc.add(pokemon);
            }
            
            if (success && sendMessage) {
                MessageHelper.enviarMensaje(
                    player,
                    Teras.HEADER_MENSAJE + "Has recibido " + TextFormatting.YELLOW + pokemon.getSpecies().getName() + TextFormatting.BLUE + "!");
            }
            
            return success;
        } catch (Exception e) {
            Teras.LOGGER.error("Error giving Pokemon to player {}: {}", player.getName().getString(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Creates and gives a Pokémon to a player identified by UUID based on the provided PokeSpec string
     * @param uuidString String representation of the player's UUID
     * @param pokespecString The PokeSpec string describing the Pokémon
     * @param sendMessage Whether to send a message to the player
     * @return true if successful, false otherwise
     */
    public static boolean givePokemonToPlayerByUUID(String uuidString, String pokespecString, boolean sendMessage) {
        if (uuidString == null || uuidString.isEmpty() || pokespecString == null || pokespecString.isEmpty()) {
            Teras.LOGGER.warn("Invalid UUID or PokeSpec string provided to givePokemonToPlayerByUUID");
            return false;
        }
        
        try {
            UUID uuid = UUID.fromString(uuidString);
            
            // Get server instance
            MinecraftServer server = LogicalSidedProvider.INSTANCE.get(LogicalSide.SERVER);
            if (server == null) {
                Teras.LOGGER.error("Failed to get server instance when giving Pokémon for UUID: {}", uuidString);
                return false;
            }
            
            // Try to find the player across all server worlds
            ServerPlayerEntity player = null;
            for (ServerWorld world : server.getAllLevels()) {
                player = (ServerPlayerEntity) world.getPlayerByUUID(uuid);
                if (player != null) {
                    break;
                }
            }
            
            if (player != null) {
                // Player found, use the existing method
                return givePokemonToPlayer(player, pokespecString, sendMessage);
            } else {
                Teras.LOGGER.warn("Player with UUID {} not found or not online. Cannot give Pokémon.", uuidString);
                // Could implement storage of Pokémon for later delivery when player logs in
                return false;
            }
        } catch (IllegalArgumentException e) {
            Teras.LOGGER.error("Invalid UUID format: {}", uuidString, e);
            return false;
        }
    }
    
    /**
     * Creates and gives multiple Pokémon to a player based on provided PokeSpec strings
     * @param player The player to receive the Pokémon
     * @param pokespecStrings Array of PokeSpec strings describing the Pokémon
     * @param sendMessage Whether to send messages to the player
     * @return The number of successfully given Pokémon
     */
    public static int giveMultiplePokemonToPlayer(ServerPlayerEntity player, String[] pokespecStrings, boolean sendMessage) {
        if (player == null || pokespecStrings == null || pokespecStrings.length == 0) {
            return 0;
        }
        
        int successCount = 0;
        for (String pokespecString : pokespecStrings) {
            if (givePokemonToPlayer(player, pokespecString, sendMessage)) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * Validates if a PokeSpec string can create a valid Pokémon
     * @param pokespecString The PokeSpec string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidPokeSpec(String pokespecString) {
        if (pokespecString == null || pokespecString.isEmpty()) {
            return false;
        }
        
        try {
            PokemonSpecification spec = PokemonSpecificationProxy.create(pokespecString);
            return spec != null;
        } catch (Exception e) {
            Teras.LOGGER.error("Error validating PokeSpec: {}", pokespecString, e);
            return false;
        }
    }
}