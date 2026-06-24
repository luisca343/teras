package es.boffmedia.teras.particle;


import com.google.common.base.Charsets;
import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.IAnimatedSprite;
import net.minecraft.client.particle.SpriteTexturedParticle;
import net.minecraft.client.particle.TexturesParticle;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.MissingTextureSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

public abstract class FakeParticle extends SpriteTexturedParticle {

    public static AtlasTexture atlasTexture;

    protected FakeParticle(ClientWorld world, double x, double y, double z, double motionX, double motionY, double motionZ) {
        super(world, x, y, z, motionX, motionY, motionZ);
    }

    public abstract ResourceLocation getResourceLocation();

    public static IAnimatedSprite loadTexture(ResourceLocation location) {

        if (atlasTexture == null) {
            Teras.LOGGER.warn("Particle atlas is null! Do you have Optifine installed?");

            // What is the location of the particle atlas?


            atlasTexture = Minecraft.getInstance().getModelManager().getAtlas(AtlasTexture.LOCATION_PARTICLES);


        }

        String fileLoc = "particles/" + location.getPath() + ".json";
        Teras.LOGGER.warn("Particle file location: " + fileLoc);

        try (
                Reader reader = new InputStreamReader(Objects.requireNonNull(Teras.getResource(fileLoc)), Charsets.UTF_8);
        ) {
            TexturesParticle texturesparticle = TexturesParticle.fromJson(JSONUtils.parse(reader));
            List<ResourceLocation> list = texturesparticle.getTextures();



            FakeParticleTexture texture = new FakeParticleTexture(list.stream().map((particleTextureID)
                            -> new ResourceLocation(particleTextureID.getNamespace(), "particle/" + particleTextureID.getPath()))
                    .map((resourceLoc) -> atlasTexture.getSprite(resourceLoc)).collect(Collectors.toList()));

            return texture;
        } catch (IOException e) {
            Teras.getLogger().error("Failed to load fake particle texture", e);
        }
        return new FakeParticleTexture(Arrays.asList(atlasTexture.getSprite(MissingTextureSprite.getLocation())));
    }

    public static class FakeParticleTexture implements IAnimatedSprite {
        private List<TextureAtlasSprite> sprites;

        public FakeParticleTexture (List<TextureAtlasSprite> sprites) {
            this.sprites = sprites;
        }

        @Override
        public TextureAtlasSprite get(int particleAge, int particleMaxAge) {
            return this.sprites.get(particleAge * (this.sprites.size() - 1) / particleMaxAge);
        }

        @Override
        public TextureAtlasSprite get(Random rand) {
            return this.sprites.get(rand.nextInt(this.sprites.size()));
        }
    }
}