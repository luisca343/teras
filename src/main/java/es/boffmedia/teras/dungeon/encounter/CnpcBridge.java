package es.boffmedia.teras.dungeon.encounter;

import es.boffmedia.teras.Teras;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;

/**
 * The only dungeon class allowed to import {@code noppes.npcs.*} — the same isolation rule as
 * {@code WorldEditBridge} / {@code IvKartVehicleService}. Callers MUST check {@link #available()}
 * before naming this class so it never links on servers without CustomNPCs.
 *
 * <p>Dungeon enemies are authored in-game as CustomNPCs <b>clones</b> (NPC + script saved into a
 * clone tab) and spawned here by tab + name; the mod's own scripts then own the enemy's behavior.
 * Verified against the 1.21.1 unofficial build (file 7411561), which Teras already ships against
 * for quests.</p>
 */
public final class CnpcBridge {
    private CnpcBridge() {}

    public static boolean available() {
        return ModList.get().isLoaded("customnpcs") && NpcAPI.IsAvailable();
    }

    /** Spawns clone {@code name} from {@code tab}; null when the clone does not exist. */
    public static Entity spawnClone(ServerLevel level, double x, double y, double z, int tab, String name) {
        try {
            NpcAPI api = NpcAPI.Instance();
            IEntity<?> spawned = api.getClones().spawn(x, y, z, tab, name, api.getIWorld(level));
            return spawned == null ? null : (Entity) spawned.getMCEntity();
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not spawn CNPC clone '{}' (tab {}): {}", name, tab, e.toString());
            return null;
        }
    }
}
