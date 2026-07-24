package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.gen.SatelliteOdds;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * What la Orden gives, at the font on the sello's far flank — the other half of the fork
 * {@link DevilDeal} opens (PISOS §63b–§63e).
 *
 * <h2>Why it is a pick and not a payout</h2>
 *
 * <p>The devil pays in epic gear. A restoration-only Orden would therefore be strictly weaker at
 * every moment, and a moral fork whose virtuous side is always the worse deal is not a choice, it is
 * a trap with a nice name — §63e caught exactly that before any of this was built. So her gift is a
 * <b>pick from a pool</b>, it contains a relic of her own, and how cleanly the floor was played
 * decides how many options and how many picks.</p>
 *
 * <p>§63e's resolution was that this pool <i>is</i> the "rare shrine room" source of PRODUCCION
 * §7.1's bendiciones. That system does not exist yet; when it does, these entries become its rows
 * and this class hands off to it. Until then the pick is her own, which is the same offer with a
 * shorter reach.</p>
 *
 * <h2>The trio stays intact</h2>
 *
 * <p>PRODUCCION §4.1's scarcity trio — monedas, petardos, llaves — is never substitutable, and it is
 * an invariant enforced in review rather than in code. <b>Neither NPC may ever trade in llaves or
 * petardos.</b> Everything below is health, gear, coins or absolution.</p>
 */
public final class OrdenGift {
    private OrdenGift() {}

    private static final int RANGE = 2;

    /**
     * The id an authored dialogue writes for her first slot. Resolves to Absolución or Purificación
     * by the run's own state — see {@link #take}.
     */
    public static final String MERCY_ALIAS = "misericordia";

    /** This floor's tier, from the purity of the floor the party played to get here. */
    public static SatelliteOdds.Tier tierOf(RunEngine.ActiveFloor floor) {
        return SatelliteOdds.tierOf(SatelliteOdds.purity(floor.run().lastFloorOutcome()));
    }

    /**
     * Whether the fork closed on the <i>other</i> side. {@code forkResolved} is one boolean and does
     * not say who took it, but the run's own commitment does: her first gift commits, so a resolved
     * fork on an uncommitted run can only be the deal.
     */
    private static boolean forkTakenByHim(RunEngine.ActiveFloor floor) {
        return floor.forkResolved && !floor.run().ordenCommitted();
    }

    /** The óbolo, on the same stage curve every other coin price in the run uses. */
    private static int obolus(RunEngine.ActiveFloor floor) {
        return CoinDrops.scaleToStage(DungeonsConfig.devilCoinPrice(), floor.run().stage());
    }

    static boolean tryClaim(RunEngine.ActiveFloor floor, ServerPlayer player, Room room,
                            BlockPos clicked) {
        BlockPos font = RunEngine.markerPos(floor, room, "gracia");
        if (!RunEngine.isAtFixture(font, clicked, RANGE)) {
            return false;
        }
        offer(floor, player, room);
        return true;
    }

    /**
     * Her offer, as clickable chat — the same channel the Acreedor's uses, and for the same reason:
     * a CustomNPCs custom GUI deserialises its buttons through a server-only static and throws on a
     * connected client, so every custom-GUI button in this build is broken for anyone not playing
     * singleplayer. Vanilla chat cannot crash anyone.
     */
    static void offer(RunEngine.ActiveFloor floor, ServerPlayer player, Room room) {
        if (forkTakenByHim(floor)) {
            player.displayClientMessage(Component.literal(
                    "§6La Orden calla. §7Ya habéis cerrado el trato."), true);
            return;
        }
        if (floor.ordenPicksLeft == 0) {
            player.displayClientMessage(Component.literal(
                    "§6La Orden ya ha dado lo que tenía que dar."), true);
            return;
        }
        SatelliteOdds.Tier tier = tierOf(floor);
        if (floor.ordenPicksLeft < 0) {
            floor.ordenPicksLeft = tier.picks();
        }
        player.sendSystemMessage(Component.literal("§8§m                              "));
        player.sendSystemMessage(Component.literal(
                "§6La Orden §7— lo que se da, no se cobra."));
        player.sendSystemMessage(Component.literal("§8" + OrdenPool.tierLabel(tier)
                + " §8· §7quedan §f" + floor.ordenPicksLeft + "§7 de sus dones"));
        for (OrdenPool.Gift gift : offered(floor, tier)) {
            player.sendSystemMessage(choice("§6▶ " + gift.label(), gift.id(), gift.blurb()));
        }
        player.sendSystemMessage(choice("§8▶ Marcharse", "marcharse",
                "§7Dejarlo — la puerta del trato sigue abierta"));
        player.sendSystemMessage(Component.literal("§8§m                              "));
    }

    /** The tier's offer, minus anything already taken on this floor. */
    private static List<OrdenPool.Gift> offered(RunEngine.ActiveFloor floor,
                                                SatelliteOdds.Tier tier) {
        List<OrdenPool.Gift> gifts = new ArrayList<>(
                OrdenPool.offer(tier, floor.run().deuda(), obolus(floor)));
        gifts.removeIf(gift -> floor.ordenTaken.contains(gift.id()));
        return gifts;
    }

