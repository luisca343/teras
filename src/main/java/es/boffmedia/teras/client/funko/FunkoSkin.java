package es.boffmedia.teras.client.funko;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.SkullTileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side skin resolution for funkos. Two sources:
 * <ul>
 *     <li><b>Player name</b> &mdash; resolved through the same machinery vanilla uses for
 *     player heads ({@link SkullTileEntity#updateGameprofile} + the skin manager), so it
 *     works in single- and multiplayer without any extra network code here.</li>
 *     <li><b>Local PNG</b> &mdash; loaded from {@code <gameDir>/Teras/skins/&lt;file&gt;} into a
 *     {@link DynamicTexture} and registered as a {@link ResourceLocation}.</li>
 * </ul>
 * Everything is cached; missing/failed lookups fall back to the default Steve skin.
 */
@OnlyIn(Dist.CLIENT)
public final class FunkoSkin {

    public static final String SKINS_FOLDER = "Teras/skins";

    private static final Map<String, ResourceLocation> FILE_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, GameProfile> RESOLVED_PROFILES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> RESOLVING = new ConcurrentHashMap<>();

    private FunkoSkin() {
    }

    /** Render type for a funko, preferring a local PNG file, then a resolved profile, then the default skin. */
    public static RenderType getRenderType(@Nullable GameProfile profile, @Nullable String file) {
        if (file != null && !file.isEmpty()) {
            return RenderType.entityCutoutNoCull(getFileTexture(file));
        }
        return profileRenderType(profile);
    }

    /**
     * Render type when only an owner <em>name</em> is known (the inventory item case). Resolves the
     * profile asynchronously and returns the default skin until it is ready.
     */
    public static RenderType getRenderTypeForOwner(@Nullable String ownerName, @Nullable String file) {
        if (file != null && !file.isEmpty()) {
            return RenderType.entityCutoutNoCull(getFileTexture(file));
        }
        return profileRenderType(resolveProfile(ownerName));
    }

    /** The resolved skin {@link ResourceLocation} (for particles and other direct texture use). */
    public static ResourceLocation getSkinTexture(@Nullable GameProfile profile, @Nullable String file) {
        if (file != null && !file.isEmpty()) {
            return getFileTexture(file);
        }
        if (profile != null) {
            Minecraft mc = Minecraft.getInstance();
            Map<Type, MinecraftProfileTexture> textures = mc.getSkinManager().getInsecureSkinInformation(profile);
            if (textures.containsKey(Type.SKIN)) {
                return mc.getSkinManager().registerTexture(textures.get(Type.SKIN), Type.SKIN);
            }
            return DefaultPlayerSkin.getDefaultSkin(PlayerEntity.createPlayerUUID(profile));
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    private static RenderType profileRenderType(@Nullable GameProfile profile) {
        if (profile != null) {
            Minecraft mc = Minecraft.getInstance();
            Map<Type, MinecraftProfileTexture> textures = mc.getSkinManager().getInsecureSkinInformation(profile);
            if (textures.containsKey(Type.SKIN)) {
                return RenderType.entityTranslucent(mc.getSkinManager().registerTexture(textures.get(Type.SKIN), Type.SKIN));
            }
            return RenderType.entityCutoutNoCull(DefaultPlayerSkin.getDefaultSkin(PlayerEntity.createPlayerUUID(profile)));
        }
        return RenderType.entityCutoutNoCull(DefaultPlayerSkin.getDefaultSkin());
    }

    @Nullable
    private static GameProfile resolveProfile(@Nullable String ownerName) {
        if (ownerName == null || ownerName.isEmpty()) {
            return null;
        }
        GameProfile cached = RESOLVED_PROFILES.get(ownerName);
        if (cached != null) {
            return cached;
        }
        // Resolution (and the underlying session-service call) must not run on the render thread.
        if (RESOLVING.putIfAbsent(ownerName, Boolean.TRUE) == null) {
            Teras.EXECUTOR.submit(() -> {
                try {
                    GameProfile resolved = SkullTileEntity.updateGameprofile(new GameProfile(null, ownerName));
                    if (resolved != null) {
                        RESOLVED_PROFILES.put(ownerName, resolved);
                    }
                } catch (Exception e) {
                    Teras.LOGGER.warn("Failed to resolve funko skin for player '{}'", ownerName, e);
                } finally {
                    RESOLVING.remove(ownerName);
                }
            });
        }
        return null;
    }

    private static ResourceLocation getFileTexture(String file) {
        return FILE_TEXTURES.computeIfAbsent(file, FunkoSkin::loadFileTexture);
    }

    private static ResourceLocation loadFileTexture(String file) {
        String name = file.toLowerCase().endsWith(".png") ? file : file + ".png";
        try {
            Path path = Minecraft.getInstance().gameDirectory.toPath().resolve(SKINS_FOLDER).resolve(name);
            if (!Files.isRegularFile(path)) {
                Teras.LOGGER.warn("Funko skin file not found: {}", path);
                return DefaultPlayerSkin.getDefaultSkin();
            }
            try (InputStream is = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(is);
                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation rl = new ResourceLocation(Teras.MOD_ID, "funko_skins/" + sanitize(name));
                Minecraft.getInstance().getTextureManager().register(rl, texture);
                return rl;
            }
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to load funko skin '{}'", name, e);
            return DefaultPlayerSkin.getDefaultSkin();
        }
    }

    private static String sanitize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_./-]", "_");
    }

    /** Lists the PNG files available in the skins folder (for command suggestions). */
    public static Iterable<String> availableSkinFiles() {
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(SKINS_FOLDER);
            if (Files.isDirectory(dir)) {
                java.util.List<String> files = new java.util.ArrayList<>();
                Files.list(dir)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                        .forEach(p -> files.add(p.getFileName().toString()));
                return files;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    /** A stable per-owner UUID, mirroring vanilla offline-player UUIDs. */
    public static UUID offlineUUID(String name) {
        return PlayerEntity.createPlayerUUID(new GameProfile(null, name));
    }
}
