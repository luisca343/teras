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
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.util.*;

public class SmartRotom extends Item {
    private static final double MAX_DETECTION_DISTANCE = 25.0;
    
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

    public LivingEntity getRayTracedEntities(World world, PlayerEntity player, Hand hand, int range){
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