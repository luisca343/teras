package es.boffmedia.teras.dungeon.combat;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.run.DungeonHealth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Escudo: temporary hearts that absorb first and are never healed back (ROGUELIKE §4.4).
 *
 * <h2>Why it is vanilla's absorption and not a pool of our own</h2>
 *
 * <p>Absorption already does exactly what the design asks — it is spent before health, it is not
 * touched by healing, and it <b>draws yellow hearts above the red ones</b>. A first-party pool would
 * have needed its own subtraction in the damage path and its own HUD row, and a shield you cannot see
 * on your health bar is a shield players do not know they have. Rule 3: an effect nobody can see is an
 * effect nobody learns.</p>
 *
 * <h2>The one place that has to opt out</h2>
 *
 * <p>Because absorption is consumed by vanilla's own damage application, it would otherwise pay the
 * dungeon's <b>prices</b> as well — the curse toll, the sacrifice bite, chest spikes. That is the
 * inversion {@link CombatEngine} is careful to avoid: a price must cost the same however well equipped
 * you are. {@code RunEngine.chargeToll} therefore sets absorption aside for the length of the charge
 * and puts it back afterwards, so escudo covers <i>fights</i> and nothing else.</p>
 *
 * <h2>Granting</h2>
 *
 * <p>{@link EscudoState#owed} is the rule and holds the reasoning; {@link #GRANTED} is this run's
 * high-water mark per player.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class Escudo {
    private Escudo() {}

    /** A second between grants. Equipping something is not a per-tick event. */
    private static final int INTERVAL_TICKS = 20;

    /** The most escudo this run has already handed each player. */
    private static final Map<UUID, Double> GRANTED = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0 || !DungeonsConfig.combatEnabled()) {
            return;
        }
        for (DungeonRun run : DungeonRunManager.runs()) {
            for (UUID id : run.party().keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && DungeonHealth.isInRun(player)) {
                    grant(player);
                }
            }
        }
    }

    /** Pays out whatever the player's sheet now owes them. */
    private static void grant(ServerPlayer player) {
        double sheet = CombatSheets.of(player).get(Stat.ESCUDO);
        double already = GRANTED.getOrDefault(player.getUUID(), 0.0);
        double owed = EscudoState.owed(sheet, already);
        if (owed <= 0) {
            return;
        }
        GRANTED.put(player.getUUID(), sheet);
        player.setAbsorptionAmount(
                player.getAbsorptionAmount() + (float) owed * EscudoState.HEALTH_PER_POINT);
    }

    /** What the player is currently carrying, in escudo points — what the panel shows. */
    public static double current(ServerPlayer player) {
        return player.getAbsorptionAmount() / EscudoState.HEALTH_PER_POINT;
    }

    /**
     * Drops the mark and the hearts. Owed by a run ending and by a death — escudo is run-only, and
     * carrying it home would make it the one temporary thing that was not.
     */
    public static void forget(ServerPlayer player) {
        GRANTED.remove(player.getUUID());
        player.setAbsorptionAmount(0);
    }
}
