package es.boffmedia.teras.dungeon.instance;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.BuiltDungeon;
import es.boffmedia.teras.dungeon.build.DungeonMaterializer;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.gen.DungeonGenerationException;
import es.boffmedia.teras.dungeon.gen.DungeonGenerator;
import es.boffmedia.teras.dungeon.gen.GenConfig;
import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.run.RunEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Instanced runs on a slot lattice in the dungeon dimension ({@code teras:vacio} by default,
 * coordinates claimed through {@link es.boffmedia.teras.world.VoidZones}):
 * allocate a slot, build the floor there, teleport the party in, and always leave the world as it
 * was — the run journal is written before the first block and deleted after the last one is
 * swept, so a crash at any point leaves a file the boot sweep can act on instead of an orphan
 * floor. Runs themselves do not survive a restart; their cleanup and their players' way home do.
 *
 * <p>Everything here runs on the server thread (commands, tick jobs, login events) — no locks.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonRunManager {
    private DungeonRunManager() {}

    private static final Map<Integer, DungeonRun> RUNS = new LinkedHashMap<>();
    private static final BitSet SLOTS = new BitSet();
    private static Map<UUID, DungeonRun.ReturnPoint> pendingReturns = new LinkedHashMap<>();
    /** Members who walked out on a run, so the report can tell them from those who saw it through. */
    private static final Set<UUID> abandoned = new java.util.HashSet<>();
    private static int nextRunId = 1;

    public record StartOutcome(DungeonRun run, String error) {
        static StartOutcome fail(String error) {
            return new StartOutcome(null, error);
        }
    }

    public static Collection<DungeonRun> runs() {
        return List.copyOf(RUNS.values());
    }

    public static DungeonRun runOf(UUID player) {
        return RUNS.values().stream()
                .filter(r -> r.party().containsKey(player))
                .findFirst().orElse(null);
    }

    /** Starts a run for {@code members} (the leader among them); one slot, one shared floor. */
    public static StartOutcome start(ServerPlayer leader, Collection<ServerPlayer> members,
                                     int stage, Set<Curse> curses, String seed) {
        for (ServerPlayer member : members) {
            if (runOf(member.getUUID()) != null) {
                return StartOutcome.fail(member == leader ? "Ya estás en una mazmorra."
                        : member.getName().getString() + " ya está en una mazmorra.");
            }
        }
        ServerLevel level = dungeonLevel(leader.getServer());
        if (level == null) {
            return StartOutcome.fail("La dimensión " + DungeonsConfig.dimension() + " no existe.");
        }
        int slot = SLOTS.nextClearBit(0);
        if (slot >= DungeonsConfig.maxSlots()) {
            return StartOutcome.fail("No hay huecos de instancia libres.");
        }

        DungeonLayout layout;
        try {
            layout = DungeonGenerator.generate(GenConfig.defaults(), stage, curses, seed);
        } catch (DungeonGenerationException e) {
            return StartOutcome.fail("Generación fallida: " + e.getMessage());
        }

        SLOTS.set(slot);
        DungeonRun run = new DungeonRun(nextRunId++, slot, stage, curses, layout);
        for (ServerPlayer member : members) {
            run.party().put(member.getUUID(), returnPointOf(member));
            run.names().put(member.getUUID(), member.getName().getString());
        }
        RUNS.put(run.id(), run);

        BlockPos origin = padOrigin(slot, 0);
        RunJournal.write(run.id(), DungeonsConfig.dimension(),
                DungeonsConfig.roomSize(), DungeonsConfig.roomHeight(),
                cellOrigins(layout, origin), run.party());

        MinecraftServer server = leader.getServer();
        DungeonMaterializer.enqueueBuild(level, layout, origin, built -> {
            run.activate(built.id());
            // The whole party can quit or log out during the second or two the floor takes to
            // build; a run with nobody in it must fold instead of standing registered forever.
            if (run.party().isEmpty()) {
                end(server, run.id());
                return;
            }
            RunEngine.register(run, built, level);
            teleportPartyIn(server, run, built);
        });
        return new StartOutcome(run, null);
    }

    /**
     * One member walks out on a live run: home (mode and all) with their stored return point,
     * map hidden, the rest told. The last one out ends the run and sweeps the floor.
     */
    public static boolean leaveRun(ServerPlayer player) {
        DungeonRun run = runOf(player.getUUID());
        if (run == null) {
            return false;
        }
        DungeonRun.ReturnPoint point = run.party().remove(player.getUUID());
        abandoned.add(player.getUUID());
        if (point != null) {
            teleport(player, point);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                es.boffmedia.teras.net.DungeonMapPayload.hidden());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                es.boffmedia.teras.net.DungeonWalletPayload.hidden());
        message(player.getServer(), run,
                "§7" + player.getName().getString() + " ha abandonado la mazmorra.");
        if (run.party().isEmpty() && run.state() == DungeonRun.State.ACTIVE) {
            end(player.getServer(), run.id());
        }
        return true;
    }

    /**
     * The party dropped through the trapdoor: build the next floor on the slot's other pad,
     * move everyone, then sweep the floor they left. The journal covers <b>both</b> pads from
     * the moment the new build starts until the old floor is cleared — a crash anywhere in the
     * transition leaves nothing the boot sweep doesn't know about.
     */
    public static void advanceStage(DungeonRun run, ServerLevel level) {
        if (run.state() != DungeonRun.State.ACTIVE || RUNS.get(run.id()) != run) {
            return;
        }
        MinecraftServer server = level.getServer();
        int next = run.stage() + 1;
        if (next > GenConfig.defaults().finalStage()) {
            completeRun(server, run);
            return;
        }

        DungeonLayout newLayout;
        try {
            newLayout = DungeonGenerator.generate(GenConfig.defaults(), next, run.curses(),
                    run.layout().seedString());
        } catch (DungeonGenerationException e) {
            Teras.LOGGER.error("Dungeons: could not generate stage {} of run {}: {}",
                    next, run.id(), e.getMessage());
            completeRun(server, run);
            return;
        }

        int oldBuiltId = run.builtId();
        BuiltDungeon oldBuilt = DungeonMaterializer.get(oldBuiltId);
        int newPad = run.padIndex() ^ 1;
        BlockPos newOrigin = padOrigin(run.slot(), newPad);

        run.beginAdvance();
        RunEngine.unregister(run.id());

        List<BlockPos> journalCells = cellOrigins(newLayout, newOrigin);
        if (oldBuilt != null) {
            journalCells.addAll(builtCellOrigins(oldBuilt));
        }
        RunJournal.write(run.id(), DungeonsConfig.dimension(),
                DungeonsConfig.roomSize(), DungeonsConfig.roomHeight(), journalCells, run.party());
        message(server, run, "§7Descendiendo al piso " + next + "…");

        DungeonMaterializer.enqueueBuild(level, newLayout, newOrigin, built -> {
            run.advanceFloor(next, newLayout, built.id(), newPad);
            if (run.party().isEmpty()) {
                // Enqueued before end()'s own discard, so the journal (deleted only after the
                // later job completes) covers the old floor for the whole sweep.
                if (oldBuilt != null) {
                    DungeonMaterializer.enqueueDiscard(oldBuiltId, level, () -> { });
                }
                end(server, run.id());
                return;
            }
            RunEngine.register(run, built, level);
            teleportPartyIn(server, run, built);
            if (oldBuilt != null) {
                DungeonMaterializer.enqueueDiscard(oldBuiltId, level, () ->
                        RunJournal.write(run.id(), DungeonsConfig.dimension(),
                                DungeonsConfig.roomSize(), DungeonsConfig.roomHeight(),
                                cellOrigins(newLayout, newOrigin), run.party()));
            }
        });
    }

    private static void completeRun(MinecraftServer server, DungeonRun run) {
        message(server, run, "§6¡Mazmorra completada! Etapa " + run.stage()
                + " superada con semilla " + run.layout().seedString() + ".");
        payOutCoins(server, run);
        report(run, true);
        end(server, run.id());
    }

    /**
     * The one place dungeon coins become money. They are worthless anywhere else — no bank, no
     * trade, no way out of the run with them — so finishing is what makes the whole floor's income
     * real, and dying on the last stage forfeits it. Split equally among whoever is still standing
     * there: the purse was shared all along.
     */
    private static void payOutCoins(MinecraftServer server, DungeonRun run) {
        int coins = run.wallet().coins();
        int rate = DungeonsConfig.coinToPesos();
        if (coins <= 0 || rate <= 0) {
            return;
        }
        List<ServerPlayer> present = new java.util.ArrayList<>();
        for (UUID member : run.party().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                present.add(player);
            }
        }
        if (present.isEmpty()) {
            return;
        }
        run.wallet().cashOut();
        run.recordConversion(coins);
        java.math.BigDecimal total = java.math.BigDecimal.valueOf((long) coins * rate);
        java.math.BigDecimal share = total.divide(java.math.BigDecimal.valueOf(present.size()),
                0, java.math.RoundingMode.DOWN);
        if (share.signum() <= 0) {
            return;
        }
        for (ServerPlayer player : present) {
            es.boffmedia.teras.economy.EconomyStore.deposit(player.getUUID(), share);
            player.sendSystemMessage(Component.literal("§6" + coins + " monedas cambiadas — te llevas "
                    + share.toBigInteger() + " ₽."));
            es.boffmedia.teras.dungeon.run.DungeonTitles.send(player, "§6¡Mazmorra completada!",
                    "§7+" + share.toBigInteger() + " ₽");
        }
    }

    /** False when the run does not exist or is still building. */
    public static boolean end(MinecraftServer server, int runId) {
        DungeonRun run = RUNS.get(runId);
        if (run == null || run.state() != DungeonRun.State.ACTIVE) {
            return false;
        }
        RUNS.remove(runId);
        // Not completed: completeRun already reported before handing over, and markReported keeps
        // this from posting the same run a second time.
        report(run, false);
        RunEngine.unregister(runId);
        sendPartyHome(server, run);

        ServerLevel level = dungeonLevel(server);
        boolean discarding = level != null
                && DungeonMaterializer.enqueueDiscard(run.builtId(), level, () -> {
                    RunJournal.delete(run.id());
                    SLOTS.clear(run.slot());
                });
        if (!discarding) {
            Teras.LOGGER.warn("Dungeons: run {} had no floor to discard; freeing slot {}",
                    run.id(), run.slot());
            RunJournal.delete(run.id());
            SLOTS.clear(run.slot());
        }
        return true;
    }

    /**
     * Posts the run to SmartRotom, once, if the server asked for it. Failures are the backend's
     * problem: the party is already home and the coins already paid, so a lost row costs a
     * leaderboard entry and nothing else.
     */
    private static void report(DungeonRun run, boolean completed) {
        if (!run.markReported()) {
            return;
        }
        // Cleared whether or not anything is posted: leaving the flags behind would make a member
        // who walked out of one run read as having walked out of their next one.
        if (!DungeonsConfig.backendPostEnabled()) {
            run.names().keySet().forEach(abandoned::remove);
            return;
        }
        try {
            List<DungeonRunResult.Participant> participants = new java.util.ArrayList<>();
            for (Map.Entry<UUID, String> member : run.names().entrySet()) {
                participants.add(new DungeonRunResult.Participant(
                        member.getKey().toString(), member.getValue(),
                        run.stateOf(member.getKey()).deaths(),
                        abandoned.contains(member.getKey())));
            }
            List<String> curseNames = run.curses().stream().map(Enum::name).toList();
            es.boffmedia.teras.util.net.SmartRotomService.saveDungeonRun(new DungeonRunResult(
                    run.layout().seedString(), run.startStage(), run.stage(), run.stagesCleared(),
                    completed, System.currentTimeMillis() - run.startedAtMs(), curseNames,
                    run.wallet().totalEarned(), run.wallet().totalSpent(), run.coinsConverted(),
                    participants));
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not report run {}: {}", run.id(), e.toString());
        } finally {
            run.names().keySet().forEach(abandoned::remove);
        }
    }

    // --- boot sweep and stragglers -------------------------------------------------------------

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        pendingReturns = RunJournal.loadReturns();
        List<RunJournal.SweptRun> stale = RunJournal.readAll();
        for (RunJournal.SweptRun swept : stale) {
            nextRunId = Math.max(nextRunId, swept.id() + 1);
            pendingReturns.putAll(swept.party());
            ServerLevel level = levelByName(event.getServer(), swept.dimension());
            if (level == null) {
                Teras.LOGGER.warn("Dungeons: cannot sweep run {} — dimension {} missing; journal kept",
                        swept.id(), swept.dimension());
                continue;
            }
            Teras.LOGGER.info("Dungeons: sweeping stale run {} ({} cells)",
                    swept.id(), swept.cellOrigins().size());
            DungeonMaterializer.enqueueClear(level, swept.cellOrigins(),
                    swept.roomSize(), swept.roomHeight(), () -> RunJournal.delete(swept.id()));
        }
        if (!pendingReturns.isEmpty()) {
            RunJournal.saveReturns(pendingReturns);
        }
    }

    /**
     * A player logging in with a pending return goes home; one who wakes up stranded in the
     * dungeon dimension with no run and no pending return goes to the world spawn rather than
     * being left floating in the void.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DungeonRun.ReturnPoint pending = pendingReturns.remove(player.getUUID());
        if (pending != null) {
            RunJournal.saveReturns(pendingReturns);
            teleport(player, pending);
            return;
        }
        boolean inDungeonDim = player.serverLevel().dimension().location().toString()
                .equals(DungeonsConfig.dimension());
        if (inDungeonDim && runOf(player.getUUID()) == null) {
            ServerLevel overworld = player.getServer().overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            // Cleared here too: a crash mid-run leaves a devil deal's max-health modifier saved on
            // the player, and this is the path a stranded one comes back through.
            RunEngine.clearRunEffects(player);
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            RunEngine.land(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RUNS.clear();
        SLOTS.clear();
    }

    // --- helpers -------------------------------------------------------------------------------

    /** A slot holds two build pads so stage advances can build before tearing down. */
    static BlockPos padOrigin(int slot, int pad) {
        return es.boffmedia.teras.world.VoidZones.dungeonRunPad(slot, pad);
    }

    private static List<BlockPos> cellOrigins(DungeonLayout layout, BlockPos origin) {
        List<BlockPos> cells = new java.util.ArrayList<>();
        int roomSize = DungeonsConfig.roomSize();
        for (var room : layout.rooms()) {
            for (var cell : room.cells()) {
                cells.add(origin.offset(cell.x() * roomSize, 0, cell.y() * roomSize));
            }
        }
        return cells;
    }

    private static List<BlockPos> builtCellOrigins(BuiltDungeon built) {
        List<BlockPos> cells = new java.util.ArrayList<>();
        for (var room : built.layout().rooms()) {
            for (var cell : room.cells()) {
                cells.add(built.cellOrigin(cell));
            }
        }
        return cells;
    }

    private static void message(MinecraftServer server, DungeonRun run, String text) {
        for (java.util.UUID member : run.party().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(Component.literal(text));
            }
        }
    }

    private static ServerLevel dungeonLevel(MinecraftServer server) {
        return levelByName(server, DungeonsConfig.dimension());
    }

    private static ServerLevel levelByName(MinecraftServer server, String name) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(name));
        return server.getLevel(key);
    }

    private static DungeonRun.ReturnPoint returnPointOf(ServerPlayer player) {
        return new DungeonRun.ReturnPoint(
                player.serverLevel().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer().getName());
    }

    private static void teleportPartyIn(MinecraftServer server, DungeonRun run, BuiltDungeon built) {
        ServerLevel level = dungeonLevel(server);
        if (level == null) {
            return;
        }
        BlockPos start = built.roomCenter(built.layout().start());
        for (UUID member : run.party().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                // Clears the descent: gravity back on, and no fall distance carried into the
                // landing (arriving mid-drop from the floor above was fatal).
                RunEngine.land(player);
                // Always adventure, whatever they came in as — the floor is not the party's to
                // mine through or brick over. On every floor, not just the first: idempotent, and
                // it covers a member who talked an op into a mode change mid-run.
                player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
                // Blessings are a floor's purchase, not a run's: carrying them down would stack
                // three shops' worth of buffs onto the stages that are supposed to be hardest.
                es.boffmedia.teras.dungeon.run.DungeonShop.clearBlessings(player);
                // The hearts a devil deal took are a run-long debt, so they follow the party down.
                es.boffmedia.teras.dungeon.run.DungeonHealth.applyHpDebt(player,
                        run.stateOf(member).hpDebt());
                player.sendSystemMessage(Component.literal(
                        "§aMazmorra lista — etapa " + run.stage()
                                + ", semilla " + run.layout().seedString()));
                es.boffmedia.teras.dungeon.run.DungeonTitles.send(player,
                        "§ePiso " + run.stage(), "§7semilla " + run.layout().seedString());
            }
        }
    }

    private static void sendPartyHome(MinecraftServer server, DungeonRun run) {
        for (Map.Entry<UUID, DungeonRun.ReturnPoint> member : run.party().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.getKey());
            if (player != null) {
                teleport(player, member.getValue());
            } else {
                pendingReturns.put(member.getKey(), member.getValue());
                RunJournal.saveReturns(pendingReturns);
            }
        }
    }

    private static void teleport(ServerPlayer player, DungeonRun.ReturnPoint point) {
        ServerLevel level = levelByName(player.getServer(), point.dimension());
        if (level == null) {
            level = player.getServer().overworld();
        }
        // Before the teleport, not after: this is the only chokepoint every way out of a run goes
        // through, and a devil deal's missing hearts or a pocket of dungeon potions must not
        // arrive in the overworld with the player.
        RunEngine.clearRunEffects(player);
        player.teleportTo(level, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        // A run can end while its party is mid-descent, so going home has to clear the fall too.
        RunEngine.land(player);
        // The run forced adventure; home means their own mode again.
        player.setGameMode(net.minecraft.world.level.GameType.byName(
                point.gameMode(), net.minecraft.world.level.GameType.SURVIVAL));
    }
}
