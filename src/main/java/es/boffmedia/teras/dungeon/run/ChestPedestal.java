package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chests: a reward that may stand in <b>any</b> room, priced in something other than coins.
 *
 * <h2>Why a chest is not another treasure room</h2>
 *
 * <p>Every reward Teras had lived in a room whose whole identity was that reward — the treasure
 * room, the secret, the shop, the devil's pedestal. A chest is the opposite: it is a reason to look
 * around a room you would otherwise clear and leave, which is the property worth taking from Isaac.
 * So {@code cofre} is an <b>optional marker any template may carry</b>, not a room type.</p>
 *
 * <h2>The lock is the design, and it is never the approach</h2>
 *
 * <p>ParCool ships in the pack, so wall-run, cling-to-cliff and cat-leap are baseline movement: a
 * ledge is not a lock, it is a stretch of stamina. Every price here is therefore charged <b>at the
 * moment of claim</b>, where no movement mod can reach it — a wall charge, health, or a gamble.</p>
 *
 * <p>{@link Kind#PROEZA} is the deliberate exception and the reason the rest can be strict. It has
 * no claim-time price at all: it is placed where only parkour reaches, and the stamina and the skill
 * are what it costs. That is only fair because the mod is mandatory — an optional client mod gating
 * loot would sort players rather than charge them.</p>
 *
 * <h2>Who gets one</h2>
 *
 * <p>Per player, like the treasure stands: each member may claim each chest once, so there is no
 * loot race and nobody arrives to an empty room. {@link Kind#SELLADO} is the one two-step case —
 * <b>one</b> wall charge opens the chest for everybody, because a key that had to be bought once per
 * head would price a four-player party out of every sealed chest on the floor.</p>
 */
public final class ChestPedestal {

    /** How close a click has to be to count — every fixture in the run shares this reach. */
    private static final int RANGE = 2;

    /** How long the lid stays up after a claim: long enough to watch the loot come out. */
    private static final int LID_TICKS = 40;

    /** What a chest asks for. Read from the marker's argument: {@code cofre:sellado}. */
    public enum Kind {
        /** No price. A small find, and the only chest that is purely a bonus. */
        LIBRE,
        /** One wall charge opens it, once, for the whole party. */
        SELLADO,
        /** Health at the moment of claim, per claimant, scaling with the floor. */
        PUAS,
        /** A gamble: it pays double, or it bites and pays nothing. */
        TRAMPA,
        /** Placed where only parkour reaches. The route is the price. */
        PROEZA
    }

    /** One chest, its lock, and who has been to it. */
    private static final class Chest {
        final Kind kind;
        final BlockPos pos;
        final Set<UUID> claimed = new LinkedHashSet<>();
        /** SELLADO only: whether somebody has spent the charge that opens it. */
        boolean opened;
        UUID textDisplay;

        Chest(Kind kind, BlockPos pos) {
            this.kind = kind;
            this.pos = pos;
        }
    }

    private final Map<Room, List<Chest>> byRoom = new LinkedHashMap<>();

    /**
     * Stands up every chest {@code room}'s template declares. Rooms with no {@code cofre} marker —
     * which is most of them — cost one lookup and nothing else.
     */
    public void arm(RunEngine.ActiveFloor floor, Room room) {
        if (byRoom.containsKey(room)) {
            return;
        }
        List<Chest> chests = new ArrayList<>();
        for (TemplateMarkers.Marker marker : floor.built().markers().getOrDefault(room, List.of())) {
            if (!marker.kind().equals("cofre")) {
                continue;
            }
            Chest chest = new Chest(kindOf(marker.argument()),
                    floor.built().clampInside(room, marker.pos(), 1));
            chests.add(chest);
            show(floor, chest);
        }
        if (!chests.isEmpty()) {
            byRoom.put(room, chests);
        }
    }

    /**
     * An unknown qualifier is a plain chest rather than a refusal: a typo in a template should cost
     * the room its lock, not its reward, and the log line is where an author finds out.
     */
    private static Kind kindOf(String argument) {
        if (argument == null || argument.isBlank()) {
            return Kind.LIBRE;
        }
        for (Kind kind : Kind.values()) {
            if (kind.name().equalsIgnoreCase(argument)) {
                return kind;
            }
        }
        es.boffmedia.teras.Teras.LOGGER.warn(
                "Dungeons: 'cofre:{}' names no chest kind — standing it up as a plain one", argument);
        return Kind.LIBRE;
    }

    /**
     * Opens a chest if the click was at one.
     *
     * @return whether the click was this system's business, so the caller can cancel the event
     */
    public boolean tryClaim(RunEngine.ActiveFloor floor, ServerPlayer player, BlockPos clicked) {
        for (Map.Entry<Room, List<Chest>> entry : byRoom.entrySet()) {
            for (Chest chest : entry.getValue()) {
                if (!RunEngine.isAtFixture(chest.pos, clicked, RANGE)) {
                    continue;
                }
                take(floor, player, entry.getKey(), chest);
                return true;
            }
        }
        return false;
    }

    private void take(RunEngine.ActiveFloor floor, ServerPlayer player, Room room, Chest chest) {
        if (!floor.run().party().containsKey(player.getUUID())) {
            return;
        }
        if (chest.claimed.contains(player.getUUID())) {
            player.displayClientMessage(Component.literal("§8Ya has abierto este cofre."), true);
            return;
        }
        if (chest.kind == Kind.SELLADO && !chest.opened) {
            if (!floor.run().wallet().tryUseCharge()) {
                player.displayClientMessage(Component.literal(
                        "§7El cofre está sellado — hace falta una carga rompemuros."), true);
                RunEngine.playAt(floor, chest.pos, DungeonSound.PURCHASE_DENIED, 1.0f);
                return;
            }
            // Opened once, for everybody. The charge bought the lock, not a share of the contents.
            chest.opened = true;
            // The bars come off where everyone can see them go. A lock that opens invisibly is a
            // lock the party has to be told about; this one they watch.
            dressing(floor.level(), chest, false);
            floor.level().playSound(null, chest.pos, SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.BLOCKS, 0.8f, 0.8f);
            RunEngine.broadcastWallet(floor);
            RunEngine.message(floor, "§b" + player.getName().getString()
                    + " ha forzado un cofre sellado.");
        }
        if (chest.kind == Kind.PUAS) {
            RunEngine.chargeToll(floor, player, DungeonsConfig.chestSpikeDamage());
        }
        chest.claimed.add(player.getUUID());

        if (chest.kind == Kind.TRAMPA
                && floor.level().random.nextInt(100) < DungeonsConfig.chestTrapChancePct()) {
            // The bite. Isaac's spiked chest, and the reason the gamble is worth taking: what it
            // costs when it goes wrong is health, which nothing on the floor gives back.
            openAndShut(floor, chest);
            RunEngine.chargeToll(floor, player, DungeonsConfig.chestTrapDamage());
            RunEngine.playAt(floor, chest.pos, DungeonSound.PURCHASE_DENIED, 0.6f);
            DungeonTitles.send(player, "§4El cofre muerde", "§7Estaba vacío");
            refresh(floor, room);
            return;
        }
        openAndShut(floor, chest);
        int rolls = chest.kind == Kind.TRAMPA ? 2 : 1;
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : RunEngine.rollLoot(floor, lootTable(chest.kind))) {
                RunEngine.ejectTo(floor, player, chest.pos, stack.copy());
            }
        }
        RunEngine.playAt(floor, chest.pos, DungeonSound.PURCHASE, 1.0f);
        player.displayClientMessage(Component.literal(label(chest.kind, true)), true);
        refresh(floor, room);
    }

    /** Which table a kind draws. Proeza draws the good one: the route was the price. */
    private static String lootTable(Kind kind) {
        return kind == Kind.PROEZA
                ? DungeonsConfig.chestProezaLootTable() : DungeonsConfig.chestLootTable();
    }

    /** Clears every chest's label and takes its blocks back out of the floor. */
    public void despawn(RunEngine.ActiveFloor floor) {
        for (List<Chest> chests : byRoom.values()) {
            for (Chest chest : chests) {
                hide(floor.level(), chest);
                clearBlocks(floor.level(), chest);
            }
        }
        byRoom.clear();
    }

    /** Once everyone has been to a chest it goes quiet, rather than vanishing. */
    private void refresh(RunEngine.ActiveFloor floor, Room room) {
        for (Chest chest : byRoom.getOrDefault(room, List.of())) {
            hide(floor.level(), chest);
            boolean spent = chest.claimed.size() >= Math.max(1, floor.run().party().size());
            var text = DungeonDisplays.spawnText(floor.level(), chest.pos, 1.5,
                    Component.literal(spent ? "§8Vacío" : label(chest.kind, false)));
            chest.textDisplay = text == null ? null : text.getUUID();
            if (spent) {
                // The lid stays up. An emptied chest that looks shut is one the party walks back to;
                // one standing open is a room they can read from the doorway.
                setLid(floor.level(), chest, true);
            }
        }
    }

    /**
     * Puts the chest into the world.
     *
     * <p>A block, not a floating icon on a plinth. That idiom belongs to <b>items</b> — the shop's
     * wares, the treasure stands, the devil's offer — and it was carrying the wrong meaning here: a
     * chest is a container, so it sits on the ground where you walk up and open it. Only the label
     * floats, because the price still has to be readable before the click.</p>
     */
    private void show(RunEngine.ActiveFloor floor, Chest chest) {
        ServerLevel level = floor.level();
        BlockState standing = level.getBlockState(chest.pos);
        boolean free = standing.isAir() || standing.is(Blocks.CHEST) || standing.is(Blocks.ENDER_CHEST);
        if (free) {
            BlockState state = chestBlock(chest.kind).defaultBlockState();
            if (state.hasProperty(net.minecraft.world.level.block.ChestBlock.FACING)) {
                state = state.setValue(net.minecraft.world.level.block.ChestBlock.FACING,
                        facingFor(level, chest.pos));
            }
            level.setBlock(chest.pos, state, 3);
            dressing(level, chest, true);
        } else {
            // The template put something where its own marker is. Not overwriting it keeps a chest
            // from swallowing authored geometry, and the chest still works — the label stands and
            // the click still pays out, so a template mistake costs the room its chest's *look*
            // rather than its reward. The warning is where an author finds out.
            es.boffmedia.teras.Teras.LOGGER.warn(
                    "Dungeons: a 'cofre' marker at {} stands inside {} — the chest has no block",
                    chest.pos, standing.getBlock().getName().getString());
        }
        var text = DungeonDisplays.spawnText(floor.level(), chest.pos, 1.5,
                Component.literal(label(chest.kind, false)));
        chest.textDisplay = text == null ? null : text.getUUID();
    }

    /**
     * The block each kind wears — and the mimic wears nothing.
     *
     * <p>{@link Kind#TRAMPA} is deliberately <b>indistinguishable</b> from {@link Kind#LIBRE}. A
     * trapped chest was the obvious pick and it is the wrong one: a mimic you can identify is a
     * mimic nobody ever opens, which is content that ships dead. Isaac's works because it does not
     * announce itself, so the tension stops being "is this one safe" and becomes "some of them
     * bite" — a fact about the run rather than a puzzle at each chest.</p>
     *
     * <p>The kinds that are <b>decisions</b> rather than gambles do show their price, through the
     * dressing below: púas is ringed in dripstone, sellado is visibly barred. Hiding a cost the
     * player agreed to pay would be a lie; hiding a risk they knowingly take is the game.</p>
     */
    private static Block chestBlock(Kind kind) {
        return kind == Kind.PROEZA ? Blocks.ENDER_CHEST : Blocks.CHEST;
    }

    /** Faces the chest at open floor, so it is approached from the front rather than the back. */
    private static Direction facingFor(ServerLevel level, BlockPos pos) {
        Direction best = Direction.NORTH;
        int bestRoom = -1;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int open = 0;
            for (int step = 1; step <= 3; step++) {
                if (!level.getBlockState(pos.relative(dir, step)).isAir()) {
                    break;
                }
                open++;
            }
            // Prefer a front with room to stand and a back against something.
            if (!level.getBlockState(pos.relative(dir.getOpposite())).isAir()) {
                open += 2;
            }
            if (open > bestRoom) {
                bestRoom = open;
                best = dir;
            }
        }
        return best;
    }

    /**
     * What stands around the chest: the bars that seal one, the spikes that price one.
     *
     * <p>The bars come off when the charge is paid, which is the point of building them out of
     * blocks — the party sees the lock leave rather than reading that it did.</p>
     */
    private static void dressing(ServerLevel level, Chest chest, boolean present) {
        BlockState fill = present ? dressingBlock(chest.kind) : Blocks.AIR.defaultBlockState();
        if (fill == null) {
            return;
        }
        Direction facing = level.getBlockState(chest.pos)
                .hasProperty(net.minecraft.world.level.block.ChestBlock.FACING)
                ? level.getBlockState(chest.pos)
                        .getValue(net.minecraft.world.level.block.ChestBlock.FACING)
                : Direction.NORTH;
        for (Direction side : List.of(facing.getClockWise(), facing.getCounterClockWise())) {
            BlockPos at = chest.pos.relative(side);
            // Only into empty space: a chest wedged against a wall keeps the wall.
            if (present ? level.getBlockState(at).isAir() : level.getBlockState(at) == fill) {
                level.setBlock(at, fill, 3);
            }
        }
    }

    private static BlockState dressingBlock(Kind kind) {
        return switch (kind) {
            case SELLADO -> Blocks.IRON_BARS.defaultBlockState();
            case PUAS -> Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties
                            .VERTICAL_DIRECTION, Direction.UP);
            // Libre, trampa and proeza wear nothing — see chestBlock on why the mimic must not.
            case LIBRE, TRAMPA, PROEZA -> null;
        };
    }

    /**
     * Opens or shuts the lid.
     *
     * <p>Vanilla animates a chest from a block event carrying the number of players who have it
     * open, which is the whole mechanism — so the run can drive the animation without ever opening a
     * container screen. That matters: the reward is thrown to the player by {@code ejectTo}, and a
     * real inventory screen would put a second, emptier chest between them and it.</p>
     */
    private static void setLid(ServerLevel level, Chest chest, boolean open) {
        BlockState state = level.getBlockState(chest.pos);
        if (!state.is(Blocks.CHEST) && !state.is(Blocks.ENDER_CHEST)) {
            return;
        }
        level.blockEvent(chest.pos, state.getBlock(), 1, open ? 1 : 0);
    }

    /** The lid opens, the loot comes out, the lid falls shut behind it. */
    private void openAndShut(RunEngine.ActiveFloor floor, Chest chest) {
        ServerLevel level = floor.level();
        setLid(level, chest, true);
        level.playSound(null, chest.pos, chest.kind == Kind.PROEZA
                ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.CHEST_OPEN,
                SoundSource.BLOCKS, 0.7f, 1.0f);
        DungeonScheduler.after(level.getServer(), LID_TICKS, () -> {
            // Only if nobody has emptied it in the meantime: a spent chest keeps its lid up.
            if (chest.claimed.size() < Math.max(1, floor.run().party().size())) {
                setLid(level, chest, false);
                level.playSound(null, chest.pos, chest.kind == Kind.PROEZA
                        ? SoundEvents.ENDER_CHEST_CLOSE : SoundEvents.CHEST_CLOSE,
                        SoundSource.BLOCKS, 0.6f, 1.0f);
            }
        });
    }

    private void hide(ServerLevel level, Chest chest) {
        DungeonDisplays.discard(level, chest.textDisplay);
        // By tag too: an id lookup answers null in an unloaded chunk, which is what once left shop
        // pedestals standing on later floors.
        DungeonDisplays.sweep(level, chest.pos);
        chest.textDisplay = null;
    }

    /** Takes the chest and its dressing back out, so a rebuilt floor starts from the template. */
    private static void clearBlocks(ServerLevel level, Chest chest) {
        dressing(level, chest, false);
        BlockState state = level.getBlockState(chest.pos);
        if (state.is(Blocks.CHEST) || state.is(Blocks.ENDER_CHEST)) {
            level.setBlock(chest.pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** The stand's own label, and the line a claimant reads. One place, so they cannot disagree. */
    private static String label(Kind kind, boolean taken) {
        if (taken) {
            return switch (kind) {
                case LIBRE, SELLADO -> "§7Has abierto el cofre.";
                case PUAS -> "§4Las púas se cobran lo suyo.";
                case TRAMPA -> "§6La apuesta ha salido bien.";
                case PROEZA -> "§dTe lo has ganado subiendo.";
            };
        }
        return switch (kind) {
            case LIBRE -> "§6Cofre §7· cada uno abre una vez";
            case SELLADO -> "§bCofre sellado §7· 1 carga lo abre para todos";
            case PUAS -> "§4Cofre de púas §7· cuesta sangre";
            case TRAMPA -> "§6Cofre dudoso §7· paga el doble, o muerde";
            case PROEZA -> "§dCofre de proeza §7· si llegas, es tuyo";
        };
    }

    /** Diagnostic, for the debug command. */
    public int size() {
        return byRoom.size();
    }
}
