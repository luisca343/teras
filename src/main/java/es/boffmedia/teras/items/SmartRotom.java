package es.boffmedia.teras.items;


import com.pixelmonmod.pixelmon.api.pokedex.PlayerPokedex;
import com.pixelmonmod.pixelmon.api.pokedex.PokedexRegistrationStatus;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.entities.pixelmon.StatueEntity;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.ClientProxy;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.server.SMessageUpdateDex;
import es.boffmedia.teras.util.data.smartrotom.SmartRotomService;
import es.boffmedia.teras.util.objects.dex.ActualizarDex;
import es.boffmedia.teras.util.math.vector.RayTrace;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.util.*;
import net.minecraft.entity.Entity;

import java.util.List;
import java.util.ArrayList;

public class SmartRotom extends Item {
    private static final double MAX_DETECTION_DISTANCE = 25.0;
    private static final double DETECTION_FOV = 40.0;
    
    public SmartRotom(Properties properties) {
        super(properties);
    }

    /*
    @Override
    public ActionResultType interactLivingEntity(ItemStack item, PlayerEntity player, LivingEntity entity, Hand hand) {
        if(entity instanceof PixelmonEntity){

            PixelmonEntity pixelmon = (PixelmonEntity) entity;
            int smartRotomID = item.getTag().getInt("PadID");
            ClientProxy.PadData smartRotom = Teras.PROXY.getPadByID(smartRotomID);

            int dex = pixelmon.getSpecies().getDex();
            smartRotom.view.runJS("openDex("+ dex +")", "");
            PlayerPokedex pokedex = new PlayerPokedex(player.getUUID());
            if(pokedex.get(dex) == PokedexRegistrationStatus.UNKNOWN){
                Messages.INSTANCE.sendToServer( new SMessageUpdateDex(dex+""));

            }
            return ActionResultType.FAIL;
        }

        return ActionResultType.SUCCESS;
    }
*/

    /**
     * Detects all entities visible on the player's screen within a given range
     * Prioritizes entities that are centered in the player's view
     */
    public List<LivingEntity> getEntitiesInView(World world, PlayerEntity player, double range, double fov) {
        List<EntityWithScore> entitiesWithScores = new ArrayList<>();
        
        Vector3d eyePos = player.getEyePosition(1.0F);
        Vector3d lookVec = player.getViewVector(1.0F);
        
        // Get all entities within range
        AxisAlignedBB searchBox = new AxisAlignedBB(
            eyePos.x - range, eyePos.y - range, eyePos.z - range,
            eyePos.x + range, eyePos.y + range, eyePos.z + range
        );
        
        List<Entity> nearbyEntities = world.getEntities(player, searchBox);
        
        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == player) continue;
            
            LivingEntity livingEntity = (LivingEntity) entity;
            
            // Get vector from player to entity center
            Vector3d entityCenter = entity.position().add(0, entity.getBbHeight() / 2, 0);
            Vector3d toEntity = entityCenter.subtract(eyePos).normalize();
            
