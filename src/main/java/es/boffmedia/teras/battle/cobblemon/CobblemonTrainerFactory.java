package es.boffmedia.teras.battle.cobblemon;

import com.cobblemon.mod.common.api.npc.NPCClass;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.npc.configuration.NPCBattleConfiguration;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import es.boffmedia.teras.Teras;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Builds Cobblemon trainer participants backed by a world entity. Cobblemon only spawns a
 * {@code PokemonEntity} for an actor implementing {@code EntityBackedBattleActor} (its
 * {@code SwitchInstruction} otherwise falls back to a GUI-only switch), so a virtual
 * {@code TrainerBattleActor} would battle invisibly. Each battle spawns its own NPC; the caller
 * discards it at battle end.
 */
final class CobblemonTrainerFactory {
    private CobblemonTrainerFactory() {}

    private static final ResourceLocation TRAINER_CLASS =
            ResourceLocation.fromNamespaceAndPath("cobblemon", "standard");

    /** Fallback when the NPC class declares no skill; Cobblemon's own {@code standard} class uses 5. */
    private static final int DEFAULT_SKILL = 5;

    /**
     * Spawns the trainer NPC at {@code pos} turned towards {@code lookAt} and wraps it as an actor
     * carrying {@code team}; the entity is reachable via {@link NPCBattleActor#getEntity()} for
     * cleanup. Returns {@code null} if the NPC class is unavailable, in which case no battle should
     * start.
     */
    static NPCBattleActor buildTrainer(ServerPlayer player, String name, List<BattlePokemon> team,
                                       int teamLevel, Vec3 pos, Vec3 lookAt) {
        NPCEntity npc = spawnTrainerNpc(player, name, teamLevel, pos, lookAt);
        if (npc == null) {
            return null;
        }
        Integer skill = npc.getSkill();
        return new NPCBattleActor(npc, team, skill != null ? skill : DEFAULT_SKILL, new RandomBattleAI());
    }

    private static NPCEntity spawnTrainerNpc(ServerPlayer player, String name, int teamLevel,
                                             Vec3 pos, Vec3 lookAt) {
        NPCClass npcClass = NPCClasses.getByIdentifier(TRAINER_CLASS);
        if (npcClass == null) {
            Teras.LOGGER.error("Cobblemon NPC class '{}' is not loaded; cannot spawn trainer", TRAINER_CLASS);
            return null;
        }
        ServerLevel level = player.serverLevel();
        NPCEntity npc = new NPCEntity(level);
        npc.moveTo(pos.x, pos.y, pos.z, facing(pos, lookAt), 0f);
        npc.setNpc(npcClass);
        npc.initialize(teamLevel);
        npc.setCustomName(Component.literal(name));
        // The NPC exists only to anchor the battle: it must not wander off, be attacked, or offer its
        // own class dialogue/challenge while ours is running.
        npc.setMovable(Boolean.FALSE);
        npc.setInvulnerable(Boolean.TRUE);
        npc.setLeashable(Boolean.FALSE);
        npc.setAllowProjectileHits(Boolean.FALSE);
        npc.setInteraction(null);
        NPCBattleConfiguration battleConfig = new NPCBattleConfiguration();
        battleConfig.setCanChallenge(false);
        npc.setBattle(battleConfig);
        if (!level.addFreshEntity(npc)) {
            Teras.LOGGER.error("Failed to spawn Cobblemon trainer NPC '{}'", name);
            return null;
        }
        return npc;
    }

    /**
     * A spot {@code distance} blocks ahead of the player and {@code sideways} blocks to their left,
     * derived from the player's yaw ({@code getLookAngle} degenerates when looking straight up/down).
     * Cobblemon derives send-out positions by interpolating between the two sides' actor positions,
     * so a trainer must never share the player's spot.
     */
    static Vec3 spotNear(ServerPlayer player, double distance, double sideways) {
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 left = new Vec3(forward.z, 0, -forward.x);
        return player.position().add(forward.scale(distance)).add(left.scale(sideways));
    }

    /** Yaw in degrees that points {@code from} at {@code to}. */
    private static float facing(Vec3 from, Vec3 to) {
        return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
    }
}
