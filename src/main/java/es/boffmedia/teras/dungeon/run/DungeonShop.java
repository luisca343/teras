package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.dungeon.model.SeededRng;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Isaac's shop: one pedestal per {@code shopslot} marker, each holding a floating item with its
 * price over it, bought by right-clicking and gone once bought. No restock, no shopkeeper, no
 * menu — which is also what makes it work in adventure mode, where a container UI would be the
 * only other option.
 *
 * <p>The money is the party's ({@link DungeonWallet}) and the effect is the buyer's: whoever
 * clicks drinks the potion or wears the blessing, but everyone paid for it. Map and compass are
 * the exception and reveal for the whole party, because a shared minimap that showed different
 * things to different members would just be confusing.</p>
 *
 * <p>The first slot always stocks a wall-breaker charge. Secret rooms are gated behind charges
 * now, so a shop that happened to roll none would lock a floor's secrets away entirely.</p>
 */
public final class DungeonShop {

    /** One pedestal: what it holds, what it costs, and the displays standing over it. */
    private static final class Slot {
        final BlockPos pos;
        final ShopStock.StockKind kind;
        final int price;
        UUID itemDisplay;
        UUID textDisplay;
        boolean bought;

        Slot(BlockPos pos, ShopStock.StockKind kind, int price) {
            this.pos = pos;
            this.kind = kind;
            this.price = price;
        }
    }

    private final List<Slot> slots = new ArrayList<>();

    /**
     * How close a click has to be to a pedestal to count, in blocks. One rather than two so that
     * two pedestals set side by side can never both match the same click.
     */
    private static final int CLICK_RANGE = 1;

    /** Rolls and spawns the floor's shop. Called once, when the floor's loop is registered. */
    void stock(RunEngine.ActiveFloor floor) {
        slots.clear();
        for (Room room : floor.built().layout().rooms()) {
            if (room.type() != RoomType.SHOP) {
                continue;
            }
            List<BlockPos> pedestals = markerPositions(floor, room);
            if (pedestals.isEmpty()) {
                es.boffmedia.teras.Teras.LOGGER.warn(
                        "Dungeons: {} has no 'shopslot' markers — the shop stands empty. Add them "
                        + "to its template with the room editor.", room);
                continue;
            }
            // Salted by the room's grid position, not its identity: Room has no value hashCode, so
            // an identity hash would re-roll the same floor's stock differently every launch.
            SeededRng rng = new SeededRng(DungeonSeeds.derive(floor.built().layout().baseSeed(),
                    0x53484F50L + room.anchor().x() * 31L + room.anchor().y()));
            List<ShopStock.StockKind> rolled = ShopStock.roll(pedestals.size(), rng,
                    kind -> DungeonsConfig.shopWeight(kind.configKey()));
            int stage = floor.run().stage();
            for (int i = 0; i < pedestals.size(); i++) {
                BlockPos pos = floor.built().clampInside(room, pedestals.get(i), 1);
                Slot slot = new Slot(pos, rolled.get(i), priceOf(rolled.get(i), stage));
                slots.add(slot);
                spawnDisplays(floor.level(), slot);
            }
        }
    }

    /** Prices climb with the floor by the same rate coin drops do, so the shop keeps its bite. */
    private static int priceOf(ShopStock.StockKind kind, int stage) {
        return CoinDrops.scaleToStage(DungeonsConfig.shopPrice(kind.configKey()), stage);
    }

    /**
     * A click near a live pedestal buys it. Returns true when the click was the shop's business —
     * including a refused purchase, which must still swallow the interaction rather than fall
     * through to the secret-wall check behind it.
     */
    boolean tryBuy(RunEngine.ActiveFloor floor, ServerPlayer player, BlockPos clicked) {
        for (Slot slot : slots) {
            if (slot.bought || !RunEngine.isAtFixture(slot.pos, clicked, CLICK_RANGE)) {
                continue;
            }
            if (!floor.run().wallet().trySpend(slot.price)) {
                player.displayClientMessage(Component.literal("§cNecesitas " + slot.price
                        + " monedas — tienes " + floor.run().wallet().coins() + "."), true);
                RunEngine.playAt(floor, slot.pos, DungeonSound.PURCHASE_DENIED, 1.0f);
                return true;
            }
            slot.bought = true;
            despawn(floor.level(), slot);
            apply(floor, player, slot.kind);
            RunEngine.playAt(floor, slot.pos, DungeonSound.PURCHASE, 1.2f);
            RunEngine.broadcastWallet(floor);
            return true;
        }
        return false;
    }

