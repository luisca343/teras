package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.encounter.EnemyNames;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bar across the top of the screen while a boss is alive.
 *
 * <p>A boss fight looked exactly like a normal one: the same room seal, the same wave sound, and a
 * mob you had no way to read. Nothing said how far through it you were, and with a party of four
 * spread around a 2×2 chamber, nobody could tell whether the thing was nearly dead or barely
 * scratched.</p>
 *
 * <p>Deliberately server-side and vanilla: {@link ServerBossEvent} is the same mechanism the Ender
 * Dragon and the Wither use, so it needs no packet of ours, no client code, and it renders correctly
 * for a player who joins the fight late.</p>
 *
 * <p>The bar belongs to the <b>floor</b>, not the entity. A boss that despawns, is discarded with the
 * floor, or dies while the room is being torn down would otherwise leave a bar on screen with
 * nothing behind it — which is how a boss bar becomes a bug report.</p>
 */
public final class DungeonBossBars {

    /** Health below which the bar turns: the fight's last act, said in colour. */
    private static final float ENRAGE_AT = 0.35f;

    private static final class Bar {
        final ServerBossEvent event;
        final LivingEntity boss;
        boolean enraged;

        Bar(ServerBossEvent event, LivingEntity boss) {
            this.event = event;
            this.boss = boss;
        }
    }

    private final Map<UUID, Bar> bars = new LinkedHashMap<>();

    /**
     * Raises a bar for {@code boss}.
     *
     * @param mini a mini-boss gets a smaller, quieter bar — same information, less ceremony, so the
     *             real boss still lands
     */
    public void add(RunEngine.ActiveFloor floor, LivingEntity boss, String id, boolean mini) {
        if (boss == null || bars.containsKey(boss.getUUID())) {
            return;
        }
        Component name = EnemyNames.of(id);
        ServerBossEvent event = new ServerBossEvent(
                mini ? name : Component.literal("§l").append(name),
                mini ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED,
                mini ? BossEvent.BossBarOverlay.PROGRESS : BossEvent.BossBarOverlay.NOTCHED_10);
        event.setDarkenScreen(!mini);
        event.setPlayBossMusic(false);
        for (UUID member : floor.run().party().keySet()) {
            ServerPlayer player = floor.level().getServer().getPlayerList().getPlayer(member);
            if (player != null && player.serverLevel() == floor.level()) {
                event.addPlayer(player);
            }
        }
        bars.put(boss.getUUID(), new Bar(event, boss));
    }

    /**
     * Follows the fight: health, and the party as it changes.
     *
     * <p>Called from the run loop's existing scan rather than a tick of its own — the bar has to
     * track a health value that changes on somebody else's schedule, and a player who walks in
     * halfway has to be added to it.</p>
     */
    public void tick(RunEngine.ActiveFloor floor) {
        if (bars.isEmpty()) {
            return;
        }
        List<UUID> dead = new ArrayList<>();
        for (Map.Entry<UUID, Bar> entry : bars.entrySet()) {
            Bar bar = entry.getValue();
            if (!bar.boss.isAlive()) {
                dead.add(entry.getKey());
                continue;
            }
            float fraction = Math.max(0f, Math.min(1f,
                    bar.boss.getHealth() / Math.max(1f, bar.boss.getMaxHealth())));
            bar.event.setProgress(fraction);
            if (!bar.enraged && fraction <= ENRAGE_AT) {
                bar.enraged = true;
                bar.event.setColor(BossEvent.BossBarColor.YELLOW);
            }
            syncViewers(floor, bar);
        }
        for (UUID id : dead) {
            remove(id);
        }
    }

    /**
     * Everyone in the party who is on this floor sees it, and nobody else does.
     *
     * <p>Driven from the <b>current viewers</b> and not only from the party, because the two ways a
     * player stops being in the party — walking out, and disconnecting — both stop the party loop
     * from ever reaching them again. The bar was then never taken off their screen: a client only
     * removes one when it is told to, so leaving a boss fight left its bar there until relog.</p>
     */
    private void syncViewers(RunEngine.ActiveFloor floor, Bar bar) {
        for (ServerPlayer viewer : List.copyOf(bar.event.getPlayers())) {
            if (viewer.hasDisconnected()
                    || !floor.run().party().containsKey(viewer.getUUID())
                    || viewer.serverLevel() != floor.level()) {
                bar.event.removePlayer(viewer);
            }
        }
        for (UUID member : floor.run().party().keySet()) {
            ServerPlayer player = floor.level().getServer().getPlayerList().getPlayer(member);
            if (player != null && player.serverLevel() == floor.level()) {
                bar.event.addPlayer(player);
            }
        }
    }

    public void remove(UUID bossId) {
        Bar bar = bars.remove(bossId);
        if (bar != null) {
            bar.event.removeAllPlayers();
            bar.event.setVisible(false);
        }
    }

    /** Every bar down, for a floor being torn down or a run ending. */
    public void clear() {
        for (Bar bar : bars.values()) {
            bar.event.removeAllPlayers();
            bar.event.setVisible(false);
        }
        bars.clear();
    }

    public boolean isEmpty() {
        return bars.isEmpty();
    }
}
