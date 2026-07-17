package es.boffmedia.teras.client.camera.pixelmon;

import com.pixelmonmod.pixelmon.entities.pixelmon.StatueEntity;
import net.minecraft.world.entity.Entity;

/**
 * Tells a Pixelmon statue apart from a live Pokémon for the camera's entity list.
 *
 * <p>Compiles against Pixelmon ({@code compileOnly}), so it is only ever named from behind an
 * {@code isPixelmonLoaded()} guard — see {@code ViewScanner}.</p>
 *
 * <p>Statues carry a species and so read as Pokémon to {@code DexProvider}, which is correct for the
 * dex but not for a photo: a statue is a decoration, and reporting it as a wild Pokémon would let one
 * satisfy anything the web asks a player to photograph. Cobblemon has no equivalent, which is why this
 * is a Pixelmon-only distinction rather than part of the neutral scan.</p>
 */
public final class PixelmonStatues {
    private PixelmonStatues() {}

    public static boolean isStatue(Entity entity) {
        return entity instanceof StatueEntity;
    }
}