    private static Component choice(String label, String id, String tooltip) {
        return Component.literal(label).withStyle(style -> style
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        "/teras gracia @s " + id))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.literal(tooltip))));
    }

    /**
     * A gift claimed. The first one commits the run to her — which is what stops the Acreedor
     * calling and, because grace needs a refusal and a refusal needs him to have appeared, is what
     * closes the arc on itself.
     */
    static void take(RunEngine.ActiveFloor floor, ServerPlayer player, Room room, String giftId) {
        BlockPos font = RunEngine.markerPos(floor, room, "gracia");
        if ("marcharse".equals(giftId)) {
            player.displayClientMessage(Component.literal("§8Dejas la capilla como estaba."), true);
            return;
        }
        // The fork is exclusive, and her door being barred is not enough to enforce it: a player
        // standing inside the chapel when the deal was struck elsewhere is still in range of the
        // font, and would otherwise take both sides of a choice that exists to be one.
        if (forkTakenByHim(floor)) {
            deny(floor, player, font, "§6Ya elegisteis. La Orden no da nada a un deudor suyo.");
            return;
        }
        SatelliteOdds.Tier tier = tierOf(floor);
        if (floor.ordenPicksLeft < 0) {
            floor.ordenPicksLeft = tier.picks();
        }
        if (floor.ordenPicksLeft <= 0) {
            deny(floor, player, font, "§6No queda nada más que ella pueda darte aquí.");
            return;
        }
        // An authored dialogue is static and its options cannot be gated, but her first slot is
        // whichever mercy the party's state calls for. One alias resolves to the right one, so an
        // operator writes a single option instead of two dialogues that differ by one line.
        if (MERCY_ALIAS.equals(giftId)) {
            giftId = OrdenPool.mercy(floor.run().deuda()).id();
        }
        final String chosen = giftId;
        OrdenPool.Gift gift = offered(floor, tier).stream()
                .filter(candidate -> candidate.id().equals(chosen))
                .findFirst().orElse(null);
        if (gift == null) {
            deny(floor, player, font, "§6Eso no está sobre la pila.");
            return;
        }
        if (!apply(floor, player, font, gift.id())) {
            return;
        }
        floor.ordenTaken.add(gift.id());
        floor.ordenPicksLeft--;
        // Committed on the first gift, not on the last: a party that took one and walked out has
        // still chosen her side, and the creditor has still been turned down for good.
        floor.run().commitToOrden();
        RunEngine.closeTheFork(floor, DoorKind.GRACIA);
        RunEngine.playAt(floor, font, DungeonSound.PHOENIX, 1.0f);
        DungeonTitles.send(player, "§6La Orden responde", gift.label());
        RunEngine.message(floor, "§6" + player.getName().getString() + " acepta la gracia.");
        if (floor.ordenPicksLeft > 0) {
            player.displayClientMessage(Component.literal(
                    "§7Aún puedes tomar §f" + floor.ordenPicksLeft + "§7 más."), true);
        }
    }

    /** The gift itself. False when it could not be given, which leaves the pick unspent. */
    private static boolean apply(RunEngine.ActiveFloor floor, ServerPlayer player, BlockPos font,
                                 String giftId) {
        switch (giftId) {
            case OrdenPool.ABSOLUCION -> {
                floor.run().settleDebt(floor.run().deuda());
                RunEngine.message(floor, "§6La deuda queda saldada.");
            }
            case OrdenPool.PURIFICACION -> {
                if (!purge(floor, player)) {
                    deny(floor, player, font, "§8No cargas con ninguna aflicción.");
                    return false;
                }
            }
            case OrdenPool.VIGOR -> {
                for (java.util.UUID member : floor.run().party().keySet()) {
                    ServerPlayer other = floor.level().getServer().getPlayerList()
                            .getPlayer(member);
                    if (other != null) {
                        DungeonHealth.healFully(other);
                    }
                }
            }
            case OrdenPool.OBOLO -> {
                floor.run().wallet().add(obolus(floor));
                RunEngine.broadcastWallet(floor);
            }
            case OrdenPool.RELIQUIA ->
                    RunEngine.rollLootAt(floor, font, DungeonsConfig.ordenLootTable());
            case OrdenPool.RESTITUCION -> {
                for (java.util.UUID member : floor.run().party().keySet()) {
                    floor.run().stateOf(member).forgiveHpDebt();
                    ServerPlayer other = floor.level().getServer().getPlayerList()
                            .getPlayer(member);
                    if (other != null) {
                        // Through Afflictions, never DungeonHealth directly: the run's max-health
                        // debt is a devil deal's hearts *plus* any Pulso débil taken at a curse
                        // room, and writing the attribute here would silently undo the other one.
                        Afflictions.apply(floor.run(), other);
                        DungeonHealth.healFully(other);
                    }
                }
            }
            case OrdenPool.FENIX -> floor.run().stateOf(player.getUUID()).grantPhoenix();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Lifts one affliction, personal before party — the curse room's own purge order. */
    private static boolean purge(RunEngine.ActiveFloor floor, ServerPlayer player) {
        AfflictionSet party = floor.run().afflictions();
        AfflictionSet mine = floor.run().stateOf(player.getUUID()).afflictions();
        if (party.size() + mine.size() == 0) {
            return false;
        }
        Afliccion shed = mine.carried().isEmpty()
                ? party.carried().get(party.carried().size() - 1)
                : mine.carried().get(mine.carried().size() - 1);
        if (!mine.remove(shed)) {
            party.remove(shed);
        }
        RunEngine.message(floor, "§d" + shed.nombre() + "§6 se desvanece.");
        RunEngine.syncAfflictions(floor);
        return true;
    }

    private static void deny(RunEngine.ActiveFloor floor, ServerPlayer player, BlockPos font,
                             String reason) {
        player.displayClientMessage(Component.literal(reason), true);
        player.sendSystemMessage(Component.literal(reason));
        RunEngine.playAt(floor, font, DungeonSound.PURCHASE_DENIED, 1.0f);
    }
}
