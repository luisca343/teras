package es.boffmedia.teras.items;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.entities.pixelmon.StatueEntity;
import es.boffmedia.teras.util.game.TerasDamageSource;
import es.boffmedia.teras.util.media.TerasSoundEvents;
import es.boffmedia.teras.util.math.vector.RayTrace;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

public class Taser extends Item {

    public Taser(Properties properties) {
        super(properties);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        world.playSound(player, player.getX(), player.getY(), player.getZ(), TerasSoundEvents.TASER.get(), player.getSoundSource(), 1.0F, 1.0F);

        int range = 7;
        Vector3d startVec = player.getEyePosition(1.0F);
        Vector3d lookVec = player.getViewVector(1.0F).scale(range);
        Vector3d endVec = startVec.add(lookVec);
        AxisAlignedBB boundingBox = player.getBoundingBox().expandTowards(lookVec).inflate(1, 1, 1);
        EntityRayTraceResult entityRayTraceResult = RayTrace.rayTraceEntities(player, startVec, endVec, boundingBox, s -> s instanceof LivingEntity, range * range);

        if (entityRayTraceResult != null) {
            LivingEntity entity = (LivingEntity) entityRayTraceResult.getEntity();
            entity.hurt(TerasDamageSource.TASER, 1.0F);
            if(!(entity instanceof PixelmonEntity) && !(entity instanceof StatueEntity)) {
                entity.animateHurt();
            }
            entity.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 100, 2));
        }

        player.getCooldowns().addCooldown(this, 100);
        return super.use(world, player, hand);
    }




}
