package es.boffmedia.teras.util.math.vector;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.Predicate;

public class RayTrace {
    public static EntityRayTraceResult rayTraceEntities(Entity shooter, Vector3d startVec, Vector3d endVec, AxisAlignedBB boundingBox, Predicate<Entity> filter, double dist) {
        World world = shooter.level;
        double distance = dist;
        Entity rayTracedEntity = null;
        Vector3d hitVec = null;

        for (Entity entity : world.getEntities(shooter, boundingBox, filter)) {
            AxisAlignedBB boxToCheck = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vector3d> optional = boxToCheck.clip(startVec, endVec);

            if (boxToCheck.contains(startVec)) {
                if (distance >= 0.0D) {
                    rayTracedEntity = entity;
                    hitVec = optional.orElse(startVec);
                    distance = 0.0D;
                }
            }
            else if (optional.isPresent()) {
                Vector3d vector = optional.get();
                double sqDist = startVec.distanceToSqr(vector);

                if (sqDist < distance || distance == 0.0D) {
                    if (entity.getRootVehicle() == shooter.getRootVehicle() && !entity.canRiderInteract()) {
                        if (distance == 0.0D) {
                            rayTracedEntity = entity;
                            hitVec = vector;
                        }
                    }
                    else {
                        rayTracedEntity = entity;
                        hitVec = vector;
                        distance = sqDist;
                    }
                }
            }
        }

        return rayTracedEntity == null ? null : new EntityRayTraceResult(rayTracedEntity, hitVec);
    }
}
