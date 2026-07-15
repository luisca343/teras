package es.boffmedia.teras.util.game;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.performance.ClientScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ShinyTracker {

    public static final ShinyTracker INSTANCE = new ShinyTracker();

    private Set<Pokemon> shinyMap = new HashSet<>();
    private Set<PixelmonEntity> shinyTracking = new HashSet<>();

    public ClippingHelper camera = null;
    private double range = 20f ;//TweaksConfig.shinySparkleRange.get();
    private float volume = 1f ;//Math.min(TweaksConfig.shinySparkleVolume.get().floatValue(), 2F);

    public boolean shouldTrackShiny(PixelmonEntity entity) {
        Teras.getLogger().info("SHINY TRACKER: "  + entity.getPokemon().getPalette().getName());
        if (entity.isUncatchable() || !entity.isAlive() || entity.isBossPokemon()
                || entity.getOwner() != null ||
                (!entity.getPokemon().isShiny() && !entity.getPokemon().isPalette("shiny2"))
                || shinyMap.contains(entity.getPokemon()) || shinyTracking.contains(entity)) {
            return false;
        }
        return true;
    }

    public void track(PixelmonEntity entity) {
        shinyTracking.add(entity);
    }

    public void tick() {
        //Check if the player is in a battle, and if so, don't sparkle shinies
        if (BattleRegistry.getBattle(Minecraft.getInstance().player) != null || camera == null) return;


        //Filter out all dead entities
        shinyTracking.removeIf(entity -> !entity.isLoaded() || !entity.isAlive());

        Vector3d vec = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        camera.prepare(vec.x, vec.y, vec.z);

        //Check all pokemon
        Iterator<PixelmonEntity> iterator = shinyTracking.iterator();
        while (iterator.hasNext()) {
            PixelmonEntity entity = iterator.next();
            //Check if the pokemon is in range & entity is being rendered

            boolean rendered = Minecraft.getInstance().getEntityRenderDispatcher().shouldRender(entity, camera, vec.x, vec.y, vec.z);
            //boolean visible = rayTrace(entity);
            boolean visible = true;

            Teras.LOGGER.info(entity.position().distanceToSqr(Minecraft.getInstance().player.position()));
            Teras.LOGGER.info(range * range);
            if (entity.position().distanceToSqr(Minecraft.getInstance().player.position()) <= range * range && rendered && visible) {

                //Remove from tracking
                iterator.remove();
                //PixelTweaks
                ClientScheduler.schedule(10, () -> {
                    Vector3d vec2 = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                    camera.prepare(vec2.x, vec2.y, vec2.z);
                    boolean rendered2 = Minecraft.getInstance().getEntityRenderDispatcher().shouldRender(entity, camera, vec.x, vec.y, vec.z);

                    if (rendered2) {
                        //Add to shiny map
                        shinyMap.add(entity.getPokemon());
                        spawnSparkle(entity);
                    } else {
                        shinyTracking.add(entity); //Allow it to try again
                    }
                });
            }
        }
    }

    public void untrackAll() {
        shinyTracking.clear();
    }

    public boolean rayTrace(PixelmonEntity entity) {
        PlayerEntity player = Minecraft.getInstance().player;

        Vector3d vector3d = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3d vector3d1 = new Vector3d(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());

        if (entity.level != player.level || vector3d1.distanceToSqr(vector3d) > 128.0D * 128.0D) return false; //Forge Backport MC-209819
        BlockRayTraceResult result = entity.level.clip(new RayTraceContext(vector3d, vector3d1, RayTraceContext.BlockMode.VISUAL, RayTraceContext.FluidMode.NONE, entity));

        if (result.getType() == RayTraceResult.Type.MISS) {
            //PixelTweaks.LOGGER.info(result.getPos() + " " + result.hitInfo);
            return true;
        }
        return false;
    }

    public void spawnSparkle(PixelmonEntity entity) {
        PlayerEntity thiz = Minecraft.getInstance().player;
        if (volume > 0) {
            ClientScheduler.schedule(3, () -> {
                SimpleSound sound = new SimpleSound(new ResourceLocation(Teras.MOD_ID, "sparkle"), SoundCategory.PLAYERS,
                        volume, 1F, false, 0, ISound.AttenuationType.LINEAR,
                        entity.getX(), entity.getY(), entity.getZ(), true);
                Minecraft.getInstance().getSoundManager().play(sound);
            });
        }


        final double d = entity.getBbWidth() / 2.5D + 0.2D;
        final double h = entity.getBbHeight() / 2.5D - 0.5D;

        int amount = 5;
        int div = 360 / amount;

        for (int i = 0; i < amount; i++) {
            double deg = i * (div) + (entity.level.random.nextInt(div / 6) - div / 3F);
            double xx = Math.cos(Math.toRadians(deg));
            double zz = Math.sin(Math.toRadians(deg));

            double driftX = (entity.level.random.nextDouble() * 0.2D - 0.1D) * d;
            double driftY = (entity.level.random.nextDouble() * 0.2D - 0.1D) * h;
            double driftZ = (entity.level.random.nextDouble() * 0.2D - 0.1D) * d;

            double x = xx * d + entity.getX() + driftX;
            double y = h + entity.getY() + driftY;
            double z = zz * d + entity.getZ() + driftZ;



            /*
            StarParticle particle = new StarParticle(Minecraft.getInstance().level, x, y, z, 0.01 * xx, 0.1 * entity.getEyeHeight(), 0.01 * zz);
            Minecraft.getInstance().particleEngine.add(particle);
            */
        }
    }
}