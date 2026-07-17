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
 * Resolves a funko's skin to a texture: either a {@link ResolvableProfile} through vanilla's skin
 * manager, or a PNG loaded from {@code <gameDir>/Teras/skins/} into a {@link DynamicTexture}.
 * Profiles arrive already resolved from the server. Cached; failures fall back to the default skin.
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
            // The name rides on an item stack, so it is attacker-supplied: a crafted "../../.." would
            // otherwise walk a viewer's client out of the skins folder into arbitrary PNGs on disk.
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
