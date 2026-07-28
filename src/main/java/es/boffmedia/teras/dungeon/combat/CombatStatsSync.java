package es.boffmedia.teras.dungeon.combat;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.run.DungeonHealth;
import es.boffmedia.teras.net.CombatStatsPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps each player's stat panel truthful, at the smallest cost that manages it.
 *
 * <h2>Why polled and diffed rather than pushed</h2>
 *
 * <p>A sheet is a <i>function</i> of equipo, reliquias and bendiciones ({@link StatBlock}), and those
 * change from a dozen unrelated places — a swapped weapon, an absorbed pickup, a sold heart, a curse
 * landing. Pushing from each of them means every future one of those places has to remember to push,
 * and the failure is silent: a panel that is quietly stale reads exactly like a panel that is right. So
 * this reads the sheet on a slow tick and sends only when a number actually moved, which costs one
 * comparison a second and cannot be forgotten by code that does not exist yet.</p>
 *
 * <h2>Why it walks the runs and not the player list</h2>
 *
 * <p>It used to iterate every online player, which meant a hundred-player server ran a hundred
 * {@code isInRun} checks a second — each of them a linear scan over active runs — to serve four panels,
 * and sent every one of those players an empty payload once just to find out they were not playing.
 * Walking the parties asks only about players who can possibly have a sheet.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class CombatStatsSync {
    private CombatStatsSync() {}

    /** A second between reads. Stats do not change fast enough to be worth more. */
    private static final int INTERVAL_TICKS = 20;

    /** The last thing each player was told, so nothing unchanged is sent twice. */
    private static final Map<UUID, List<Float>> SENT = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        if (!DungeonsConfig.combatEnabled()) {
            // Nothing to show and nothing to clear: a panel only ever appeared if this was on.
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (DungeonRun run : DungeonRunManager.runs()) {
            for (UUID id : run.party().keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) {
                    continue;
                }
                seen.add(id);
                send(player, DungeonHealth.isInRun(player) ? sheetOf(player) : List.of());
            }
        }
        // A player who left a run this second is no longer in any party, so the loop above cannot
        // clear their panel — this is what does. Once cleared they drop out of SENT entirely and cost
        // nothing until they start another run.
        SENT.keySet().removeIf(id -> {
            if (seen.contains(id)) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, CombatStatsPayload.hidden());
            }
            return true;
        });
    }

    private static void send(ServerPlayer player, List<Float> values) {
        if (values.equals(SENT.get(player.getUUID()))) {
            return;
        }
        SENT.put(player.getUUID(), values);
        PacketDistributor.sendToPlayer(player, new CombatStatsPayload(values));
    }

    /** Every stat in enum order — read positionally by the client, so order is the contract. */
    private static List<Float> sheetOf(ServerPlayer player) {
        StatBlock sheet = CombatSheets.of(player);
        List<Float> values = new ArrayList<>(Stat.values().length);
        for (Stat stat : Stat.values()) {
            // Escudo is the one stat the panel shows as a BALANCE rather than as a sheet value: the
            // sheet says how much the player's gear has granted them, and what they want to see is how
            // much of it is left. Every other stat is a property; this one is a resource.
            values.add(stat == Stat.ESCUDO
                    ? (float) Escudo.current(player)
                    : (float) sheet.get(stat));
        }
        return values;
    }

    /**
     * Takes the panel down and forgets the player.
     *
     * <p><b>Both halves, and in that order.</b> This was a bare {@code SENT.remove}, which is why the
     * panel survived walking out of a dungeon: dropping the entry is exactly what stops the tick loop
     * above from noticing the player needs clearing, so the last sheet they were sent stayed on screen
     * until they disconnected. Forgetting a player and telling them to forget are different jobs and
     * the exit path needs both.</p>
     */
    public static void clear(ServerPlayer player) {
        SENT.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, CombatStatsPayload.hidden());
    }

    /** Forgets what a player was last sent, so their next sheet is sent in full rather than diffed. */
    public static void forget(UUID player) {
        SENT.remove(player);
    }
}
