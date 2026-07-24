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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
    public enum Role { ACREEDOR }

    /** The clone tab the characters are installed into — the bestiary's neighbour, not its tab. */
    public static final int TAB = 8;

    public static final String ACREEDOR_ID = "acreedor";

    /** The shipped characters, installed by {@code /teras dungeon personajes instalar}. */
    public static final List<CnpcBridge.CharacterPreset> SHIPPED = List.of(
            new CnpcBridge.CharacterPreset(ACREEDOR_ID, "§5El Acreedor",
                    "minecraft:textures/entity/illager/evoker.png", 5, 40));

    /**
     * A character currently standing on a floor.
     *
     * @param hasDialog whether an authored CustomNPCs dialogue is attached. When it is, the
     *                  right-click belongs to CustomNPCs and we keep out of the way; when it is not,
     *                  we claim the click and show the built-in chat offer instead
     */
    private record Standing(int runId, Role role, Room room, boolean hasDialog) {}

    private static final Map<UUID, Standing> STANDING = new HashMap<>();
    /** Who has an offer window open, and whose offer it is. */
    private static final Map<UUID, Standing> OPEN_OFFER = new HashMap<>();

    /** Choice ids, as they appear in the hidden command behind each clickable line. */
    public static final String CHOICE_COINS = "monedas";
    public static final String CHOICE_HEARTS = "corazones";
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
        BlockPos at = RunEngine.markerPos(floor, room, "deal");
        Entity spawned = CnpcBridge.spawnClone(floor.level(),
                at.getX() + 0.5, at.getY(), at.getZ() + 1.5, TAB, idOf(role));
        if (spawned == null) {
            // No clone installed yet — the pedestal still works, so the floor is playable.
            Teras.LOGGER.warn("Dungeons: no '{}' clone in tab {} — the room keeps its pedestal only",
                    idOf(role), TAB);
            return;
        }
        // Tagged like everything else the dungeon places, so the floor's teardown sweep takes him
        // with it. Untagged, a character would outlive his floor in the void dimension — and the
        // next floor builds its geometry straight over where he is standing.
        spawned.addTag(es.boffmedia.teras.dungeon.encounter.EnemySpawner.DUNGEON_TAG);
        // Read, not assigned: dialogues live in the NPC's own NBT and therefore in the stored
        // clone, so whatever the operator set up in the editor arrives here by itself.
        boolean dialog = CnpcBridge.hasDialog(spawned);
        STANDING.put(spawned.getUUID(), new Standing(floor.run().id(), role, room, dialog));
    }

    private static String idOf(Role role) {
        return role == Role.ACREEDOR ? ACREEDOR_ID : ACREEDOR_ID;
    }

    /** Forgets a floor's characters; the entities themselves go with the floor's own sweep. */
    static void clear(int runId) {
        STANDING.entrySet().removeIf(entry -> entry.getValue().runId() == runId);
        OPEN_OFFER.entrySet().removeIf(entry -> entry.getValue().runId() == runId);
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
        // With a dialogue attached the conversation is CustomNPCs' job and its options carry the
        // commands — claiming the click here would suppress the very dialogue we asked it to open.
        if (standing.hasDialog()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            openOffer(floor, player, standing);
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
        OPEN_OFFER.put(player.getUUID(), standing);

        player.sendSystemMessage(Component.literal("§8§m                              "));
        player.sendSystemMessage(Component.literal("§5El Acreedor §7— todo tiene precio."));
        if (floor.run().deuda() > 0) {
            player.sendSystemMessage(Component.literal(
                    "§7Aún me debes §f" + floor.run().deuda() + " §7monedas."));
        }
        player.sendSystemMessage(choice("§e▶ Pagar " + coins + " monedas", CHOICE_COINS,
                "§7Del bolsillo común de la partida"));
        player.sendSystemMessage(choice("§4▶ Pagar " + hearts + " corazones", CHOICE_HEARTS,
                "§7De tu salud máxima, hasta el final de la partida"));
        player.sendSystemMessage(choice("§8▶ Marcharse", CHOICE_LEAVE,
                "§7Rechazar — y quizá la Orden lo note"));
        player.sendSystemMessage(Component.literal("§8§m                              "));
    }

    /** One clickable line of the offer, running the hidden choice command. */
    private static Component choice(String label, String id, String tooltip) {
        return Component.literal(label).withStyle(style -> style
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        "/teras trato " + id))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.literal(tooltip))));
    }

    /**
     * A choice made at an open offer. The offer must have been opened by clicking him — the command
     * is only ever reachable through the chat lines above, and typing it without a live offer, from
     * another run, or from across the floor does nothing.
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
        OPEN_OFFER.remove(player.getUUID());
        switch (choiceId) {
            case CHOICE_COINS -> DevilDeal.offer(floor, player, room, false);
            case CHOICE_HEARTS -> DevilDeal.offer(floor, player, room, true);
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
        return Map.of(
                "%monedas%", String.valueOf(
                        CoinDrops.scaleToStage(DungeonsConfig.devilCoinPrice(), run.stage())),
                "%corazones%", String.valueOf(DungeonsConfig.devilHeartPrice()),
                "%bolsa%", String.valueOf(run.wallet().coins()),
                "%deuda%", String.valueOf(run.deuda()),
                "%piso%", String.valueOf(run.stage()),
                "%jugador%", player.getName().getString());
    }

    /** The room a floor's Acreedor stands in, or null when he did not visit. */
    static Room acreedorRoom(RunEngine.ActiveFloor floor) {
        for (Room room : floor.built().layout().rooms()) {
            if (room.type() == RoomType.DEVIL_DEAL) {
                return room;
            }
        }
        return null;
    }
}
