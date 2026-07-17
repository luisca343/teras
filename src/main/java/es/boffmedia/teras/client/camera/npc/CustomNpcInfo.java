package es.boffmedia.teras.client.camera.npc;

import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Names a CustomNPCs NPC for the camera's entity list.
 *
 * <p>Compiles against CustomNPCs ({@code compileOnly}), so it is only ever named from behind a
 * {@code ModList.isLoaded("customnpcs")} guard — see {@code ViewScanner}.</p>
 */
public final class CustomNpcInfo {
    private CustomNpcInfo() {}

    /** {@code entity}'s NPC name, or {@code null} if it isn't a CustomNPCs NPC. */
    public static String nameOf(Entity entity) {
        return entity instanceof EntityNPCInterface npc ? npc.getName().getString() : null;
    }
}
