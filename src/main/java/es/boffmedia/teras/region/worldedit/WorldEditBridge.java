package es.boffmedia.teras.region.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.neoforge.NeoForgePlayer;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The only class allowed to import {@code com.sk89q.*} (same isolation rule as the battle
 * providers): reads the player's current WorldEdit selection into an engine-free
 * {@link SelectionResult}. Callers MUST check {@code ModList.get().isLoaded("worldedit")} before
 * naming this class, so it never links on servers without WorldEdit.
 *
 * <p>On 1.16.5 this was {@code com.sk89q.worldedit.forge.ForgeAdapter}; the NeoForge build of
 * WorldEdit 7.3.x renamed the platform package to {@code neoforge}.</p>
 */
public final class WorldEditBridge {
    private WorldEditBridge() {}

    /**
     * The player's selection in their current world. Selections made in another dimension surface
     * as {@link SelectionResult.Status#INCOMPLETE} (WorldEdit throws for a world mismatch), which
     * is the right message: there is nothing usable to select here.
     */
    public static SelectionResult readSelection(ServerPlayer player) {
        NeoForgePlayer adapted = NeoForgeAdapter.adaptPlayer(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(adapted);
        Region selection;
        try {
            selection = session.getSelection(adapted.getWorld());
        } catch (IncompleteRegionException e) {
            return SelectionResult.incomplete();
        }
        if (selection instanceof Polygonal2DRegion poly) {
            List<RegionPoint> points = new ArrayList<>(poly.getPoints().size());
            for (BlockVector2 vertex : poly.getPoints()) {
                points.add(new RegionPoint(vertex.x(), vertex.z()));
            }
            return SelectionResult.polygon(points, poly.getMinimumY(), poly.getMaximumY());
        }
        if (selection instanceof CuboidRegion cuboid) {
            BlockVector3 min = cuboid.getMinimumPoint();
            BlockVector3 max = cuboid.getMaximumPoint();
            return SelectionResult.cuboid(
                    new TerasRegion.Corner(min.x(), min.y(), min.z()),
                    new TerasRegion.Corner(max.x(), max.y(), max.z()));
        }
        return SelectionResult.unsupported(selection.getClass().getSimpleName());
    }
}
