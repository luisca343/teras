package es.boffmedia.teras.dungeon.party;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.model.Curse;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The player-facing way into a run: <b>always a CustomNPCs NPC</b> (decided by Luisca — the entry
 * is content, standing in the world, not a command people memorize). An admin marks the NPC with
 * {@code /teras dungeon entrada marcar}; its dialog then starts the run, either through
 * {@code /teras dungeon entrada iniciar @dp <etapa>} (dialog commands run at permission 2 and
 * CustomNPCs substitutes {@code @dp} with the talking player's name) or by the player typing
 * {@code /teras mazmorra entrar} themselves. Both funnel through {@link #enter}, and both are
 * gated on standing near a marked NPC — the command existing does not make it a second entrance.
 */
public final class DungeonEntrance {
    private DungeonEntrance() {}

    /** Entity tag that makes a CustomNPCs NPC an entrance; persists in its NBT like any tag. */
    public static final String ENTRANCE_TAG = "teras_dungeon_entrada";

    /**
     * Starts a run for {@code leader} and whoever of their party is standing with them. Returns
     * an error to show the leader, or null once the build is under way.
     *
     * <p>Members who are offline, elsewhere, or dead when the leader enters are left out of the
     * run — the alternative is teleporting someone into a dungeon from wherever they wandered off
     * to. They are told, not silently dropped.</p>
     */
    public static String enter(ServerPlayer leader, int stage, Set<Curse> curses) {
        if (!nearEntrance(leader)) {
            return "Busca al guardián de las mazmorras para entrar.";
        }
        DungeonParty party = PartyManager.partyOf(leader.getUUID());
        if (party != null && !party.isLeader(leader.getUUID())) {
            return "Solo el líder del grupo puede iniciar la mazmorra.";
        }

        List<ServerPlayer> entering = new ArrayList<>();
        entering.add(leader);
        List<String> leftBehind = new ArrayList<>();
        if (party != null) {
            double radius = DungeonsConfig.entranceRadius();
            for (var member : party.members()) {
                if (member.equals(leader.getUUID())) {
                    continue;
                }
                ServerPlayer player = leader.getServer().getPlayerList().getPlayer(member);
                if (player != null && player.isAlive()
                        && player.serverLevel() == leader.serverLevel()
                        && player.distanceTo(leader) <= radius) {
                    entering.add(player);
                } else {
                    leftBehind.add(player == null ? "un miembro desconectado"
                            : player.getName().getString());
                }
            }
        }

        DungeonRunManager.StartOutcome outcome =
                DungeonRunManager.start(leader, entering, stage, curses, null);
        if (outcome.error() != null) {
            return outcome.error();
        }
        if (party != null) {
            PartyManager.dissolve(party);
        }
        for (ServerPlayer player : entering) {
            player.sendSystemMessage(Component.literal(
                    "§7Preparando la mazmorra (etapa " + stage + ", "
                            + entering.size() + " jugador" + (entering.size() == 1 ? "" : "es")
                            + ")…"));
        }
        for (String name : leftBehind) {
            leader.sendSystemMessage(Component.literal(
                    "§eSe queda fuera: " + name + " (lejos, muerto o desconectado)."));
        }
        return null;
    }

    /** Whether an entrance NPC stands within the configured radius of {@code player}. */
    public static boolean nearEntrance(ServerPlayer player) {
        double radius = DungeonsConfig.entranceRadius();
        return !player.serverLevel().getEntities((Entity) null,
                player.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e.getTags().contains(ENTRANCE_TAG)).isEmpty();
    }

    /** The nearest CustomNPCs entity within {@code radius}, for the admin marking commands. */
    public static Entity nearestNpc(ServerPlayer player, double radius) {
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : player.serverLevel().getEntities((Entity) null,
                player.getBoundingBox().inflate(radius), DungeonEntrance::isCustomNpc)) {
            double distance = entity.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    /** By registry namespace, so this class stays free of {@code noppes} imports. */
    private static boolean isCustomNpc(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && id.getNamespace().equals("customnpcs");
    }
}
