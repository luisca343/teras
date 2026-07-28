package es.boffmedia.teras.dungeon.instance;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.BuiltDungeon;
import es.boffmedia.teras.dungeon.build.DungeonMaterializer;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.gen.DungeonGenerationException;
import es.boffmedia.teras.dungeon.gen.DungeonGenerator;
import es.boffmedia.teras.dungeon.gen.GenConfig;
import es.boffmedia.teras.dungeon.gen.FloorDepth;
import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.piso.DungeonDef;
import es.boffmedia.teras.dungeon.piso.FloorPlan;
import es.boffmedia.teras.dungeon.piso.FloorSelector;
import es.boffmedia.teras.dungeon.piso.PisoCatalog;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.run.DungeonTitles;
import es.boffmedia.teras.dungeon.run.RunEngine;
import es.boffmedia.teras.dungeon.run.RunPartyHelper;
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
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DungeonRunManager {
    private DungeonRunManager() {}

    private static final Map<Integer, DungeonRun> RUNS = new LinkedHashMap<>();
    /** Player to run id, so {@link #runOf} is a lookup rather than a scan over every party. */
    private static final RunIndex MEMBERSHIP = new RunIndex();
    private static final SlotTable SLOTS = new SlotTable();
    /** How often the watchdog looks; a second is far below either timeout and costs nothing. */
    private static final int WATCH_INTERVAL_TICKS = 20;
    private static RunWatchdog watchdog = new RunWatchdog(180 * 20, 60 * 20);
    private static Map<UUID, RunJournal.TimestampedReturn> pendingReturns = new LinkedHashMap<>();
    /** Members who walked out on a run, so the report can tell them from those who saw it through. */
    private static final Set<UUID> abandoned = new java.util.HashSet<>();
    /**
     * Players whose ascensor score is still owed, written on a later tick rather than at login.
     *
     * <p>A scoreboard write during {@code PlayerLoggedInEvent} runs inside
     * {@code PlayerList.placeNewPlayer}, where anything that throws kicks the player with "Invalid
     * player data" on every attempt. {@link #onLoginSeedScores} is what makes CustomNPCs' listener
     * not throw, and the value write is still kept out of the join for belt and braces. Deferring
     * costs nothing: nothing reads this until the player talks to el Guardián.</p>
     */
    private static final Set<UUID> pendingScores = new java.util.LinkedHashSet<>();
    private static int nextRunId = 1;

    public record StartOutcome(DungeonRun run, String error) {
        static StartOutcome fail(String error) {
            return new StartOutcome(null, error);
        }
    }

    public static Collection<DungeonRun> runs() {
        return List.copyOf(RUNS.values());
    }

    /**
     * The run {@code player} belongs to, or null.
     *
     * <p><b>Indexed, because this is one of the hottest lookups in the mod.</b> It used to stream every
     * active run's party map, which was affordable when it answered a command and stopped being
     * affordable once the rebuilt combat loop started asking per hit, per right-click and — through
     * {@code DungeonHealth.isInRun} — per player per second. Twenty parties of four is eighty entries
     * scanned, hundreds of times a second, to answer a question a hash lookup answers.</p>
     *
     * <p>{@link #MEMBERSHIP} is maintained through {@link RunIndex} at the only four
     * places membership can change, all of them in this class: a run starting, a member leaving, a run
     * ending, and a stuck build being failed. {@code party()} is handed out mutable and nothing outside
     * this class mutates it — which is a rule the index now depends on rather than merely a fact, so it
     * is stated here.</p>
     */
    public static DungeonRun runOf(UUID player) {
        Integer runId = MEMBERSHIP.runIdOf(player);
        return runId == null ? null : RUNS.get(runId);
    }

    /**
     * Starts a run for {@code members} (the leader among them); one slot, one shared floor.
     *
     * @param dungeonId which mazmorra to descend; its tramos decide the length and what each floor
     *                  can be
     * @param forced    curses applied on top of the rolled ones, bypassing the piso's preferences.
     *                  The admin test commands' lab/lost switches — deliberately not filtered, so
     *                  forcing a curse to see it actually shows it
     */
    public static StartOutcome start(ServerPlayer leader, Collection<ServerPlayer> members,
                                     String dungeonId, int stage, Set<Curse> forced, String seed) {
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
        // Checked before the floor is generated, claimed after it: a full lattice must refuse the
        // command without having done a generator's worth of work first.
        if (SLOTS.nextFree(DungeonsConfig.maxSlots()) < 0) {
            return StartOutcome.fail("No hay huecos de instancia libres.");
        }

        DungeonDef dungeon = PisoCatalog.dungeon(dungeonId);
        if (dungeon == null) {
            return StartOutcome.fail("No existe la mazmorra '" + dungeonId + "'.");
        }
        if (!dungeon.isValidStage(stage)) {
            return StartOutcome.fail(dungeon.nombre() + " tiene " + dungeon.length()
                    + " pisos; no existe el " + stage + ".");
        }
        // Resolved before selection, not inside the generator: the piso is drawn from this seed too,
        // so an invented one has to exist before anything is chosen from it.
        String runSeed = (seed == null || seed.isBlank())
                ? Long.toUnsignedString(java.util.concurrent.ThreadLocalRandom.current().nextLong(), 36)
                : seed;
        FloorPlan plan = planFor(dungeon, stage, runSeed, forced);
        if (plan == null) {
            return StartOutcome.fail("Ningún piso utilizable para el piso " + stage
                    + " de " + dungeon.nombre() + " — revisa el log.");
        }

        DungeonLayout layout;
        try {
            layout = DungeonGenerator.generate(es.boffmedia.teras.dungeon.build.DungeonsConfig.genConfig()
                            .withShapeWeights(plan.piso().pesoFormas())
                            .withExitRoom(es.boffmedia.teras.dungeon.build.RoomTemplates
                                    .hasExitRoom(plan.piso()))
                            .withForceBossQuad(plan.piso().shapes()
                                    .contains(es.boffmedia.teras.dungeon.model.RoomShape.QUAD)),
                    FloorDepth.ofStage(es.boffmedia.teras.dungeon.build.DungeonsConfig.genConfig(), dungeon.primerPiso(), stage),
                    plan.curses(), plan.piso().shapes(), runSeed,
                    // Nobody has played anything yet, so the ledger is empty and the floor blank —
                    // which still leaves him his base chance. He may visit the first floor.
                    satellitesFor(es.boffmedia.teras.dungeon.gen.SatelliteOdds.Ledger.empty(),
                            es.boffmedia.teras.dungeon.gen.SatelliteOdds.FloorOutcome.fresh(), plan));
        } catch (DungeonGenerationException e) {
            return StartOutcome.fail("Generación fallida: " + e.getMessage());
        } catch (RuntimeException e) {
            // A config that drifted under a dungeon def — a shortened curve, a window reaching past
            // the canonical sequence — arrives here as an IllegalArgumentException, not as the
            // generator's own exception. Load-time validation is supposed to catch it; a refused
            // start beats an unhandled throw out of the command.
            Teras.LOGGER.error("Dungeons: generating stage {} of '{}' threw", stage, dungeonId, e);
            return StartOutcome.fail("Generación fallida: revisa el log.");
        }
        logWarnings(layout, dungeonId, stage);

        int slot = SLOTS.allocate(DungeonsConfig.maxSlots());
        if (slot < 0) {
            return StartOutcome.fail("No hay huecos de instancia libres.");
        }
        DungeonRun run = new DungeonRun(nextRunId++, slot, dungeonId, stage, plan, layout);
        for (ServerPlayer member : members) {
            run.party().put(member.getUUID(), returnPointOf(member));
            run.names().put(member.getUUID(), member.getName().getString());
        }
        RUNS.put(run.id(), run);
        MEMBERSHIP.index(run.party().keySet(), run.id());

        BlockPos origin = padOrigin(slot, 0);
        RunJournal.write(run.id(), DungeonsConfig.dimension(),
                DungeonsConfig.roomSize(), DungeonsConfig.roomHeight(),
                cellOrigins(layout, origin), run.party());

        MinecraftServer server = leader.getServer();
        DungeonMaterializer.enqueueBuild(level, layout, plan, origin, built -> {
            run.activate(built.id());
            // The whole party can quit or log out during the second or two the floor takes to
            // build; a run with nobody in it must fold instead of standing registered forever.
            if (run.party().isEmpty()) {
                end(server, run.id());
                return;
            }
            RunEngine.register(run, built, level);
            teleportPartyIn(server, run, built);
        }, () -> failStuck(server, run, "la construcción falló"),
                new es.boffmedia.teras.dungeon.piso.VariantDraw(
                        es.boffmedia.teras.dungeon.model.DungeonSeeds.fnv1a64(layout.seedString()),
                        run.claimPisoOrdinal(plan.piso().id())));
        return new StartOutcome(run, null);
    }

    /**
     * The plan for one floor: which piso, and the curses it accepts. Forced curses are added after
     * selection so an admin switch is never silently dropped by a piso that refuses it.
     */
    private static FloorPlan planFor(DungeonDef dungeon, int stage, String seed, Set<Curse> forced) {
        FloorPlan plan = FloorSelector.select(dungeon, PisoCatalog.pisos(), stage, seed,
                DungeonsConfig.curseChances());
        if (plan == null || forced == null || forced.isEmpty()) {
            return plan;
        }
        Set<Curse> combined = java.util.EnumSet.noneOf(Curse.class);
        combined.addAll(plan.curses());
        combined.addAll(forced);
        return new FloorPlan(plan.stage(), plan.dungeonId(), plan.tierIndex(), plan.indexInTier(),
                plan.piso(), plan.dificultad(), combined, plan.jefes(), plan.minijefes(),
                plan.tramoBoundary());
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
        MEMBERSHIP.remove(player.getUUID());
        abandoned.add(player.getUUID());
        if (point != null) {
            teleport(player, point);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                es.boffmedia.teras.net.DungeonMapPayload.hidden());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                es.boffmedia.teras.net.DungeonWalletPayload.hidden());
        RunPartyHelper.message(player.getServer(), run,
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
    /**
     * The validator's soft findings, to the log.
     *
     * <p>They used to reach only {@code /teras dungeon generar}, an admin typing a command by hand —
     * so on the path every real floor takes they were computed, carried into the layout and dropped.
     * "Final floor without CHALLENGE room" fires on about half of all finales and nobody had ever
     * seen one.</p>
     */
    private static void logWarnings(DungeonLayout layout, String dungeonId, int stage) {
        for (String warning : layout.warnings()) {
            Teras.LOGGER.warn("Dungeons: floor {} of '{}' (seed {}): {}",
                    stage, dungeonId, layout.seedString(), warning);
        }
    }

    /**
     * The run-long half of the Acreedor/Orden odds, read off the run (PISOS §63c). Selling hearts
     * is asked of the whole party, not the borrower: he knows a customer when he sees one, and
     * bodies are personal but reputation is not.
     */
    private static es.boffmedia.teras.dungeon.gen.SatelliteOdds.Ledger ledgerOf(DungeonRun run) {
        boolean soldHearts = run.playerStates().values().stream()
                .anyMatch(state -> state.hpDebt() > 0);
        return new es.boffmedia.teras.dungeon.gen.SatelliteOdds.Ledger(
                run.acreedorDeals(), run.acreedorRefusals(), run.ordenCommitted(),
                run.deuda(), soldHearts);
    }

    /**
     * Turns the odds into this floor's chances, after the two content gates: both satellites hang
     * off the sala del sello, so a piso without an {@code exit} template gets neither (it keeps its
     * playfield devil room instead), and la Orden additionally needs her own template — absence is
     * a choice, never a validation failure.
     */
    private static es.boffmedia.teras.dungeon.gen.SatelliteChances satellitesFor(
            es.boffmedia.teras.dungeon.gen.SatelliteOdds.Ledger ledger,
            es.boffmedia.teras.dungeon.gen.SatelliteOdds.FloorOutcome floor, FloorPlan plan) {
        boolean exit = es.boffmedia.teras.dungeon.build.RoomTemplates.hasExitRoom(plan.piso());
        int acreedor = exit
                ? es.boffmedia.teras.dungeon.gen.SatelliteOdds.acreedor(ledger, floor) : 0;
        int orden = exit
                && es.boffmedia.teras.dungeon.build.RoomTemplates.hasOrdenRoom(plan.piso())
                ? es.boffmedia.teras.dungeon.gen.SatelliteOdds.orden(ledger, floor) : 0;
        return new es.boffmedia.teras.dungeon.gen.SatelliteChances(acreedor, orden);
    }

    public static void advanceStage(DungeonRun run, ServerLevel level) {
        if (run.state() != DungeonRun.State.ACTIVE || RUNS.get(run.id()) != run) {
            return;
        }
        MinecraftServer server = level.getServer();
        int next = run.stage() + 1;
        DungeonDef dungeon = PisoCatalog.dungeon(run.dungeonId());
        if (dungeon == null) {
            Teras.LOGGER.error("Dungeons: run {} is in mazmorra '{}', which is no longer loaded — "
                    + "ending it rather than descending into nothing", run.id(), run.dungeonId());
            completeRun(server, run);
            return;
        }
        if (next > dungeon.length()) {
            completeRun(server, run);
            return;
        }

        DungeonLayout newLayout;
        FloorPlan plan;
        try {
            // Rolled afresh for this floor: curses are per floor, and each piso only accepts the
            // ones it declares.
            plan = planFor(dungeon, next, run.layout().seedString(), Set.of());
            if (plan == null) {
                Teras.LOGGER.error("Dungeons: no usable piso for floor {} of '{}' — ending run {}",
                        next, dungeon.id(), run.id());
                completeRun(server, run);
                return;
            }
            newLayout = DungeonGenerator.generate(es.boffmedia.teras.dungeon.build.DungeonsConfig.genConfig()
                            .withShapeWeights(plan.piso().pesoFormas())
                            .withExitRoom(es.boffmedia.teras.dungeon.build.RoomTemplates
                                    .hasExitRoom(plan.piso()))
                            .withForceBossQuad(plan.piso().shapes()
                                    .contains(es.boffmedia.teras.dungeon.model.RoomShape.QUAD)),
                    FloorDepth.ofStage(es.boffmedia.teras.dungeon.build.DungeonsConfig.genConfig(), dungeon.primerPiso(), next),
                    plan.curses(), plan.piso().shapes(), run.layout().seedString(),
                    // Read now, at the descent — the one moment every input is finally known: the
                    // floor just played has been scored and any deal or refusal on it recorded.
                    satellitesFor(ledgerOf(run), run.lastFloorOutcome(), plan));
        } catch (DungeonGenerationException e) {
            Teras.LOGGER.error("Dungeons: could not generate stage {} of run {}: {}",
                    next, run.id(), e.getMessage());
            completeRun(server, run);
            return;
        } catch (RuntimeException e) {
            // Same net as start(), and it matters more here: this runs from the trapdoor, inside a
            // tick, on a live party. An unhandled throw would leave them standing on a floor whose
            // successor never came.
            Teras.LOGGER.error("Dungeons: generating stage {} of run {} threw", next, run.id(), e);
            completeRun(server, run);
            return;
        }
        logWarnings(newLayout, run.dungeonId(), next);

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
        RunPartyHelper.message(server, run, "§7Descendiendo al piso " + next + "…");

        FloorPlan floorPlan = plan;
        DungeonMaterializer.enqueueBuild(level, newLayout, floorPlan, newOrigin, built -> {
            run.enterFloor(floorPlan);
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
        }, () -> failStuck(server, run, "no se pudo construir el piso " + next),
                new es.boffmedia.teras.dungeon.piso.VariantDraw(
                        es.boffmedia.teras.dungeon.model.DungeonSeeds.fnv1a64(
                                newLayout.seedString()),
                        run.claimPisoOrdinal(floorPlan.piso().id())));
    }

    /**
     * The party rides el ascensor out, wherever they had got to.
     *
     * <p><b>A completion, not an abandonment</b>, which is the whole design: coins become ₽ exactly
     * as they do at the bottom of a dungeon, so leaving early is banking rather than forfeiting and
     * the choice at the lift is a real one. Abandoning still exists and still pays nothing — that is
     * {@code leave}, and it is what walking out of the run does.</p>
     */
    public static void extract(MinecraftServer server, DungeonRun run) {
        if (run == null || run.state() != DungeonRun.State.ACTIVE || RUNS.get(run.id()) != run) {
            return;
        }
        // stage() is the floor they are STANDING on, and taking the lift is precisely the case where
        // they have not cleared it — see completeRun.
        completeRun(server, run, run.stage() - 1);
    }

    private static void completeRun(MinecraftServer server, DungeonRun run) {
        completeRun(server, run, run.stage());
    }

    /**
     * Ends the run and pays it out, reporting {@code stagesBeaten} as the depth reached.
     *
     * <h2>Why the depth is a parameter</h2>
     *
     * <p>The two ways in disagree about what {@code run.stage()} means. Coming from
     * {@link #advanceStage}, the descent has been refused for running out of dungeon, so the stage
     * they are on is the stage they beat. Coming from {@link #extract}, they are standing on a floor
     * the lift is carrying them out of — <b>the one floor they did not clear</b>. Reading
     * {@code stage()} in both places claimed one etapa more than the party had earned.</p>
     */
    private static void completeRun(MinecraftServer server, DungeonRun run, int stagesBeaten) {
        String seed = " con semilla " + run.layout().seedString() + ".";
        RunPartyHelper.message(server, run, stagesBeaten <= 0
                ? "§6Salís de la mazmorra. §7Ninguna etapa superada" + seed
                : "§6¡Mazmorra completada! Etapa " + stagesBeaten + " superada" + seed);
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
        List<ServerPlayer> present = RunPartyHelper.onlineMembers(server, run);
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
        MEMBERSHIP.unindex(runId);
        watchdog.forget(runId);
        // Not completed: completeRun already reported before handing over, and markReported keeps
        // this from posting the same run a second time.
        report(run, false);
        RunEngine.unregister(runId);
        sendPartyHome(server, run);
        sweepFloor(server, run);
        return true;
    }

    /**
     * Clears the floor a run was standing on and only then forgets it.
     *
     * <p><b>The journal is never deleted by a path that did not clear anything.</b> This used to
     * delete it whenever {@code enqueueDiscard} came back false — no {@code BuiltDungeon} for that
     * id, or the dungeon dimension missing — which threw away the one record of where the floor was.
     * Anything left standing at that point was then unreachable by every mechanism there is: the run
     * was gone from memory, the journal was gone from disk, and the boot sweep had nothing to read.
     * A false discard is exactly when the record matters most.</p>
     */
    private static void sweepFloor(MinecraftServer server, DungeonRun run) {
        ServerLevel level = dungeonLevel(server);
        if (level == null) {
            // The slot is freed anyway — holding it changes nothing when there is no dimension to
            // build in — but the journal stays for a boot that has the dimension back.
            Teras.LOGGER.error("Dungeons: dimension {} is missing; run {}'s floor is left to the "
                    + "boot sweep", DungeonsConfig.dimension(), run.id());
            SLOTS.free(run.slot());
            return;
        }
        Runnable done = () -> {
            RunJournal.delete(run.id());
            SLOTS.free(run.slot());
        };
        if (DungeonMaterializer.enqueueDiscard(run.builtId(), level, done)) {
            return;
        }
        Teras.LOGGER.warn("Dungeons: run {} has no built floor {}; clearing its pad from the "
                + "layout instead", run.id(), run.builtId());
        DungeonMaterializer.enqueueClear(level,
                cellOrigins(run.layout(), padOrigin(run.slot(), run.padIndex())),
                DungeonsConfig.roomSize(), DungeonsConfig.roomHeight(), done);
    }

    /**
     * A run that will never become ACTIVE: its build died, or it has been waiting on one for longer
     * than any build takes. {@link #end} cannot help — it only ends ACTIVE runs — so this is the
     * only path that frees the slot and clears whatever was written before the failure.
     *
     * <p>Both pads, at full generator size, because there is no {@link BuiltDungeon} to ask what was
     * actually pasted and a stage advance has rooms on both. Wasteful and certain, which is the
     * right trade for a path that only runs when something has already gone wrong.</p>
     */
    private static void failStuck(MinecraftServer server, DungeonRun run, String why) {
        if (RUNS.remove(run.id()) == null) {
            return;
        }
        MEMBERSHIP.unindex(run.id());
        Teras.LOGGER.error("Dungeons: run {} failed ({}) — clearing slot {}", run.id(), why,
                run.slot());
        watchdog.forget(run.id());
        RunEngine.unregister(run.id());
        RunPartyHelper.message(server, run, "§cLa mazmorra ha fallado; volvéis a casa.");
        sendPartyHome(server, run);
        ServerLevel level = dungeonLevel(server);
        if (level == null) {
            SLOTS.free(run.slot());
            return;
        }
        int grid = es.boffmedia.teras.dungeon.build.DungeonsConfig.genConfig().gridSize();
        DungeonMaterializer.enqueuePadClear(level, padOrigin(run.slot(), 0), grid, () -> { });
        DungeonMaterializer.enqueuePadClear(level, padOrigin(run.slot(), 1), grid, () -> {
            RunJournal.delete(run.id());
            SLOTS.free(run.slot());
        });
    }

    /**
     * The safety net for every way a party can stop being in a dungeon without saying so.
     *
     * <p>Nothing listened for a disconnect, so a run whose party dropped out stayed ACTIVE forever:
     * its slot held, its floor standing, its shop displays and reward pedestals in the world, and no
     * player left who could ever end it. Polling the state — is anyone online? — rather than
     * listening for an event catches kicks, client crashes and timeouts too, and cannot be bypassed
     * by a future way of leaving that fires no event.</p>
     */
    /** False until the first server tick has created whatever objectives the world was missing. */
    private static boolean objectivesEnsured;

    /**
     * Creates the dungeon's scoreboard objectives, once, on the <b>first server tick</b>.
     *
     * <p>The first tick is after every mod has started and before any player can join, so nobody
     * is connected to receive a packet about an objective appearing and CustomNPCs is fully up.
     * {@code ServerStartedEvent} would be earlier than CustomNPCs' own start.</p>
     */
    private static void ensureObjectivesOnce(MinecraftServer server) {
        if (objectivesEnsured) {
            return;
        }
        objectivesEnsured = true;
        es.boffmedia.teras.dungeon.run.DungeonNpcs.ensureObjectives(server);
        es.boffmedia.teras.dungeon.run.DungeonObjectives.ensure(
                server, List.of(ElevatorAccess.OBJECTIVE));
    }

    /** Every objective a Teras dialogue may condition on, seeded together at login. */
    private static List<String> conditionedObjectives() {
        List<String> all = new java.util.ArrayList<>(
                es.boffmedia.teras.dungeon.run.DungeonNpcs.objectives());
        all.add(ElevatorAccess.OBJECTIVE);
        return all;
    }

    /**
     * Gives this player's conditioned scores a display name and a number format — see
     * {@code DungeonObjectives.seed}.
     *
     * <p><b>After CustomNPCs' login handler</b>, deliberately. That handler is what tells the client
     * these objectives exist, and a score written before it arrives is a score for an objective the
     * client does not have yet, which it can only log and drop. Seeding is no longer what keeps
     * CustomNPCs from throwing on the null — {@code CnpcScoreSyncMixin} fixes that at the call — so
     * there is nothing left that wants it early.</p>
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onLoginSeedScores(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            es.boffmedia.teras.dungeon.run.DungeonObjectives.seed(player, conditionedObjectives());
        }
    }

    /** Writes the ascensor scores owed since the last logins, off the join and on a plain tick. */
    private static void drainPendingScores(MinecraftServer server) {
        if (pendingScores.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(pendingScores)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                // Gone again before the tick came round; there is nothing owed to nobody.
                pendingScores.remove(id);
                continue;
            }
            ElevatorAccess.publish(player);
            pendingScores.remove(id);
        }
    }

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ensureObjectivesOnce(server);
        drainPendingScores(server);
        if (RUNS.isEmpty() || server.getTickCount() % WATCH_INTERVAL_TICKS != 0) {
            return;
        }
        long tick = server.getTickCount();
        for (DungeonRun run : List.copyOf(RUNS.values())) {
            boolean building = run.state() != DungeonRun.State.ACTIVE;
            switch (watchdog.check(run.id(), building, RunPartyHelper.anyOnline(server, run), tick)) {
                case END_DESERTED -> {
                    Teras.LOGGER.info("Dungeons: run {} has had nobody online for {}s — ending it",
                            run.id(), DungeonsConfig.desertionGraceSeconds());
                    end(server, run.id());
                }
                case FAIL_STUCK -> failStuck(server, run, "la construcción nunca terminó");
                case HEALTHY -> { }
            }
        }
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
            List<String> curseNames = run.cursesSeen().stream().map(Enum::name).toList();
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
        // Built here rather than at class load: the config is only read once the server exists.
        watchdog = new RunWatchdog(DungeonsConfig.desertionGraceSeconds() * 20L,
                DungeonsConfig.buildTimeoutSeconds() * 20L);
        pendingReturns = RunJournal.loadReturns();
        purgeStaleReturns();
        ElevatorAccess.load();
        // The objectives are NOT created here — see ensureObjectivesOnce.
        objectivesEnsured = false;
        List<RunJournal.SweptRun> stale = RunJournal.readAll();
        for (RunJournal.SweptRun swept : stale) {
            nextRunId = Math.max(nextRunId, swept.id() + 1);
            for (Map.Entry<UUID, DungeonRun.ReturnPoint> member : swept.party().entrySet()) {
                pendingReturns.put(member.getKey(), timestamped(member.getValue()));
            }
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
        // Queued, NOT written here: see pendingScores. Everyone gets one, not only dungeon
        // players — a missing objective reads to CustomNPCs as unavailable rather than as zero, so
        // a player who has never entered would find el Guardián with no descent options at all.
        pendingScores.add(player.getUUID());
        RunJournal.TimestampedReturn pending = pendingReturns.remove(player.getUUID());
        if (pending != null) {
            RunJournal.saveReturns(pendingReturns);
            teleport(player, pending.point());
            return;
        }
        // Cheaper than a timer and it fires often enough: a server with players logging in is the
        // only one whose returns file is growing.
        if (purgeStaleReturns()) {
            RunJournal.saveReturns(pendingReturns);
        }
        boolean inDungeonDim = player.serverLevel().dimension().location().toString()
                .equals(DungeonsConfig.dimension());
        if (inDungeonDim && runOf(player.getUUID()) == null) {
            ServerLevel overworld = player.getServer().overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            // Cleared here too: a crash mid-run leaves a devil deal's max-health modifier saved on
            // the player, and this is the path a stranded one comes back through.
            RunEngine.clearRunEffects(player);
            // Same case, one system later: a player benched out of the expedition is a spectator,
            // and a crash takes RunEngine's record of that with it. Only spectators are touched, so
            // an operator who flew in to look at a stranded dungeon keeps their own mode.
            if (player.gameMode.getGameModeForPlayer()
                    == net.minecraft.world.level.GameType.SPECTATOR) {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            RunEngine.land(player);
        }
    }

    /**
     * A clean shutdown leaves a clean world.
     *
     * <p>This used to drop the run table on the floor: the dungeons stayed built, their shops and
     * pedestals stayed standing, and the world was saved that way. It survived only because the run
     * journal made the <i>next</i> boot sweep them — a restart's worth of orphan geometry that
     * happened to be cleaned up later. Ending every run properly here means the shutdown itself
     * despawns the displays, sends the party home, and queues the floors; the materializer then
     * finishes those discards synchronously at {@link net.neoforged.bus.api.EventPriority#LOWEST}.
     * The journal stays as it was: the net for a <b>crash</b>, which never reaches this event.</p>
     *
     * <p>Fires at {@link net.neoforged.bus.api.EventPriority#HIGHEST} so the runs are ended before
     * anything else tears its own state down. Safe because NeoForge posts this from the tick loop,
     * before {@code stopServer()} saves the players and the chunks.</p>
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        for (DungeonRun run : List.copyOf(RUNS.values())) {
            if (run.state() == DungeonRun.State.ACTIVE) {
                end(event.getServer(), run.id());
            } else {
                // Mid-build: nothing to discard that the journal does not already cover, but the
                // party of a run caught mid-descent is standing on the floor below and has to be
                // put somewhere real before the world is saved.
                RunEngine.unregister(run.id());
                sendPartyHome(event.getServer(), run);
            }
        }
        RUNS.clear();
        MEMBERSHIP.clear();
        SLOTS.clear();
        watchdog.clear();
    }

    // --- helpers -------------------------------------------------------------------------------

    private static RunJournal.TimestampedReturn timestamped(DungeonRun.ReturnPoint point) {
        return new RunJournal.TimestampedReturn(point, System.currentTimeMillis());
    }

    /**
     * Drops return points nobody has come back for.
     *
     * <p>An entry is only ever consumed by its owner logging in, so a player who leaves and never
     * returns leaves one behind for good — cheap on its own, unbounded over a server's lifetime.
     * Expiring one costs that player a teleport home they were never going to collect; they still
     * land wherever they logged out, and the stranded-in-the-void path below still catches them.</p>
     *
     * @return whether anything was purged, so the caller knows to rewrite the file
     */
    private static boolean purgeStaleReturns() {
        long days = DungeonsConfig.returnExpiryDays();
        if (days <= 0 || pendingReturns.isEmpty()) {
            return false;
        }
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        int before = pendingReturns.size();
        pendingReturns.values().removeIf(entry -> entry.savedAtMs() < cutoff);
        int purged = before - pendingReturns.size();
        if (purged > 0) {
            Teras.LOGGER.info("Dungeons: purged {} stale return points (older than {} days)",
                    purged, days);
        }
        return purged > 0;
    }

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

    /**
     * The floor's own name on arrival — "Cuevas II" over the piso's flavour line, with its music and
     * ambience. The numeral is depth within the tramo, so two Cuevas floors in a row read as I and
     * II rather than as the same place twice.
     */
    private static void announceFloor(ServerPlayer player, DungeonRun run) {
        var plan = run.plan();
        if (plan == null) {
            return;
        }
        DungeonTitles.send(player, "§6" + plan.title(),
                plan.subtitle().isBlank() ? "" : "§7" + plan.subtitle());
        playPisoSound(player, plan.piso().musica());
        playPisoSound(player, plan.piso().ambiente());
    }

    /**
     * Plays a piso's sound if the id names one. The fields are resource locations either way, so
     * swapping a vanilla track for a custom one later is a config edit, not a code change.
     */
    private static void playPisoSound(ServerPlayer player, String soundId) {
        if (soundId == null || soundId.isBlank()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        if (id == null) {
            return;
        }
        var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
        if (sound == null) {
            Teras.LOGGER.warn("Dungeons: piso sound '{}' is not a registered sound event", soundId);
            return;
        }
        player.playNotifySound(sound, net.minecraft.sounds.SoundSource.AMBIENT, 0.7f, 1.0f);
    }

    private static void teleportPartyIn(MinecraftServer server, DungeonRun run, BuiltDungeon built) {
        ServerLevel level = dungeonLevel(server);
        if (level == null) {
            return;
        }
        BlockPos start = built.partySpawn(built.layout().start());
        for (ServerPlayer player : RunPartyHelper.onlineMembers(server, run)) {
            player.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            announceFloor(player, run);
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
            // The hearts a devil deal took, and any Pulso débil accepted at a curse room, are
            // a run-long debt: they follow the party down.
            es.boffmedia.teras.dungeon.run.Afflictions.apply(run, player);
            // Seed and stage to chat only. This used to also send a title, which fired after
            // announceFloor and overwrote "Cuevas I" with "Piso 1 / semilla" — the floor's own
            // name is the title, the seed is a chat aside.
            player.sendSystemMessage(Component.literal(
                    "§7Mazmorra lista — etapa " + run.stage()
                            + ", semilla " + run.layout().seedString()));
        }
    }

    private static void sendPartyHome(MinecraftServer server, DungeonRun run) {
        for (Map.Entry<UUID, DungeonRun.ReturnPoint> member : run.party().entrySet()) {
            ServerPlayer player = RunPartyHelper.playerOf(server, member.getKey());
            if (player != null) {
                teleport(player, member.getValue());
            } else {
                pendingReturns.put(member.getKey(), timestamped(member.getValue()));
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
