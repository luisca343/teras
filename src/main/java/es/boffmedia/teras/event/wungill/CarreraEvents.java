package es.boffmedia.teras.event.wungill;
import com.mrcrayfish.vehicle.entity.EngineTier;
import com.mrcrayfish.vehicle.entity.PoweredVehicleEntity;
import com.mrcrayfish.vehicle.entity.vehicle.GoKartEntity;
import com.mrcrayfish.vehicle.init.ModEntities;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.clientOld.CMessageCambioPosicion;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.UUID;


public class CarreraEvents {

    public static void iniciarCarrera(UUID uuid, int x, int y, int z){
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        World world = player.level;
        GoKartEntity vehicleEntity = new GoKartEntity(ModEntities.GO_KART.get(), world);
        vehicleEntity.setRequiresFuel(false);
        vehicleEntity.setColorRGB(1,2,3);
        vehicleEntity.setEngineTier(EngineTier.WOOD);
        vehicleEntity.setPos(x, y, z);
        //vehicleEntity.setOwner(uuid);
        vehicleEntity.yRot = -90;



        world.addFreshEntity(vehicleEntity);
        vehicleEntity.getSeatTracker().setSeatIndex(0, uuid);
        player.startRiding(vehicleEntity);
    }

    public static void moverVehiculo(UUID uuid, boolean mover){
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        Entity vehicle = player.getVehicle();
        if(vehicle instanceof PoweredVehicleEntity){
            PoweredVehicleEntity powered = (PoweredVehicleEntity) vehicle;
            powered.setEngine(mover);
        }
    }

    public static void setPosicion(UUID uuid, int posicion){
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        if(player == null) return;
        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageCambioPosicion(posicion+""));
    }

    public static void golpearCoche(UUID uuid){
        Teras.LOGGER.info("GOLPEANDO COCHE");
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        PoweredVehicleEntity vehicleEntity = (PoweredVehicleEntity) player.getVehicle();


        int tiempo = 2000;
        new Thread(() -> {
            vehicleEntity.setEngine(false);

            for(int i = 0; i < 8; i++){
                float f = MathHelper.wrapDegrees(vehicleEntity.yRot);
                float f2 = MathHelper.wrapDegrees(player.yRot);

                vehicleEntity.yRot = f+ 45f ;
                vehicleEntity.yRotO = f+ 45f ;
                player.yRot = f2+45f;

                Teras.LOGGER.info(vehicleEntity.yRot);
                try {
                    Thread.sleep(tiempo / 8);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            vehicleEntity.setEngine(true);
        }).start();
    }


}
