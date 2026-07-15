package es.boffmedia.teras.items;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import es.boffmedia.teras.util.media.TerasSoundEvents;
import es.boffmedia.teras.util.math.vector.RayTrace;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

public class LatigoNumeril extends Item {
    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public LatigoNumeril(Properties properties) {
        super(properties);

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 1.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", 1.6D, AttributeModifier.Operation.ADDITION));
        //builder.put(ForgeMod.REACH_DISTANCE.get(), new AttributeModifier(UUID.fromString("533e583e-312f-11ee-be56-0242ac120002"), "Weapon modifier", 5.0D, AttributeModifier.Operation.ADDITION));

        this.defaultModifiers = builder.build();
    }



    @Override
    public boolean onLeftClickEntity(ItemStack stack, PlayerEntity player, Entity entity) {
        player.level.playSound(player, player.getX(), player.getY(), player.getZ(), TerasSoundEvents.LATIGO_NUMERIL.get(), player.getSoundSource(), 1.0F, 1.0F);

        return super.onLeftClickEntity(stack, player, entity);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        world.playSound(player, player.getX(), player.getY(), player.getZ(), TerasSoundEvents.LATIGO_NUMERIL.get(), player.getSoundSource(), 1.0F, 1.0F);

        int range = 5;
        Vector3d startVec = player.getEyePosition(1.0F);
        Vector3d lookVec = player.getViewVector(1.0F).scale(range);
        Vector3d endVec = startVec.add(lookVec);
        AxisAlignedBB boundingBox = player.getBoundingBox().expandTowards(lookVec).inflate(1, 1, 1);
        EntityRayTraceResult entityRayTraceResult = RayTrace.rayTraceEntities(player, startVec, endVec, boundingBox, s -> s instanceof LivingEntity, range * range);

        if (entityRayTraceResult != null) {
            Entity entity = entityRayTraceResult.getEntity();
            if(!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity)) return new ActionResult<>(ActionResultType.FAIL, player.getItemInHand(hand));

            // Pull entity towards player
            Vector3d playerPos = player.position().add(0, player.getEyeHeight(), 0);
            Vector3d entityPos = entity.position();
            Vector3d pullVec = playerPos.subtract(entityPos).normalize().scale(1);
            entity.setDeltaMovement(pullVec);

            player.getCooldowns().addCooldown(this, 100);
        }

        return super.use(world, player, hand);
    }





}
