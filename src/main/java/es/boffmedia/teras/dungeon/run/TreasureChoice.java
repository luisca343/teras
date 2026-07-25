package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The treasure room as a <b>visible choice</b> (PISOS §66): three stands, each showing a real
 * reward, and <b>each player takes one</b>.
 *
 * <h2>Why this replaces the dispenser</h2>
 *
 * <p>The old treasure room was a single pedestal, armed {@code PER_PLAYER}, that rolled a hidden
 * table when clicked and handed everyone the same faucet. It asked nothing — no visibility, no
 * opportunity cost, no framing — which is the weakest form the genre has. This asks the question
 * every good treasure room asks: <i>which one?</i></p>
 *
 * <h2>The three archetypes, and why the choice is real here</h2>
 *
 * <p>A "choose one of three" is only a real choice if no option is strictly best. In solo Isaac that
 * is hard; here it is free, because the game already runs on three separate pressures and different
 * runs feel each one differently — so the stands are the fighter's, the survivor's and the
 * merchant's pick:</p>
 *
 * <ul>
 *   <li><b>arma</b> — a gear piece: best when the party is under-equipped;</li>
 *   <li><b>vitalidad</b> — potions: best when it is hurt, or hearts are locked down;</li>
 *   <li><b>provisión</b> — coins and a charge to the shared purse: best when saving for the shop,
 *       the Acreedor, or a walled secret.</li>
 * </ul>
 *
 * <h2>Co-op-healthy by construction</h2>
 *
 * <p>Each bundle is rolled <b>once, at arm</b>, and shown; every player who picks that stand gets a
 * copy. The stands <b>do not deplete</b>, so the opportunity cost is personal ("I wanted both, I
 * chose one") and never interpersonal ("you grabbed it, I got nothing") — no loot race, which is the
 * thing that sours contested loot among friends. A player picks exactly once per room.</p>
 */
public final class TreasureChoice {

    /** How close a click has to be to a stand to count — the pedestals' shared reach. */
    private static final int RANGE = 2;

    /** Which pressure a stand answers. Assigned from the marker's argument, or by order. */
    public enum Archetype { ARMA, VITALIDAD, PROVISION }

    /** One stand: its reward rolled once and shown, so the party chooses among visible things. */
    private static final class Stand {
        final Archetype archetype;
        final BlockPos pos;
        /** Pre-rolled item bundle for ARMA/VITALIDAD; empty for PROVISION, which pays the purse. */
        final List<ItemStack> reserved = new ArrayList<>();
        final int coins;
        final int charges;
        UUID itemDisplay;
        UUID textDisplay;

        Stand(Archetype archetype, BlockPos pos, int coins, int charges) {
            this.archetype = archetype;
            this.pos = pos;
            this.coins = coins;
            this.charges = charges;
        }
    }

    private final Map<Room, List<Stand>> byRoom = new LinkedHashMap<>();
    /** Who has already taken their one pick, per room. */
    private final Map<Room, Set<UUID>> picked = new LinkedHashMap<>();

    /**
     * Stands the three rewards up in {@code room}, one per {@code loot} marker. The marker's argument
     * ({@code loot:arma}) names the archetype; without one they fall in the fixed order arma,
     * vitalidad, provisión, so an older single-marker template still stands up one arma pick rather
     * than breaking.
     */
    public void arm(RunEngine.ActiveFloor floor, Room room) {
        if (byRoom.containsKey(room)) {
            return;
        }
        List<TemplateMarkers.Marker> markers = new ArrayList<>();
        for (TemplateMarkers.Marker marker : floor.built().markers().getOrDefault(room, List.of())) {
            if (marker.kind().equals("loot")) {
                markers.add(marker);
            }
        }
        if (markers.isEmpty()) {
            return;
        }
        List<Stand> stands = new ArrayList<>();
        int stage = floor.run().stage();
        for (int i = 0; i < markers.size(); i++) {
            TemplateMarkers.Marker marker = markers.get(i);
            Archetype archetype = archetypeOf(marker.argument(), i);
            BlockPos pos = floor.built().clampInside(room, marker.pos(), 1);
            int coins = archetype == Archetype.PROVISION
                    ? CoinDrops.scaleToStage(DungeonsConfig.treasureProvisionCoins(), stage) : 0;
            int charges = archetype == Archetype.PROVISION
                    ? DungeonsConfig.treasureProvisionCharges() : 0;
            Stand stand = new Stand(archetype, pos, coins, charges);
            if (archetype == Archetype.ARMA) {
                stand.reserved.addAll(RunEngine.rollLoot(floor, DungeonsConfig.treasureArmaLootTable()));
            } else if (archetype == Archetype.VITALIDAD) {
                stand.reserved.addAll(
                        RunEngine.rollLoot(floor, DungeonsConfig.treasureVitalidadLootTable()));
            }
            stands.add(stand);
            show(floor, stand);
        }
        byRoom.put(room, stands);
        picked.put(room, new LinkedHashSet<>());
    }

    private static Archetype archetypeOf(String argument, int index) {
        return switch (argument) {
            case "arma" -> Archetype.ARMA;
            case "vitalidad" -> Archetype.VITALIDAD;
            case "provision", "provisión" -> Archetype.PROVISION;
            // No argument: fixed order, so a three-marker template needs no qualifiers and a
            // single-marker one lands on arma.
            default -> Archetype.values()[index % Archetype.values().length];
        };
    }

    /**
     * Takes a stand's reward if the click was at one, and the clicker has a pick left.
     *
     * @return whether the click was this system's business, so the caller can cancel the event
     */
    public boolean tryClaim(RunEngine.ActiveFloor floor, ServerPlayer player, BlockPos clicked) {
        for (Map.Entry<Room, List<Stand>> entry : byRoom.entrySet()) {
            for (Stand stand : entry.getValue()) {
                if (!RunEngine.isAtFixture(stand.pos, clicked, RANGE)) {
                    continue;
                }
                take(floor, player, entry.getKey(), stand);
                return true;
            }
        }
        return false;
    }

    private void take(RunEngine.ActiveFloor floor, ServerPlayer player, Room room, Stand stand) {
        if (!floor.run().party().containsKey(player.getUUID())) {
            return;
        }
        Set<UUID> roomPicked = picked.get(room);
        if (roomPicked != null && roomPicked.contains(player.getUUID())) {
            player.displayClientMessage(Component.literal(
                    "§8Ya te has llevado tu recompensa de esta sala."), true);
            return;
        }
        switch (stand.archetype) {
            case ARMA, VITALIDAD -> {
                for (ItemStack held : stand.reserved) {
                    RunEngine.ejectTo(floor, player, stand.pos, held.copy());
                }
            }
            case PROVISION -> {
                floor.run().wallet().add(stand.coins);
                floor.run().wallet().addCharges(stand.charges);
                RunEngine.broadcastWallet(floor);
            }
        }
        if (roomPicked != null) {
            roomPicked.add(player.getUUID());
        }
        RunEngine.playAt(floor, stand.pos, DungeonSound.PURCHASE, 1.0f);
        player.displayClientMessage(Component.literal(
                "§7Has elegido §f" + name(stand.archetype) + "§7."), true);
        refresh(floor, room);
    }

    /** Clears every stand's displays, on the floor teardown and on a stage advance. */
    public void despawn(RunEngine.ActiveFloor floor) {
        for (List<Stand> stands : byRoom.values()) {
            for (Stand stand : stands) {
                hide(floor.level(), stand);
            }
        }
        byRoom.clear();
        picked.clear();
    }

    /** Once the whole party has chosen, the stands go quiet — otherwise they keep offering. */
    private void refresh(RunEngine.ActiveFloor floor, Room room) {
        Set<UUID> roomPicked = picked.get(room);
        boolean spent = roomPicked != null
                && roomPicked.size() >= Math.max(1, floor.run().party().size());
        for (Stand stand : byRoom.getOrDefault(room, List.of())) {
            hide(floor.level(), stand);
            if (spent) {
                var text = DungeonDisplays.spawnText(floor.level(), stand.pos, 1.9,
                        Component.literal("§8Vacío"));
                stand.textDisplay = text == null ? null : text.getUUID();
            } else {
                show(floor, stand);
            }
        }
    }

    private void show(RunEngine.ActiveFloor floor, Stand stand) {
        var item = DungeonDisplays.spawnItem(floor.level(), stand.pos, 1.2, iconOf(stand));
        var text = DungeonDisplays.spawnText(floor.level(), stand.pos, 1.9, label(stand));
        stand.itemDisplay = item == null ? null : item.getUUID();
        stand.textDisplay = text == null ? null : text.getUUID();
    }

    private void hide(ServerLevel level, Stand stand) {
        DungeonDisplays.discard(level, stand.itemDisplay);
        DungeonDisplays.discard(level, stand.textDisplay);
        // By tag as well, because an id lookup answers null in an unloaded chunk — the blindness that
        // once left shop pedestals standing on later floors.
        DungeonDisplays.sweep(level, stand.pos, 3);
        stand.itemDisplay = null;
        stand.textDisplay = null;
    }

    private static ItemStack iconOf(Stand stand) {
        return switch (stand.archetype) {
            case ARMA -> stand.reserved.isEmpty()
                    ? new ItemStack(Items.IRON_SWORD) : stand.reserved.get(0).copy();
            case VITALIDAD -> stand.reserved.isEmpty()
                    ? new ItemStack(ItemInit.POCION_VITAL_MAYOR.get()) : stand.reserved.get(0).copy();
            case PROVISION -> new ItemStack(ItemInit.MONEDA_MAZMORRA.get());
        };
    }

    private static Component label(Stand stand) {
        return switch (stand.archetype) {
            case ARMA -> Component.literal("§bArma §7· cada uno elige una");
            case VITALIDAD -> Component.literal("§aVitalidad §7· cada uno elige una");
            case PROVISION -> Component.literal("§eProvisión §7· §f" + stand.coins + "⛁"
                    + (stand.charges > 0 ? " §7+ " + stand.charges + " carga" : ""));
        };
    }

    private static String name(Archetype archetype) {
        return switch (archetype) {
            case ARMA -> "el arma";
            case VITALIDAD -> "la vitalidad";
            case PROVISION -> "la provisión";
        };
    }

    /** Diagnostic, for the debug command. */
    public int size() {
        return byRoom.size();
    }
}
