package es.boffmedia.teras.battle.pixelmon;

import com.pixelmonmod.pixelmon.battles.api.BattleBuilder;
import com.pixelmonmod.pixelmon.battles.controller.ai.BattleAI;
import net.minecraft.resources.ResourceKey;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Per-player hand-off from {@link PixelmonBattleProvider} to {@code TeamSelectionMixin}: the
 * {@link BattleBuilder} customizer (battle type, clauses, end handler), the trainer AI and gimmick
 * flags to re-apply to the battle the native preview screen builds.
 */
public final class TerasTeamPreview {
    private TerasTeamPreview() {}

    public record PendingBattle(Consumer<BattleBuilder> builderCustomizer,
                                ResourceKey<BattleAI> aiMode,
                                boolean canMega,
                                boolean canDynamax) {}

    private static final Map<UUID, PendingBattle> PENDING = new ConcurrentHashMap<>();

    public static void stash(UUID playerId, PendingBattle pending) {
        PENDING.put(playerId, pending);
    }

    public static PendingBattle peek(UUID playerId) {
        return PENDING.get(playerId);
    }

    public static PendingBattle consume(UUID playerId) {
        return PENDING.remove(playerId);
    }

    public static void discard(UUID playerId) {
        PENDING.remove(playerId);
    }
}