    /** What the buyer walks away with. Party-wide for the two map items, personal for the rest. */
    private void apply(RunEngine.ActiveFloor floor, ServerPlayer player, ShopStock.StockKind kind) {
        switch (kind) {
            case POCION -> give(player, new ItemStack(ItemInit.POCION_VITAL.get()));
            case POCION_MAYOR -> give(player, new ItemStack(ItemInit.POCION_VITAL_MAYOR.get()));
            case MAPA -> {
                floor.mapRevealed = true;
                RunEngine.syncMapFor(floor);
                RunEngine.message(floor, "§b" + player.getName().getString()
                        + " ha comprado el mapa del piso.");
            }
            case BRUJULA -> {
                floor.compassRevealed = true;
                RunEngine.syncMapFor(floor);
                RunEngine.message(floor, "§b" + player.getName().getString()
                        + " ha comprado la brújula — las salas especiales están marcadas.");
            }
            case ROMPEMUROS -> {
                floor.run().wallet().addCharges(1);
                player.displayClientMessage(
                        Component.literal("§bCarga rompemuros al fondo común."), true);
            }
            case BENDICION_FUERZA -> bless(player, MobEffects.DAMAGE_BOOST, "§cFuerza");
            case BENDICION_RESISTENCIA -> bless(player, MobEffects.DAMAGE_RESISTANCE, "§9Resistencia");
            case BENDICION_VELOCIDAD -> bless(player, MobEffects.MOVEMENT_SPEED, "§fVelocidad");
            case FENIX -> {
                floor.run().stateOf(player.getUUID()).grantPhoenix();
                player.displayClientMessage(
                        Component.literal("§6El amuleto fénix te salvará una vez."), true);
            }
            case SEGURO -> {
                floor.seguro = true;
                RunEngine.message(floor, "§eSeguro contratado — este piso la muerte cuesta la mitad.");
            }
            case CAJA_SORPRESA -> RunEngine.rollLootAt(floor, player.blockPosition(),
                    DungeonsConfig.treasureLootTable());
        }
    }

    /**
     * Blessings last the floor, not the run: they are cleared on the descent, which is what keeps
     * a stack of them from turning the last stages into a walkover.
     */
    private static void bless(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                              String label) {
        player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0,
                false, true, true));
        player.displayClientMessage(Component.literal(label + " §7hasta el próximo piso."), true);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Wipes the floor's blessings; called on the descent so each floor is bought fresh, and on the
     * way out of a run.
     *
     * <p>Only infinite-duration instances are removed, which is how {@link #bless} applies them. A
     * blanket {@code removeEffect} would also strip a timed strength potion the player brought
     * from outside — the run has no business confiscating that.</p>
     */
    public static void clearBlessings(ServerPlayer player) {
        for (var effect : List.of(MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE,
                MobEffects.MOVEMENT_SPEED)) {
            MobEffectInstance active = player.getEffect(effect);
            if (active != null && active.isInfiniteDuration()) {
                player.removeEffect(effect);
            }
        }
    }

    private void spawnDisplays(ServerLevel level, Slot slot) {
        Entity item = DungeonDisplays.spawnItem(level, slot.pos, 1.2, iconOf(slot.kind));
        Entity text = DungeonDisplays.spawnText(level, slot.pos, 1.9,
                Component.literal(labelOf(slot.kind) + " §e" + slot.price + "⛁"));
        slot.itemDisplay = item == null ? null : item.getUUID();
        slot.textDisplay = text == null ? null : text.getUUID();
    }

    private void despawn(ServerLevel level, Slot slot) {
        DungeonDisplays.discard(level, slot.itemDisplay);
        DungeonDisplays.discard(level, slot.textDisplay);
        slot.itemDisplay = null;
        slot.textDisplay = null;
    }

    /** Every display this shop put up, for the window between a stage advance and the teardown. */
    void despawnDisplays(RunEngine.ActiveFloor floor) {
        for (Slot slot : slots) {
            despawn(floor.level(), slot);
        }
        slots.clear();
    }

    private static ItemStack iconOf(ShopStock.StockKind kind) {
        return switch (kind) {
            case POCION -> new ItemStack(ItemInit.POCION_VITAL.get());
            case POCION_MAYOR -> new ItemStack(ItemInit.POCION_VITAL_MAYOR.get());
            case MAPA -> new ItemStack(net.minecraft.world.item.Items.FILLED_MAP);
            case BRUJULA -> new ItemStack(net.minecraft.world.item.Items.COMPASS);
            case ROMPEMUROS -> new ItemStack(ItemInit.CARGA_ROMPEMUROS.get());
            case BENDICION_FUERZA -> new ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER);
            case BENDICION_RESISTENCIA -> new ItemStack(net.minecraft.world.item.Items.SHIELD);
            case BENDICION_VELOCIDAD -> new ItemStack(net.minecraft.world.item.Items.SUGAR);
            case FENIX -> new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
            case SEGURO -> new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT);
            case CAJA_SORPRESA -> new ItemStack(net.minecraft.world.item.Items.CHEST);
        };
    }

    private static String labelOf(ShopStock.StockKind kind) {
        return switch (kind) {
            case POCION -> "§aPoción";
            case POCION_MAYOR -> "§aPoción mayor";
            case MAPA -> "§bMapa";
            case BRUJULA -> "§bBrújula";
            case ROMPEMUROS -> "§7Rompemuros";
            case BENDICION_FUERZA -> "§cFuerza";
            case BENDICION_RESISTENCIA -> "§9Resistencia";
            case BENDICION_VELOCIDAD -> "§fVelocidad";
            case FENIX -> "§6Fénix";
            case SEGURO -> "§eSeguro";
            case CAJA_SORPRESA -> "§dCaja sorpresa";
        };
    }

    private static List<BlockPos> markerPositions(RunEngine.ActiveFloor floor, Room room) {
        List<BlockPos> positions = new ArrayList<>();
        for (TemplateMarkers.Marker marker : floor.built().markers().getOrDefault(room, List.of())) {
            if (marker.kind().equals("shopslot")) {
                positions.add(marker.pos());
            }
        }
        return positions;
    }
}
