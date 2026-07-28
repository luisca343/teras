package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.encounter.CnpcBridge;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dungeon's characters — the ones that talk instead of fighting. Today that is El Acreedor,
 * standing in his room off the sala del sello once the boss has fallen (PRODUCCION §10.4).
 *
 * <p>This class holds no CustomNPCs types at all: {@link CnpcBridge} owns every {@code noppes.*}
 * import, and what crosses the line is a UUID and a button index. So a server without CustomNPCs
 * loses the character and keeps the room, the pedestal and the deal.</p>
 *
 * <p><b>Identity comes from the clone, not from persistence.</b> The character is installed once and
 * a fresh copy is spawned on each floor that wants him, then swept with the floor. Nothing survives
 * between floors — and because every copy is stamped from the same stored clone, he reads as the
 * same being every time, which is exactly what the arc needs him to be.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DungeonNpcs {
    private DungeonNpcs() {}

    /** Which character a spawned entity is. */
    public enum Role { ACREEDOR, ORDEN }

    /** The clone tab the characters are installed into — the bestiary's neighbour, not its tab. */
    public static final int TAB = 8;

    public static final String ACREEDOR_ID = "acreedor";
    public static final String ORDEN_ID = "orden";

    /** The shipped characters, installed by {@code /teras dungeon personajes instalar}. */
    public static final List<CnpcBridge.CharacterPreset> SHIPPED = List.of(
            new CnpcBridge.CharacterPreset(ACREEDOR_ID, "§5El Acreedor",
                    "minecraft:textures/entity/illager/evoker.png", 5, 40),
            // Her opposite number, and deliberately the plainest skin in the game: the Orden are
            // people who stayed at their posts, not an apparition. The evoker across the wing is
            // doing the work of looking otherworldly for both of them.
            new CnpcBridge.CharacterPreset(ORDEN_ID, "§6La Orden",
                    "minecraft:textures/entity/player/wide/steve.png", 5, 40));

    /** The marker a role stands at — his pedestal, her font. */
    private static String markerOf(Role role) {
        return role == Role.ORDEN ? "gracia" : "deal";
    }

    /**
     * A character currently standing on a floor.
     *
     * @param hasDialog whether an authored CustomNPCs dialogue is attached. When it is, the
     *                  right-click belongs to CustomNPCs and we keep out of the way; when it is not,
     *                  we claim the click and show the built-in chat offer instead
     */
    private record Standing(int runId, Role role, Room room, boolean hasDialog) {}

    private static final Map<UUID, Standing> STANDING = new HashMap<>();

    /** Choice ids, as they appear behind a dialogue option or a clickable chat line. */
    public static final String CHOICE_COINS = "monedas";
    public static final String CHOICE_HEARTS = "corazones";
    public static final String CHOICE_BORROW = "prestado";
    public static final String CHOICE_SETTLE = "saldar";
    public static final String CHOICE_LEAVE = "marcharse";

    /** How close to his pedestal a choice may be made from. */
    private static final int REACH = 6;

    /**
     * Spawns the character a just-opened satellite wants, if CustomNPCs is present. Called from the
     * reveal, so he is never standing behind bars: he arrives when the door does.
     */
    static void spawnFor(RunEngine.ActiveFloor floor, Room room, Role role) {
        if (!CnpcBridge.available()) {
            return;
        }
        // Beside his own pedestal: the fixture stays where it was authored, and he stands at it.
        BlockPos at = RunEngine.markerPos(floor, room, markerOf(role));
        Entity spawned = CnpcBridge.spawnClone(floor.level(),
                at.getX() + 0.5, at.getY(), at.getZ() + 1.5, TAB, idOf(role));
        if (spawned == null) {
            // Logged as the content hole it is: he is the only way into his room's business, so
            // without the clone the room opens onto furniture and nothing can be taken from it.
            Teras.LOGGER.warn("Dungeons: no '{}' clone in tab {} — the room stands empty and nothing "
                    + "in it can be claimed", idOf(role), TAB);
            return;
        }
        // Tagged like everything else the dungeon places, so the floor's teardown sweep takes him
        // with it. Untagged, a character would outlive his floor in the void dimension — and the
        // next floor builds its geometry straight over where he is standing.
        spawned.addTag(es.boffmedia.teras.dungeon.encounter.EnemySpawner.DUNGEON_TAG);
        // Read, not assigned: dialogues live in the NPC's own NBT and therefore in the stored
        // clone, so whatever the operator set up in the editor arrives here by itself.
        boolean dialog = CnpcBridge.hasDialog(spawned);
        if (!dialog) {
            // Said out loud, because the fallback is silent and looks like a regression: the party
            // gets a clickable chat offer that works, and no sign of the authored character. The
            // clone is a copy-on-spawn snapshot of its NBT, so a dialogue attached to a spawned
            // NPC on a floor is thrown away with that floor — it has to be set on the clone in the
            // tab below. See docs/DUNGEONS_DIALOGOS.md for the pack and how to attach it.
            Teras.LOGGER.warn("Dungeons: the '{}' clone in tab {} carries no dialogue in slots 0-11 "
                    + "— falling back to the chat offer. Attach the authored dialogues to the CLONE "
                    + "(editing a spawned one is discarded); see docs/DUNGEONS_DIALOGOS.md.",
                    idOf(role), TAB);
        }
        STANDING.put(spawned.getUUID(), new Standing(floor.run().id(), role, room, dialog));
    }

    /** The clone id a role is spawned from. */
    private static String idOf(Role role) {
        return switch (role) {
            case ACREEDOR -> ACREEDOR_ID;
            case ORDEN -> ORDEN_ID;
        };
    }

    /** Forgets a floor's characters; the entities themselves go with the floor's own sweep. */
    static void clear(int runId) {
        STANDING.entrySet().removeIf(entry -> entry.getValue().runId() == runId);
    }

    /**
     * A player right-clicked an entity. If it is one of our characters the click is consumed and
     * his offer opens; anything else is left entirely alone, so ordinary server NPCs still work.
     *
     * <p><b>This is NeoForge's event, not CustomNPCs'.</b> {@code EventHooks.onNPCInteract} builds
     * an {@code NpcEvent.InteractEvent} and hands it only to that NPC's script engine — it never
     * posts to {@code WrapperNpcAPI.EVENT_BUS}, so a listener there registers and is never called.
     * {@code EntityInteract} fires before the entity's own {@code interact}, so cancelling it here
     * both opens our window and suppresses whatever CustomNPCs would have done.</p>
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // Both hands fire for one right-click; the off-hand pass would open a second window over
        // the first. Cancel both so the click is fully ours, but act on the main hand only.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Standing standing = STANDING.get(event.getTarget().getUUID());
        if (standing == null) {
            return;
        }
        RunEngine.ActiveFloor floor = RunEngine.activeFloor(standing.runId());
        if (floor == null || !floor.run().party().containsKey(player.getUUID())) {
            return;
        }
        // Before CustomNPCs decides which of his dialogues to open, and this is the only moment that
        // works: EntityInteract fires from Player.interactOn ahead of the NPC's own mobInteract, and
        // it is mobInteract that walks the slots asking each dialogue's availability.
        publishConditions(player);
        // With a dialogue attached the conversation is CustomNPCs' job and its options carry the
        // commands — claiming the click here would suppress the very dialogue we asked it to open.
        if (standing.hasDialog()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            if (standing.role() == Role.ORDEN) {
                OrdenGift.offer(floor, player, standing.room());
            } else {
                openOffer(floor, player, standing);
            }
        }
    }

    /**
     * His offer, as clickable chat.
     *
     * <p><b>Not a CustomNPCs custom GUI, and it cannot be one.</b>
     * {@code CustomGuiButtonWrapper.fromNBT} calls {@code NBTTags.getProvider()} unconditionally
     * before reading a button's display item, and {@code NBTTags.server} is a <i>server-only</i>
     * static — so on a dedicated server's client it is null and deserialising any button throws
     * {@code NullPointerException} inside CustomNPCs' own screen code. Every custom-GUI button is
     * therefore broken for connected clients in this build, and nothing we pass can avoid that path.
     * Clickable chat is vanilla, needs no client mod code at all, and cannot crash anyone.</p>
     */
    private static void openOffer(RunEngine.ActiveFloor floor, ServerPlayer player,
                                  Standing standing) {
        if (floor.devilClaimed.contains(standing.room())) {
            player.displayClientMessage(Component.literal("§5Ya has cerrado un trato aquí."), true);
            return;
        }
        int coins = CoinDrops.scaleToStage(DungeonsConfig.devilCoinPrice(), floor.run().stage());
        int hearts = DungeonsConfig.devilHeartPrice();

        player.sendSystemMessage(Component.literal("§8§m                              "));
        player.sendSystemMessage(Component.literal("§5El Acreedor §7— todo tiene precio."));
        if (floor.run().deuda() > 0) {
            player.sendSystemMessage(Component.literal(
                    "§7Aún me debes §f" + floor.run().deuda() + " §7monedas."));
            player.sendSystemMessage(choice(
                    "§6▶ Saldar por " + DevilDeal.settlePrice(floor.run().deuda()) + " monedas",
                    CHOICE_SETTLE, "§7Menos que la cifra — por venir tú a mí"));
        }
        player.sendSystemMessage(choice("§e▶ Pagar " + coins + " monedas", CHOICE_COINS,
                "§7Del bolsillo común de la partida"));
        player.sendSystemMessage(choice("§4▶ Pagar " + hearts + " corazones", CHOICE_HEARTS,
                "§7De tu salud máxima, hasta el final de la partida"));
        if (floor.run().deuda() <= 0) {
            player.sendSystemMessage(choice(
                    "§5▶ Pedir prestado (" + DevilDeal.loanFace(floor.run().stage()) + " a deber)",
                    CHOICE_BORROW, "§7Ahora nada. Después, alguien vendrá a cobrarlo"));
        }
        player.sendSystemMessage(choice("§8▶ Marcharse", CHOICE_LEAVE,
                "§7Rechazar — y quizá la Orden lo note"));
        player.sendSystemMessage(Component.literal("§8§m                              "));
    }

    /**
     * One clickable line of the offer, running the hidden choice command.
     *
     * <p><b>{@code @s} is not decoration.</b> The command is {@code /teras trato <jugador> <opcion>}
     * — it takes the player explicitly because CustomNPCs runs a dialogue option's command from its
     * own sender rather than from the player, so an authored option carries {@code @dp}. A click
     * event does run as the player, but the argument is still required, and the two-token form this
     * used to emit did not parse at all: every chat-fallback offer — which is what a server without
     * the authored dialogue pack installed sees — silently did nothing.</p>
     */
    private static Component choice(String label, String id, String tooltip) {
        return Component.literal(label).withStyle(style -> style
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        "/teras trato @s " + id))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.literal(tooltip))));
    }

    /**
     * A choice made at his pedestal, from a dialogue option or from the chat offer above. Nothing
     * here remembers that a window was opened, because a CustomNPCs dialogue is opened by CustomNPCs
     * and never tells us — so the standing itself is the check: your run, your floor, his room.
     */
    public static boolean choose(ServerPlayer player, String choiceId) {
        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        if (run == null) {
            return false;
        }
        RunEngine.ActiveFloor floor = RunEngine.activeFloor(run.id());
        if (floor == null || player.serverLevel() != floor.level()) {
            return false;
        }
        Room room = acreedorRoom(floor);
        if (room == null) {
            return false;
        }
        // Proximity IS the authorisation. The command has to be ungated so a dialogue option can
        // reach it, and CustomNPCs runs it from its own sender rather than from the player — so
        // "are you standing at him, on the floor he is on, in a run you belong to" is what stands
        // between the command and anyone who types it out of a chat log.
        BlockPos at = RunEngine.markerPos(floor, room, "deal");
        if (!player.blockPosition().closerThan(at, REACH)) {
            player.displayClientMessage(
                    Component.literal("§7Estás demasiado lejos del Acreedor."), true);
            return false;
        }
        switch (choiceId) {
            case CHOICE_COINS -> DevilDeal.offer(floor, player, room, false);
            case CHOICE_HEARTS -> DevilDeal.offer(floor, player, room, true);
            case CHOICE_BORROW -> DevilDeal.borrow(floor, player, room);
            case CHOICE_SETTLE -> DevilDeal.settle(floor, player, room);
            default -> player.displayClientMessage(
                    Component.literal("§8Te alejas del trato."), true);
        }
        return true;
    }

    /**
     * The numbers an authored dialogue may quote, as {@code %token%} → value. Filled into the
     * <i>per-player copy</i> CustomNPCs makes of a dialogue before it is sent (see
     * {@code CnpcDialogMixin}), so the operator writes the wording once in the NPC editor and the
     * offer on screen is always this floor's actual offer.
     *
     * <p>Empty outside a run, which leaves the tokens standing in the text — a raw {@code %monedas%}
     * on screen is exactly the right signal that the dialogue is being read somewhere it has no
     * prices to quote.</p>
     */
    public static Map<String, String> dialogTokens(ServerPlayer player) {
        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        if (run == null) {
            return Map.of();
        }
        RunEngine.ActiveFloor floor = RunEngine.activeFloor(run.id());
        if (floor == null || player.serverLevel() != floor.level()) {
            return Map.of();
        }
        int price = CoinDrops.scaleToStage(DungeonsConfig.devilCoinPrice(), run.stage());
        return Map.of(
                "%monedas%", String.valueOf(price),
                "%corazones%", String.valueOf(DungeonsConfig.devilHeartPrice()),
                "%bolsa%", String.valueOf(run.wallet().coins()),
                "%deuda%", String.valueOf(run.deuda()),
                "%piso%", String.valueOf(run.stage()),
                "%jugador%", player.getName().getString(),
                // The loan's face value and what clearing it early costs — the two numbers the
                // deuda arc turns on, and neither is derivable from %monedas% by an author.
                "%prestamo%", String.valueOf(DevilDeal.loanFace(run.stage())),
                "%saldo%", String.valueOf(DevilDeal.settlePrice(run.deuda())),
                // Hers: what the óbolo pays, and how many gifts are left on the font.
                "%obolo%", String.valueOf(price),
                "%dones%", String.valueOf(Math.max(0, floor.ordenPicksLeft < 0
                        ? OrdenGift.tierOf(floor).picks() : floor.ordenPicksLeft)));
    }

    /**
     * The scoreboard objectives a dialogue's <i>availability</i> can be gated on, so an operator can
     * give a character several dialogues and let the floor decide which one he opens.
     *
     * <p>CustomNPCs picks the <b>first</b> of an NPC's dialogue slots whose availability passes, and
     * a vanilla scoreboard is the only condition in that screen a mod can drive — the rest ask about
     * quests, factions, daytime and dialogues already read. Two of these are deliberately
     * pre-computed answers rather than raw numbers ({@code teras_paga_*}), because the editor
     * compares an objective against a <i>constant you type</i> and never against another objective:
     * "does the purse cover the price" is unaskable there unless the mod answers it first.</p>
     */
    public static final String OBJ_TRATO = "teras_trato";
    public static final String OBJ_BOLSA = "teras_bolsa";
    public static final String OBJ_PRECIO = "teras_precio";
    public static final String OBJ_DEUDA = "teras_deuda";
    public static final String OBJ_PISO = "teras_piso";
    public static final String OBJ_PAGA_MONEDAS = "teras_paga_monedas";
    public static final String OBJ_PAGA_VIDA = "teras_paga_vida";
    /**
     * 1 while the run owes him anything. Pre-computed for the reason the two {@code paga_} answers
     * are: the editor compares an objective against a constant you type, so "deuda is not zero" is
     * unaskable there — only "equals 0" and "equals 1" are, and this is the second one.
     */
    public static final String OBJ_DEBE = "teras_debe";
    /** Her side of the same trick: 1 while a gift is still there to take. */
    public static final String OBJ_GRACIA = "teras_gracia";
    /** Her tier as 1/2/3 — pre-computed for the same reason the two {@code paga_} answers are. */
    public static final String OBJ_NIVEL_GRACIA = "teras_nivel_gracia";
    /** 1 once either satellite has been claimed, whichever it was. */
    public static final String OBJ_BIFURCACION = "teras_bifurcacion";

    private static final List<String> OBJECTIVES = List.of(OBJ_TRATO, OBJ_BOLSA, OBJ_PRECIO,
            OBJ_DEUDA, OBJ_PISO, OBJ_PAGA_MONEDAS, OBJ_PAGA_VIDA,
            OBJ_DEBE, OBJ_GRACIA, OBJ_NIVEL_GRACIA, OBJ_BIFURCACION);

    /**
     * Writes this player's floor state onto those objectives. Called on the click itself rather than
     * on a tick: the values only have to be true at the instant CustomNPCs asks, and a run's purse
     * changes on every coin picked up.
     */
    public static void publishConditions(ServerPlayer player) {
        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        RunEngine.ActiveFloor floor = run == null ? null : RunEngine.activeFloor(run.id());
        if (floor == null || player.serverLevel() != floor.level()) {
            clearConditions(player);
            return;
        }
        int price = CoinDrops.scaleToStage(DungeonsConfig.devilCoinPrice(), run.stage());
        int hearts = DungeonsConfig.devilHeartPrice();
        Room room = acreedorRoom(floor);
        score(player, OBJ_TRATO, room != null && floor.devilClaimed.contains(room) ? 1 : 0);
        score(player, OBJ_BOLSA, run.wallet().coins());
        score(player, OBJ_PRECIO, price);
        score(player, OBJ_DEUDA, run.deuda());
        score(player, OBJ_DEBE, run.deuda() > 0 ? 1 : 0);
        score(player, OBJ_PISO, run.stage());
        score(player, OBJ_PAGA_MONEDAS, run.wallet().coins() >= price ? 1 : 0);
        score(player, OBJ_PAGA_VIDA, player.getMaxHealth() - hearts * 2 >= 2.0f ? 1 : 0);

        // Her half. `ordenPicksLeft` is -1 until her font has been read, which is "she still has
        // everything" rather than "she has nothing" — publishing the raw number would make a fresh
        // chapel look spent to a dialogue comparing against 0.
        boolean giftsLeft = floor.ordenPicksLeft != 0
                && !(floor.forkResolved && !run.ordenCommitted());
        score(player, OBJ_GRACIA, giftsLeft ? 1 : 0);
        score(player, OBJ_NIVEL_GRACIA, switch (OrdenGift.tierOf(floor)) {
            case MENOR -> 1;
            case MAYOR -> 2;
            case PLENA -> 3;
        });
        score(player, OBJ_BIFURCACION, floor.forkResolved ? 1 : 0);
    }

    /** Zeroes them on the way out, so nothing outside a run reads a floor that has ended. */
    public static void clearConditions(ServerPlayer player) {
        for (String objective : OBJECTIVES) {
            score(player, objective, 0);
        }
    }

    /**
     * Creates the eleven at server start. Called once, from {@code DungeonRunManager}.
     *
     * <p>They used to be created lazily by {@link #score}, which never fired only because a
     * long-lived world already had all eleven. See {@link DungeonObjectives}.</p>
     */
    public static void ensureObjectives(MinecraftServer server) {
        DungeonObjectives.ensure(server, OBJECTIVES);
    }

    /** The eleven, for the login seeding in {@link DungeonObjectives#seed}. */
    public static List<String> objectives() {
        return OBJECTIVES;
    }

    private static void score(ServerPlayer player, String name, int value) {
        DungeonObjectives.set(player, name, value);
    }

    /** The room a floor's Acreedor stands in, or null when he did not visit. */
    static Room acreedorRoom(RunEngine.ActiveFloor floor) {
        return roomOfType(floor, RoomType.DEVIL_DEAL);
    }

    /** La Orden's chapel on this floor, or null when purity did not bring her. */
    static Room ordenRoom(RunEngine.ActiveFloor floor) {
        return roomOfType(floor, RoomType.ORDEN);
    }

    private static Room roomOfType(RunEngine.ActiveFloor floor, RoomType type) {
        for (Room room : floor.built().layout().rooms()) {
            if (room.type() == type) {
                return room;
            }
        }
        return null;
    }

    /**
     * A gift chosen at her font, from a chat line or a dialogue option. The same shape — and the
     * same reasoning — as {@link #choose}: the command has to be ungated so a CustomNPCs option can
     * reach it, so standing at her, on her floor, in a run you belong to <i>is</i> the authorisation.
     */
    public static boolean chooseGracia(ServerPlayer player, String giftId) {
        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        if (run == null) {
            return false;
        }
        RunEngine.ActiveFloor floor = RunEngine.activeFloor(run.id());
        if (floor == null || player.serverLevel() != floor.level()) {
            return false;
        }
        Room room = ordenRoom(floor);
        if (room == null) {
            return false;
        }
        BlockPos at = RunEngine.markerPos(floor, room, "gracia");
        if (!player.blockPosition().closerThan(at, REACH)) {
            player.displayClientMessage(
                    Component.literal("§7Estás demasiado lejos de la Orden."), true);
            return false;
        }
        OrdenGift.take(floor, player, room, giftId);
        return true;
    }
}
