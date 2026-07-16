package es.boffmedia.teras.quests;

import com.mojang.authlib.GameProfile;
import es.boffmedia.teras.Teras;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import noppes.npcs.api.wrapper.PlayerWrapper;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a uuid to a CustomNPCs {@link PlayerWrapper}, <b>online or offline</b>. Port of the 1.16.5
 * {@code PlayerQuests.buildWrapper}.
 *
 * <p>Offline players matter: the SmartRotom backend polls {@code /quests/user/{uuid}} for arbitrary
 * players, most of whom aren't connected. CustomNPCs reads quest progress off a player <i>entity</i>,
 * so for an offline uuid we build a {@link FakePlayer} and load its saved data from disk — then
 * CustomNPCs reads the stored progress as if they were online.</p>
 *
 * <p>CustomNPCs-coupled: only reachable behind {@link QuestBridge#isAvailable()}.</p>
 */
final class PlayerLookup {
    private PlayerLookup() {}

    /**
     * A wrapper for {@code uuid}, or empty when the player has never played on this server.
     *
     * <p>1.16.5 built the {@code FakePlayer} unconditionally and ignored the load result, so an
     * unknown uuid silently reported an empty quest list — indistinguishable from a real player who
     * had started nothing. {@code PlayerList.load} returns an {@code Optional<CompoundTag>} on 1.21.1,
     * so an unknown player is now genuinely distinguishable and the caller can 404 instead of lying.</p>
     */
    static Optional<PlayerWrapper<?>> wrapperFor(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Optional.empty();
        }

        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return Optional.of(new PlayerWrapper<>(online));
        }

        try {
            FakePlayer fake = new FakePlayer(server.overworld(), new GameProfile(uuid, ""));
            Optional<CompoundTag> saved = server.getPlayerList().load(fake);
            if (saved.isEmpty()) {
                Teras.LOGGER.info("Quest lookup for {}: no saved player data (never played here)", uuid);
                return Optional.empty();
            }
            return Optional.of(new PlayerWrapper<>(fake));
        } catch (Exception e) {
            Teras.LOGGER.warn("Failed loading offline player {} for quest lookup: {}", uuid, e.toString());
            return Optional.empty();
        }
    }
}
