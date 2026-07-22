package es.boffmedia.teras.dungeon.mecanica;

import es.boffmedia.teras.dungeon.build.BuiltDungeon;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.piso.MechanicDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a piso's signature mechanic can hook into.
 *
 * <p>Every hook has a no-op default, so a mechanic implements only the moments it cares about and
 * adding a hook later does not break the ones that already exist.</p>
 *
 * <p>The hooks are deliberately few. There is exactly one shipped mechanic and the list is what it
 * plus the obvious next ones need — a floor-wide effect on entry, something that answers a kill,
 * something that answers a room being finished. Inventing a wider surface now would be guessing at
 * mechanics nobody has designed, and every unused hook is a call site that has to keep working.</p>
 */
public interface Mechanic {

    /** The registry key a piso names in {@code mecanica.id}. */
    String id();

    /**
     * A room has just sealed for a fight. The infestation hatches here — after the doors shut, so
     * the party is already committed.
     */
    default void onRoomSealed(ServerLevel level, BuiltDungeon built, Room room, MechanicDef def) {
    }

    /** A room's last enemy has died and the doors are about to open. */
    default void onRoomCleared(ServerLevel level, BuiltDungeon built, Room room, MechanicDef def) {
    }

    /**
     * A wave enemy died. Fires before the kill ledger is settled, so a mechanic that wants to bring
     * something back has to add it to the room itself rather than assume the room stays open.
     */
    default void onEnemyDeath(ServerLevel level, BuiltDungeon built, Room room, LivingEntity dead,
                              MechanicDef def) {
    }
}
