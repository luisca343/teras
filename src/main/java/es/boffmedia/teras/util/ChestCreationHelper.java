package es.boffmedia.teras.util;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects.legacy.ObjetoMC;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ChestCreationHelper {

    private static final int MAX_TOTAL_ITEMS = 27 * 10; // up to 10 chests' worth
    private static final int MAX_ITEM_COUNT = 64;        // a vanilla stack

    /**
     * Drops items whose id is not a registered item and clamps counts to [1, MAX_ITEM_COUNT],
     * capping the total at MAX_TOTAL_ITEMS. Protects against forged/oversized DarCaja payloads.
     */
    private static ArrayList<ObjetoMC> sanitize(ArrayList<ObjetoMC> objetos) {
        ArrayList<ObjetoMC> clean = new ArrayList<>();
        for (ObjetoMC item : objetos) {
            if (clean.size() >= MAX_TOTAL_ITEMS) {
                Teras.LOGGER.warn("DarCaja payload exceeded {} items; truncating", MAX_TOTAL_ITEMS);
                break;
            }
            if (item == null || item.getId() == null) {
                continue;
            }
            ResourceLocation id;
            try {
                id = new ResourceLocation(item.getId());
            } catch (Exception e) {
                Teras.LOGGER.warn("DarCaja: skipping malformed item id '{}'", item.getId());
                continue;
            }
            if (!ForgeRegistries.ITEMS.containsKey(id)) {
                Teras.LOGGER.warn("DarCaja: skipping unknown item id '{}'", item.getId());
                continue;
            }
            int count = Math.max(1, Math.min(MAX_ITEM_COUNT, item.getCantidad()));
            clean.add(new ObjetoMC(item.getId(), count));
        }
        return clean;
    }

    /**
     * Creates chest items containing the provided objects and gives them to the player identified by UUID
     * @param uuidString String representation of the player's UUID
     * @param objetos List of objects to put in chests
     */
    public static void createAndGiveChests(String uuidString, ArrayList<ObjetoMC> objetos) {
        if (uuidString == null || uuidString.isEmpty() || objetos == null || objetos.isEmpty()) {
            Teras.LOGGER.warn("Invalid UUID or empty object list provided to createAndGiveChests");
            return;
        }

        try {
            UUID uuid = UUID.fromString(uuidString);
            
            // Get server instance
            MinecraftServer server = LogicalSidedProvider.INSTANCE.get(LogicalSide.SERVER);
            if (server == null) {
                Teras.LOGGER.error("Failed to get server instance when creating chests for UUID: " + uuidString);
                return;
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
                createAndGiveChests(player, objetos);
            } else {
                Teras.LOGGER.warn("Player with UUID {} not found or not online. Cannot give chests.", uuidString);
                // Could implement storage of chests for later delivery when player logs in
            }
        } catch (IllegalArgumentException e) {
            Teras.LOGGER.error("Invalid UUID format: " + uuidString, e);
        }
    }
    /**
     * Creates chest items containing the provided objects and gives them to the player
     * @param player The player to receive the chests
     * @param objetos List of objects to put in chests
     */
    public static void createAndGiveChests(ServerPlayerEntity player, ArrayList<ObjetoMC> objetos) {
        if (player == null || objetos == null || objetos.isEmpty()) {
            return;
        }

        objetos = sanitize(objetos);
        if (objetos.isEmpty()) {
            return;
        }

        // Split into groups of 27 items (chest size)
        int chunkSize = 27;
        AtomicInteger counter = new AtomicInteger();
        final Collection<List<ObjetoMC>> cajas = objetos.stream()
            .collect(Collectors.groupingBy(i -> counter.getAndIncrement() / chunkSize))
            .values();

        // Create and spawn chests
        for (List<ObjetoMC> caja : cajas) {
            ItemStack chest = createChestWithItems(caja);
            spawnItemForPlayer(player, chest);
        }
    }

    /**
     * Creates a chest ItemStack with the given items inside
     * @param items List of items to put in the chest
     * @return ItemStack representing the chest
     */
    private static ItemStack createChestWithItems(List<ObjetoMC> items) {
        ItemStack chest = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "chest")));
        ListNBT itemsList = new ListNBT();

        // Add each item to the NBT list
        for (int i = 0; i < items.size(); i++) {
            CompoundNBT itemNBT = new CompoundNBT();
            ObjetoMC item = items.get(i);
            itemNBT.putInt("Slot", i);
            itemNBT.putString("id", item.getId());
            itemNBT.putInt("Count", item.getCantidad());
            itemsList.add(i, itemNBT.copy());
        }

        // Set chest BlockEntityTag and display name
        CompoundNBT blockEntityTag = new CompoundNBT();
        blockEntityTag.put("Items", itemsList.copy());

        CompoundNBT display = new CompoundNBT();
        display.putString("Name", "{\"text\":\"Paquete\", \"color\": \"aqua\"}");

        CompoundNBT nbt = chest.getOrCreateTag();
        nbt.put("BlockEntityTag", blockEntityTag);
        nbt.put("display", display);

        return chest;
    }

    /**
     * Spawns an item at the player's location
     * @param player The player to spawn the item for
     * @param itemStack The item to spawn
     */
    private static void spawnItemForPlayer(ServerPlayerEntity player, ItemStack itemStack) {
        World world = player.level;
        ItemEntity itemEntity = new ItemEntity(
            world, 
            player.getX(), 
            player.getY(), 
            player.getZ(), 
            itemStack
        );
        itemEntity.setPickUpDelay(0);
        world.addFreshEntity(itemEntity);
    }
}
