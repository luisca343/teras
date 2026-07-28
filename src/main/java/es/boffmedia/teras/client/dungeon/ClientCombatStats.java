package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.combat.Stat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.List;

/**
 * The last sheet the server sent, held for {@link CombatStatsOverlay} to draw.
 *
 * <p>A payload whose length disagrees with this client's {@link Stat} enum is dropped whole. The two
 * only diverge when a client and a server are on different builds, and a panel that confidently
 * labels {@code penetración} with {@code alcance}'s number is worse than no panel — it is wrong in a
 * way nobody would think to doubt.</p>
 *
 * <p>Single-threaded by construction, so the array needs no synchronisation: {@code accept} runs inside
 * {@code IPayloadContext.enqueueWork}, which is the client main thread — the same one that renders.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class ClientCombatStats {
    private ClientCombatStats() {}

    private static float[] values = new float[0];

    public static void accept(List<Float> incoming) {
        if (incoming.isEmpty() || incoming.size() != Stat.values().length) {
            values = new float[0];
            return;
        }
        float[] next = new float[incoming.size()];
        for (int i = 0; i < next.length; i++) {
            next[i] = incoming.get(i);
        }
        values = next;
    }

    public static boolean visible() {
        return values.length == Stat.values().length;
    }

    public static float get(Stat stat) {
        return values[stat.ordinal()];
    }

    /**
     * Dropped on disconnect, so a new session never opens showing the last one's numbers.
     *
     * <p>This was a {@code clear()} nobody called — the guarantee was documented and not implemented,
     * so a panel could survive into the next server you joined. It is the same {@code LoggingOut} hook
     * {@link ClientDungeonWallet} and {@link ClientDungeonMap} already use.</p>
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        values = new float[0];
    }
}
