package es.boffmedia.teras.battle.pixelmon;

import com.pixelmonmod.pixelmon.api.model.RenderingHandler;
import com.pixelmonmod.pixelmon.api.npc.entity.EntityProperties;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTierRegistry;
import com.pixelmonmod.pixelmon.api.storage.NPCPartyStorage;
import com.pixelmonmod.pixelmon.battles.controller.participants.EntityParticipant;
import com.pixelmonmod.pixelmon.entities.npcs.NPC;
import es.boffmedia.teras.battle.config.BattleConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.awt.Color;
import java.util.List;
import java.util.UUID;

/**
 * Builds a Pixelmon trainer participant from a {@link BattleConfig}. Each battle spawns its own NPC as
 * the trainer's backing entity ({@code EntityParticipant} requires one, and {@code BattleRegistry}
 * maps each entity to a single battle so NPCs can't be shared); the caller discards it at battle end.
 */
final class PixelmonTrainerFactory {
    private PixelmonTrainerFactory() {}

    /**
     * Spawns the trainer NPC and wraps it as an {@link EntityParticipant} carrying {@code team}; the
     * entity is reachable via {@link EntityParticipant#getEntity()} for cleanup.
     *
     * @param teamLevel above 100 becomes a {@code +N} boss tier
     */
    static EntityParticipant buildTrainer(ServerPlayer player, BattleConfig config,
                                          List<Pokemon> team, int teamLevel) {
        NPCPartyStorage storage = new NPCPartyStorage(UUID.randomUUID());
        for (int i = 0; i < team.size() && i < 6; i++) {
            storage.set(i, team.get(i));
        }

        NPC npc = spawnTrainerNpc(player, config, team.get(0), storage);
        storage.setEntity(npc);

        BossTier tier = teamLevel > 100 ? overLevelBossTier(teamLevel - 100) : BossTierRegistry.NOT_BOSS;

        return EntityParticipant.builder()
                .entity(npc)
                .storage(storage)
                .bossTier(tier)
                .canMega(config.allowsMega())
                .canDynamax(config.allowsDynamax())
                .build();
    }

    static NPC spawnTrainerNpc(ServerPlayer player, BattleConfig config, Pokemon renderPlaceholder,
                               NPCPartyStorage storage) {
        String name = config.getNombre() == null || config.getNombre().isBlank()
                ? "Entrenador" : config.getNombre();
        NPC npc = NPC.builder()
                .name(Component.literal(name))
                .position(player.getX(), player.getY(), player.getZ())
                .renderingHandler(RenderingHandler.pokemon(renderPlaceholder))
                .properties(EntityProperties.defaultProperties()
                        .makeInvulnerable()
                        .makeImmovable()
                        .withoutNamePlate())
                .party(storage)
                .noInteractions()
                .buildAndSpawn(player.level());
        npc.setNoGravity(true);
        npc.setSilent(true);
        return npc;
    }

    /** A synthetic "+N levels" boss tier for rival teams resolving above level 100. */
    private static BossTier overLevelBossTier(int extraLevels) {
        return new BossTier("+" + extraLevels, "+" + extraLevels, false, 0, Color.BLACK,
                1.0f, false, 0.0, 0.0, "PALETA", 1.0, extraLevels);
    }
}
