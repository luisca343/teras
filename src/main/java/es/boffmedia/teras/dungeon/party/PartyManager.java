package es.boffmedia.teras.dungeon.party;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Party bookkeeping for {@code /teras mazmorra}: invites with an expiry, one party per player, the
 * leader as the only one who can take the group through the entrance. In-memory only — a party is
 * a lobby, not progress, and a restart costs one invite to rebuild.
 *
 * <p>Methods return an error to show the caller, or null on success (the {@code RoomEditor}
 * convention); messages to <i>other</i> players — the invitee, the abandoned members — are sent
 * from here, because the caller has no reason to know who else needs telling.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class PartyManager {
    private PartyManager() {}

    private static final long INVITE_TTL_MS = 60_000;

    private record Invite(UUID leader, long expiresAt) {}

    private static final Map<UUID, DungeonParty> BY_MEMBER = new HashMap<>();
    /** Keyed by invitee; a newer invite simply replaces an older one. */
    private static final Map<UUID, Invite> INVITES = new HashMap<>();

    public static DungeonParty partyOf(UUID player) {
        return BY_MEMBER.get(player);
    }

    public static String invite(ServerPlayer leader, ServerPlayer target) {
        if (target == leader) {
            return "No puedes invitarte a ti mismo.";
        }
        if (DungeonRunManager.runOf(leader.getUUID()) != null) {
            return "No puedes formar grupo dentro de una mazmorra.";
        }
        if (BY_MEMBER.containsKey(target.getUUID())) {
            return target.getName().getString() + " ya está en un grupo.";
        }
        DungeonParty party = BY_MEMBER.get(leader.getUUID());
        if (party != null && !party.isLeader(leader.getUUID())) {
            return "Solo el líder del grupo puede invitar.";
        }
        if (party != null && party.size() >= DungeonsConfig.maxParty()) {
            return "El grupo está lleno (máximo " + DungeonsConfig.maxParty() + ").";
        }
        INVITES.put(target.getUUID(),
                new Invite(leader.getUUID(), System.currentTimeMillis() + INVITE_TTL_MS));
        target.sendSystemMessage(Component.literal(
                "§e" + leader.getName().getString() + " te invita a su grupo de mazmorra. "
                        + "Usa §6/teras mazmorra aceptar§e en el próximo minuto."));
        return null;
    }

    public static String accept(ServerPlayer player) {
        Invite invite = INVITES.remove(player.getUUID());
        if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
            return "No tienes ninguna invitación pendiente (o caducó).";
        }
        if (BY_MEMBER.containsKey(player.getUUID())) {
            return "Ya estás en un grupo — usa /teras mazmorra salir primero.";
        }
        ServerPlayer leader = player.getServer().getPlayerList().getPlayer(invite.leader());
        if (leader == null) {
            return "Quien te invitó ya no está conectado.";
        }
        DungeonParty party = BY_MEMBER.computeIfAbsent(invite.leader(),
                uuid -> new DungeonParty(uuid));
        if (party.size() >= DungeonsConfig.maxParty()) {
            return "El grupo se llenó mientras tanto.";
        }
        party.add(player.getUUID());
        BY_MEMBER.put(player.getUUID(), party);
        for (UUID member : party.members()) {
            ServerPlayer online = player.getServer().getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(Component.literal(
                        "§a" + player.getName().getString() + " se une al grupo ("
                                + party.size() + "/" + DungeonsConfig.maxParty() + ")."));
            }
        }
        return null;
    }

    /** Leaving as leader disbands: a leaderless lobby has nobody who can start the run anyway. */
    public static String leave(ServerPlayer player) {
        DungeonParty party = BY_MEMBER.get(player.getUUID());
        if (party == null) {
            return "No estás en ningún grupo.";
        }
        if (party.isLeader(player.getUUID())) {
            for (UUID member : party.members()) {
                BY_MEMBER.remove(member);
                ServerPlayer online = player.getServer().getPlayerList().getPlayer(member);
                if (online != null && online != player) {
                    online.sendSystemMessage(Component.literal(
                            "§7El líder deshizo el grupo de mazmorra."));
                }
            }
            return null;
        }
        party.remove(player.getUUID());
        BY_MEMBER.remove(player.getUUID());
        ServerPlayer leader = player.getServer().getPlayerList().getPlayer(party.leader());
        if (leader != null) {
            leader.sendSystemMessage(Component.literal(
                    "§7" + player.getName().getString() + " deja el grupo."));
        }
        return null;
    }

    /** The run has started for these members; the lobby's job is done. Silent on purpose. */
    public static void dissolve(DungeonParty party) {
        for (UUID member : party.members()) {
            BY_MEMBER.remove(member);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            INVITES.remove(player.getUUID());
            if (BY_MEMBER.containsKey(player.getUUID())) {
                leave(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BY_MEMBER.clear();
        INVITES.clear();
    }
}
