package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Pokémon are not part of a dungeon run, so inside one they simply are not there.
 *
 * <h2>Why this is a loadout rule and not a combat rule</h2>
 *
 * <p>It lives in {@code run} rather than {@code combat}, and it is <b>not</b> gated on
 * {@code combate.activado}, because it answers ROGUELIKE ruling 10 — the entry loadout — and not
 * ruling 1. A party can otherwise send a Pokémon out, ride it, and use field moves in a place whose
 * difficulty, movement and encounters were all designed around a player on foot with dungeon gear.
 * That is true whether Teras or Minecraft is doing the arithmetic, so handing damage back to vanilla
 * must not also hand the dungeon a mount.</p>
 *
 * <p>It was briefly in {@code combat} because the stat panel colliding with Pixelmon's HUD is what
 * surfaced it. Moving the panel would have fixed only the pixels.</p>
 *
 * <h2>Why it names no Pixelmon class</h2>
 *
 * <p>Pixelmon is {@code compileOnly} and absent from many servers, so everything here works off
 * <b>registry namespaces</b> — an item or entity id beginning {@code pixelmon:}. That needs no import,
 * cannot {@code NoClassDefFoundError}, and costs a string comparison. It also means a server running
 * Cobblemon instead gets the same treatment from the same list.</p>
 *
 * <h2>What the entity backstop cannot do</h2>
 *
 * <p>{@link #onRightClickItem} and its two siblings are the real defence: they refuse the <i>use</i>,
 * which is how a Pokémon is sent out, mounted or asked for a field move, and they run before anything
 * exists. {@link #onEntityJoin} is the backstop, and it is a blunt one — it <b>cancels the join</b>,
 * which is the only tool a namespace-only integration has. Recalling would be the correct move and
 * needs their API, so a Pokémon already out when its trainer crosses into the dungeon is deleted while
 * Pixelmon's own party state may still believe it is deployed. That cost is accepted because the
 * alternative is a Pokémon loose on the floor; if it ever bites, the fix is to refuse entry at the door
 * rather than to soften the guard here.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DungeonPokemonGuard {
    private DungeonPokemonGuard() {}

    /** Namespaces whose content has no business inside a run. */
    private static final String[] BLOCKED = {"pixelmon", "cobblemon"};

    /**
     * Stops a Pokémon from being sent out, ridden, or asked for a field move.
     *
     * <p>All three go through using the item, so refusing the use covers them without this having to
     * know what any of them are.</p>
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        refuse(event);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        refuse(event);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        refuse(event);
    }

    private static void refuse(PlayerInteractEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DungeonHealth.isInRun(player)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !blocked(BuiltInRegistries.ITEM.getKey(held.getItem()))) {
            return;
        }
        if (event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
        player.displayClientMessage(
                Component.translatable("message.teras.dungeon_no_pokemon"), true);
    }

    /**
     * Keeps Pokémon off the floor entirely.
     *
     * <p>{@link EventPriority#HIGH} so this lands before {@code InstanceGuard}'s un-cancel pass, which
     * runs at {@code LOWEST} to rescue our own enemies from Pixelmon's spawn replacement. The two do
     * not fight: our enemies carry the dungeon tag and a {@code pixelmon:} entity never does — but the
     * ordering is worth stating, because that replacement is exactly the mechanism that would otherwise
     * put a Pokémon on the floor without anyone throwing one.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().location().toString().equals(DungeonsConfig.dimension())) {
            return;
        }
        Entity entity = event.getEntity();
        if (blocked(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            event.setCanceled(true);
        }
    }

    private static boolean blocked(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        for (String namespace : BLOCKED) {
            if (namespace.equals(id.getNamespace())) {
                return true;
            }
        }
        return false;
    }
}
