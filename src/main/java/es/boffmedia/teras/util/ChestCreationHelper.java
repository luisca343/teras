package es.boffmedia.teras.util;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.model.world.ObjetoMC;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Packs a darCaja grant into named chest items and gives them to a player. Port of the 1.16.5
 * {@code util.ChestCreationHelper}.
 *
 * <p>1.16.5 wrote the contents as a {@code BlockEntityTag} NBT compound. 1.21.1 carries them as
 * {@link DataComponents#CONTAINER}, which {@code BaseContainerBlockEntity} copies into the chest when
 * it is placed — so the chest still arrives full.</p>
 *
 * <p>Entries are validated for shape only. <b>Entitlement is the backend's call</b>: 1.16.5 took the
 * item list from the client and granted it, so any modified client could mint items. Nothing here
 * re-establishes that trust — the caller must have obtained this list from the backend.</p>
 */
public final class ChestCreationHelper {
    private ChestCreationHelper() {}

    /** Slots in one chest; a grant spanning more is split across several. */
    private static final int CHEST_SLOTS = 27;

    /** Ten chests' worth — a cap on how much one grant can spawn. */
    private static final int MAX_TOTAL_ITEMS = CHEST_SLOTS * 10;

    private static final int MAX_ITEM_COUNT = 64;

    /**
     * Gives the chests to {@code uuidString} if that player is online, and logs a warning if not.
     *
     * <p>Resolving by uuid rather than holding a {@link ServerPlayer} is what the async grant path
     * needs: the backend call happens off-thread, and the player may be gone by the time it answers.
     * A grant lost that way is spent backend-side and is not redelivered — see {@code §7} of the
     * darCaja spec.</p>
     */
    public static void createAndGiveChests(String uuidString, List<ObjetoMC> objetos) {
        if (uuidString == null || uuidString.isEmpty() || objetos == null || objetos.isEmpty()) {
            Teras.LOGGER.warn("DarCaja: invalid uuid or empty object list");
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            Teras.LOGGER.error("DarCaja: invalid uuid '{}'", uuidString);
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            Teras.LOGGER.error("DarCaja: no server instance; cannot give chests to {}", uuid);
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            Teras.LOGGER.warn("DarCaja: player {} is offline; {} item(s) not delivered", uuid, objetos.size());
            return;
        }
        createAndGiveChests(player, objetos);
    }

    /** Gives {@code player} the chests holding {@code objetos}, silently doing nothing if none resolve. */
    public static void createAndGiveChests(ServerPlayer player, List<ObjetoMC> objetos) {
        if (player == null || objetos == null || objetos.isEmpty()) {
            return;
        }
        List<ItemStack> stacks = toStacks(objetos);
        if (stacks.isEmpty()) {
            return;
        }
        for (int from = 0; from < stacks.size(); from += CHEST_SLOTS) {
            List<ItemStack> caja = stacks.subList(from, Math.min(from + CHEST_SLOTS, stacks.size()));
            ItemHandlerHelper.giveItemToPlayer(player, createChestWithItems(caja));
        }
    }

    /**
     * Resolves each entry to a stack, dropping unregistered ids and clamping counts to
     * [1, {@value #MAX_ITEM_COUNT}], with the total capped at {@value #MAX_TOTAL_ITEMS} entries.
     */
    private static List<ItemStack> toStacks(List<ObjetoMC> objetos) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ObjetoMC objeto : objetos) {
            if (stacks.size() >= MAX_TOTAL_ITEMS) {
                Teras.LOGGER.warn("DarCaja payload exceeded {} items; truncating", MAX_TOTAL_ITEMS);
                break;
            }
            if (objeto == null || objeto.id() == null) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(objeto.id());
            if (id == null) {
                Teras.LOGGER.warn("DarCaja: skipping malformed item id '{}'", objeto.id());
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
                Teras.LOGGER.warn("DarCaja: skipping unknown item id '{}'", objeto.id());
                continue;
            }
            int count = Math.max(1, Math.min(MAX_ITEM_COUNT, objeto.cantidad()));
            stacks.add(new ItemStack(item, count));
        }
        return stacks;
    }

    /** A chest holding {@code items}, named so the player can tell it apart from a plain chest. */
    private static ItemStack createChestWithItems(List<ItemStack> items) {
        ItemStack chest = new ItemStack(Items.CHEST);
        chest.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        chest.set(DataComponents.CUSTOM_NAME,
                Component.literal("Paquete").withStyle(ChatFormatting.AQUA));
        return chest;
    }
}
