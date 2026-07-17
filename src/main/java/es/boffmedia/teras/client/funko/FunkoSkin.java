package es.boffmedia.teras.client.funko;

import com.mojang.blaze3d.platform.NativeImage;
import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side skin resolution for funkos. Two sources:
 * <ul>
 *     <li><b>Player</b> &mdash; a {@link ResolvableProfile}, run through the same skin manager vanilla
 *     uses for player heads.</li>
 *     <li><b>Local PNG</b> &mdash; loaded from {@code <gameDir>/Teras/skins/&lt;file&gt;} into a
 *     {@link DynamicTexture} and registered as a {@link ResourceLocation}. Cached; failures fall back
 *     to the default Steve skin.</li>
 * </ul>
 *
 * <p>1.16.5 also hand-rolled asynchronous <em>profile</em> resolution here — two static maps plus a
 * {@code Teras.EXECUTOR} job per name calling into the session service from each client. That is all
 * gone: profiles now arrive already resolved (the server resolves them, see
 * {@code FunkoBlockEntity#updateOwnerProfile} and {@code FunkoItem#verifyComponentsAfterLoad}) and
 * {@code SkinManager} does its own caching. The dead {@code availableSkinFiles()} and
 * {@code offlineUUID()} helpers (zero callers in 1.16.5) were not ported.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FunkoSkin {

    public static final String SKINS_FOLDER = "Teras/skins";

    private static final Map<String, ResourceLocation> FILE_TEXTURES = new ConcurrentHashMap<>();

    private FunkoSkin() {
    }

    /** Render type for a funko, preferring a local PNG file, then a profile, then the default skin. */
    public static RenderType getRenderType(@Nullable ResolvableProfile profile, @Nullable String file) {
        if (file != null && !file.isEmpty()) {
            return RenderType.entityCutoutNoCull(getFileTexture(file));
        }
        if (profile != null) {
            return RenderType.entityTranslucent(skinTexture(profile));
        }
        return RenderType.entityCutoutNoCull(DefaultPlayerSkin.getDefaultTexture());
    }

    /** The resolved skin {@link ResourceLocation} (for particles and other direct texture use). */
    public static ResourceLocation getSkinTexture(@Nullable ResolvableProfile profile, @Nullable String file) {
        if (file != null && !file.isEmpty()) {
            return getFileTexture(file);
        }
        if (profile != null) {
            return skinTexture(profile);
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    private static ResourceLocation skinTexture(ResolvableProfile profile) {
        // Falls back to the default skin internally when the profile carries no texture properties.
        return Minecraft.getInstance().getSkinManager().getInsecureSkin(profile.gameProfile()).texture();
    }

    private static ResourceLocation getFileTexture(String file) {
        return FILE_TEXTURES.computeIfAbsent(file, FunkoSkin::loadFileTexture);
    }

    private static ResourceLocation loadFileTexture(String file) {
        String name = file.toLowerCase().endsWith(".png") ? file : file + ".png";
        try {
            // The component this comes from is attacker-controllable in the sense that it rides on an
            // item stack: without this, a crafted `teras:funko_skin_file` of "../../.." could walk a
            // *viewer's* client out of the skins folder and render an arbitrary PNG off their disk.
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(SKINS_FOLDER).toAbsolutePath().normalize();
            Path path = dir.resolve(name).toAbsolutePath().normalize();
            if (!path.startsWith(dir)) {
                Teras.LOGGER.warn("Rejected funko skin outside {}: {}", SKINS_FOLDER, file);
                return DefaultPlayerSkin.getDefaultTexture();
            }
            if (!Files.isRegularFile(path)) {
                Teras.LOGGER.warn("Funko skin file not found: {}", path);
                return DefaultPlayerSkin.getDefaultTexture();
            }
            try (InputStream is = Files.newInputStream(path)) {
                DynamicTexture texture = new DynamicTexture(NativeImage.read(is));
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "funko_skins/" + sanitize(name));
                Minecraft.getInstance().getTextureManager().register(rl, texture);
                return rl;
            }
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to load funko skin '{}'", name, e);
            return DefaultPlayerSkin.getDefaultTexture();
        }
    }

    private static String sanitize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_./-]", "_");
    }
}