            // Calculate angle between look direction and entity direction
            double dotProduct = lookVec.dot(toEntity);
            double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dotProduct))) * (180.0 / Math.PI);
            
            // Check if entity is within FOV
            if (angle <= fov) {
                // Check if entity is visible (not blocked)
                if (hasLineOfSight(world, player, eyePos, entity)) {
                    // Calculate score: lower angle = better (more centered)
                    // Also factor in distance (closer is slightly better)
                    double distance = eyePos.distanceTo(entityCenter);
                    double score = angle + (distance / range) * 5.0; // Angle is primary, distance is secondary
                    
                    entitiesWithScores.add(new EntityWithScore(livingEntity, score));
                }
            }
        }
        
        // Sort by score (lower is better = more centered)
        entitiesWithScores.sort((a, b) -> Double.compare(a.score, b.score));
        
        // Extract entities in order of priority
        List<LivingEntity> entitiesInView = new ArrayList<>();
        for (EntityWithScore ews : entitiesWithScores) {
            entitiesInView.add(ews.entity);
        }
        
        return entitiesInView;
    }
    
    /**
     * Helper class to store entity with its centering score
     */
    private static class EntityWithScore {
        final LivingEntity entity;
        final double score;
        
        EntityWithScore(LivingEntity entity, double score) {
            this.entity = entity;
            this.score = score;
        }
    }
    
    /**
     * Checks if there's a clear line of sight from the player to the entity
     * Uses block raytrace to ensure no blocks are blocking the view
     */
    private boolean hasLineOfSight(World world, PlayerEntity player, Vector3d eyePos, Entity entity) {
        // Check multiple points on the entity for better detection
        double[] heightChecks = {
            0.1,  // Near bottom
            entity.getBbHeight() / 2,  // Middle (most important)
            entity.getBbHeight() * 0.9  // Near top
        };
        
        // Also check horizontal offsets for wider entities
        double horizontalOffset = Math.min(entity.getBbWidth() / 4, 0.3);
        Vector3d[] horizontalOffsets = {
            new Vector3d(0, 0, 0),  // Center
            new Vector3d(horizontalOffset, 0, 0),
            new Vector3d(-horizontalOffset, 0, 0),
            new Vector3d(0, 0, horizontalOffset),
            new Vector3d(0, 0, -horizontalOffset)
        };
        
        for (double heightOffset : heightChecks) {
            for (Vector3d hOffset : horizontalOffsets) {
                Vector3d entityPos = entity.position().add(hOffset.x, heightOffset, hOffset.z);
                
                // Check if there's a block in the way using block raytrace
                BlockRayTraceResult blockHit = world.clip(
                    new RayTraceContext(
                        eyePos,
                        entityPos,
                        RayTraceContext.BlockMode.COLLIDER,
                        RayTraceContext.FluidMode.NONE,
                        player
                    )
                );
                
                // If no block was hit, or the hit position is past the entity, line of sight is clear
                if (blockHit.getType() == RayTraceResult.Type.MISS) {
                    return true;
                }
                
                // Check if the block hit is closer than the entity
                double distanceToBlock = eyePos.distanceToSqr(blockHit.getLocation());
                double distanceToEntity = eyePos.distanceToSqr(entityPos);
                
                if (distanceToEntity < distanceToBlock) {
                    return true;  // Entity is in front of the block
                }
            }
        }
        
        return false;  // All raycasts were blocked
    }

    public LivingEntity getRayTracedEntities(World world, PlayerEntity player, Hand hand, int range){
        System.out.println("getRayTracedEntities");
        Vector3d startVec = player.getEyePosition(1.0F);
        Vector3d lookVec = player.getViewVector(1.0F).scale(range);
        Vector3d endVec = startVec.add(lookVec);
        AxisAlignedBB boundingBox = player.getBoundingBox().expandTowards(lookVec).inflate(1, 1, 1);
        EntityRayTraceResult entityRayTraceResult = RayTrace.rayTraceEntities(player, startVec, endVec, boundingBox, s -> s instanceof LivingEntity, range * range);
        if(entityRayTraceResult != null){
            LivingEntity entity = (LivingEntity) entityRayTraceResult.getEntity();
            if(!(entity instanceof PixelmonEntity) && !(entity instanceof StatueEntity)) {
                Teras.getLogger().info("Entidad incorrecta");
                return null;
            } else {
                Teras.getLogger().info("Entidad correcta");
                return entity;
            }
        } else {
            Teras.getLogger().info("EntityRayTraceResult: null");
        }

        return null;
    }
    
    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);

       if(!actualizarPad(stack)) return ActionResult.fail(stack);

        if(player.isShiftKeyDown() && world.isClientSide()){
            int smartRotomID = stack.getTag().getInt("PadID");
            ClientProxy.PadData pad = Teras.PROXY.getPadByID(smartRotomID);
            
            if(pad.view.getURL().contains("camara")){
                pad.view.runJS("takeScreenshot()", "");
                
                // NEW: Detect all entities on screen when taking a screenshot
                List<LivingEntity> entitiesInView = getEntitiesInView(world, player, MAX_DETECTION_DISTANCE, DETECTION_FOV);
                
                Teras.getLogger().info("=== ENTITIES DETECTED IN VIEW ===");
                Teras.getLogger().info("Total entities found: " + entitiesInView.size());
                
                for (LivingEntity entity : entitiesInView) {
                    if (entity instanceof PixelmonEntity) {
                        PixelmonEntity pixelmon = (PixelmonEntity) entity;
                        Teras.getLogger().info("Pokemon: " + pixelmon.getSpecies().getName() + 
                            " (Dex: " + pixelmon.getSpecies().getDex() + 
                            ", Form: " + pixelmon.getForm().getName() + 
                            ", Palette: " + pixelmon.getPalette().getName() + ")");
                    } else if (entity instanceof StatueEntity) {
                        StatueEntity statue = (StatueEntity) entity;
                        Teras.getLogger().info("Statue: " + statue.getSpecies().getName() + 
                            " (Dex: " + statue.getSpecies().getDex() + ")");
                    } else {
                        Teras.getLogger().info("Other Entity: " + entity.getType().getDescription().getString() + 
                            " at " + entity.position());
                    }
                }
                Teras.getLogger().info("=================================");
                
                return super.use(world, player, hand);
            }
        }

        LivingEntity entity = getRayTracedEntities(world, player, hand, (int)MAX_DETECTION_DISTANCE);
        assert entity != null;

        if(!(entity instanceof PixelmonEntity) && !(entity instanceof StatueEntity)){
            if(world.isClientSide()){
                Teras.PROXY.openMinePadGui(stack.getTag().getInt("PadID"));
            }

            return super.use(world, player, hand);
        }

        int dex;
        String form;
        String palette;

        if(entity instanceof PixelmonEntity){
            PixelmonEntity pixelmon = (PixelmonEntity) entity;
            dex = pixelmon.getSpecies().getDex();
            form = pixelmon.getForm().getName();
            palette = pixelmon.getPalette().getName();
        } else{
            StatueEntity statue = (StatueEntity) entity;
            dex = statue.getSpecies().getDex();
            form = statue.getPokemon().getForm().getName();
            palette = "";
        }


        if(world.isClientSide()){
            int smartRotomID = stack.getTag().getInt("PadID");
            ClientProxy.PadData smartRotom = Teras.PROXY.getPadByID(smartRotomID);

            smartRotom.view.runJS("openDex("+ dex +", '"+ form +"')", "");
            PlayerPokedex pokedex = new PlayerPokedex(player.getUUID());
            if(pokedex.get(dex) == PokedexRegistrationStatus.UNKNOWN){
                Messages.INSTANCE.sendToServer( new SMessageUpdateDex(dex, form, palette));
                return super.use(world, player, hand);
            }
        }
        if(!form.isEmpty() && !world.isClientSide()){
            ActualizarDex updateDex = new ActualizarDex(player.getUUID().toString(), dex, 1, form, palette);
            SmartRotomService.postRegistry(updateDex);
        }
        return super.use(world, player, hand);
    }


        /*
    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context) {
        if(!checkPad(stack)) return ActionResultType.FAIL;
        World world = context.getLevel();
        assert Teras.PROXY.getPadByID(stack.getTag().getInt("PadID")).view.getURL() != null;

        if(!world.isClientSide()){
            return super.onItemUseFirst(stack, context);
        }


        BlockState bloque = world.getBlockState(context.getClickedPos());
        String url = Teras.PROXY.getPadByID(stack.getTag().getInt("PadID")).view.getURL();
        if(url.toLowerCase().contains("rzap")){
            actualizarPad(stack);
            if(bloque.getBlock() == Blocks.JUKEBOX){
                BlockState state = BlockInit.TOCADISCOS.get().defaultBlockState();
                world.setBlock(context.getClickedPos(), state, 2);
                world.playSound(context.getPlayer(), context.getClickedPos(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }

        return super.onItemUseFirst(stack, context);
    }*/



    public static boolean actualizarPad(ItemStack stack){
        if(!stack.hasTag() || !stack.getTag().contains("PadID")){
            CompoundNBT nbt = new CompoundNBT();
            nbt.putString("PadURL", "http://www.google.es");
            int id = Teras.PROXY.getNextPadID();
            nbt.putInt("PadID", id);
            stack.setTag(nbt);

            Teras.getLogger().info("Se ha inicializado el PadID: " + id);
            Teras.PROXY.updatePad(id, stack.getTag(), true);
            return false;
        }
        Teras.getLogger().info("El PadID ya existe: " + stack.getTag().getInt("PadID"));
        return true;
    }

}