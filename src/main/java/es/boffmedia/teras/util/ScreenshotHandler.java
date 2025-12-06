package es.boffmedia.teras.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.entities.pixelmon.StatueEntity;
import es.boffmedia.teras.Teras;
import noppes.npcs.entity.EntityNPCInterface;
import es.boffmedia.teras.util.objects.ScreenshotQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.montoyo.mcef.api.IJSQueryCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles screenshot capture and entity detection for the SmartRotom camera
 */
public class ScreenshotHandler {
    private static final Gson gson = new Gson();
    private static final double MAX_DETECTION_DISTANCE = 25.0;
    
    // Minimum screen coverage for entity to be considered "recognizable"
    // This represents the minimum percentage of screen height the entity should occupy
    private static final double MIN_SCREEN_COVERAGE_PERCENT = 10.0;

    /**
     * Handles the screenshot capture process with entity detection
     */
    public static void handleTakeScreenshot(String query, IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling takeScreenshot query");
        try {
            // Parse the query JSON into ScreenshotQuery object
            ScreenshotQuery screenshotQuery = gson.fromJson(query, ScreenshotQuery.class);
            
            // Get options from the parsed object
            boolean includeUI = screenshotQuery.isIncludeUI();
            String format = screenshotQuery.getFormat();
            int quality = screenshotQuery.getQuality();

            Teras.LOGGER.info("Screenshot options - includeUI: {}, format: {}, quality: {}", includeUI, format, quality);
            
            // Detect entities in view
            ClientPlayerEntity player = Minecraft.getInstance().player;
            World world = Minecraft.getInstance().level;
            
            JsonObject locationData = new JsonObject();
            List<JsonObject> entitiesData = new ArrayList<>();
            
            if (player != null && world != null) {
                // Get player position
                Vector3d playerPos = player.position();
                Vector3d eyePos = player.getEyePosition(1.0F);
                
                JsonObject playerPosJson = new JsonObject();
                playerPosJson.addProperty("x", Math.round(playerPos.x * 100.0) / 100.0);
                playerPosJson.addProperty("y", Math.round(eyePos.y * 100.0) / 100.0);
                playerPosJson.addProperty("z", Math.round(playerPos.z * 100.0) / 100.0);
                locationData.add("playerPosition", playerPosJson);
                
                // Get block player is looking at
                Vector3d lookVec = player.getViewVector(1.0F);
                Vector3d endPos = eyePos.add(lookVec.scale(MAX_DETECTION_DISTANCE));
                
                BlockRayTraceResult blockHit = world.clip(
                    new RayTraceContext(
                        eyePos,
                        endPos,
                        RayTraceContext.BlockMode.OUTLINE,
                        RayTraceContext.FluidMode.NONE,
                        player
                    )
                );
                
                if (blockHit.getType() == RayTraceResult.Type.BLOCK) {
                    JsonObject blockPosJson = new JsonObject();
                    blockPosJson.addProperty("x", blockHit.getBlockPos().getX());
                    blockPosJson.addProperty("y", blockHit.getBlockPos().getY());
                    blockPosJson.addProperty("z", blockHit.getBlockPos().getZ());
                    blockPosJson.addProperty("block", world.getBlockState(blockHit.getBlockPos()).getBlock().getDescriptionId());
                    locationData.add("lookingAt", blockPosJson);
                }
                
                // Get player's actual FOV setting
                double playerFov = Minecraft.getInstance().options.fov;
                
                List<LivingEntity> entitiesInView = getEntitiesInView(world, player, MAX_DETECTION_DISTANCE, playerFov);
                
                Teras.getLogger().info("=== ENTITIES DETECTED IN SCREENSHOT ===");
                Teras.getLogger().info("Total entities found: " + entitiesInView.size());
                
                for (int i = 0; i < entitiesInView.size(); i++) {
                    LivingEntity entity = entitiesInView.get(i);
                    Vector3d entityPos = entity.position();
                    Vector3d entityCenter = entityPos.add(0, entity.getBbHeight() / 2, 0);
                    double distance = player.getEyePosition(1.0F).distanceTo(entityCenter);
                    double coverage = calculateScreenCoverage(entity, distance, playerFov);
                    
                    JsonObject entityData = new JsonObject();
                    entityData.addProperty("distance", Math.round(distance * 10.0) / 10.0);
                    entityData.addProperty("coverage", Math.round(coverage * 10.0) / 10.0);
                    
                    JsonObject entityPosJson = new JsonObject();
                    entityPosJson.addProperty("x", Math.round(entityPos.x * 100.0) / 100.0);
                    entityPosJson.addProperty("y", Math.round(entityPos.y * 100.0) / 100.0);
                    entityPosJson.addProperty("z", Math.round(entityPos.z * 100.0) / 100.0);
                    entityData.add("position", entityPosJson);
                    
                    if (entity instanceof PixelmonEntity) {
                        PixelmonEntity pixelmon = (PixelmonEntity) entity;
                        entityData.addProperty("type", "pokemon");
                        entityData.addProperty("species", pixelmon.getSpecies().getName());
                        entityData.addProperty("dex", pixelmon.getSpecies().getDex());
                        entityData.addProperty("form", pixelmon.getForm().getName());
                        entityData.addProperty("palette", pixelmon.getPalette().getName());
                        
                        Teras.getLogger().info(String.format("#%d Pokemon: %s (Dex: %d, Form: %s, Palette: %s) - Distance: %.1f blocks, Coverage: %.1f%%, Position: (%.2f, %.2f, %.2f)",
                            i + 1,
                            pixelmon.getSpecies().getName(),
                            pixelmon.getSpecies().getDex(),
                            pixelmon.getForm().getName(),
                            pixelmon.getPalette().getName(),
                            distance,
                            coverage,
                            entityPos.x,
                            entityPos.y,
                            entityPos.z));
                    } else if (entity instanceof StatueEntity) {
                        StatueEntity statue = (StatueEntity) entity;
                        entityData.addProperty("type", "statue");
                        entityData.addProperty("species", statue.getSpecies().getName());
                        entityData.addProperty("dex", statue.getSpecies().getDex());
                        
                        Teras.getLogger().info(String.format("#%d Statue: %s (Dex: %d) - Distance: %.1f blocks, Coverage: %.1f%%, Position: (%.2f, %.2f, %.2f)",
                            i + 1,
                            statue.getSpecies().getName(),
                            statue.getSpecies().getDex(),
                            distance,
                            coverage,
                            entityPos.x,
                            entityPos.y,
                            entityPos.z));
                    } else if (entity instanceof EntityNPCInterface) {
                        EntityNPCInterface npc = (EntityNPCInterface) entity;
                        entityData.addProperty("type", "npc");
                        entityData.addProperty("name", npc.getName().getString());
                        
                        Teras.getLogger().info(String.format("#%d NPC: %s - Distance: %.1f blocks, Coverage: %.1f%%, Position: (%.2f, %.2f, %.2f)",
                            i + 1,
                            npc.getName().getString(),
                            distance,
                            coverage,
                            entityPos.x,
                            entityPos.y,
                            entityPos.z));
                    } else {
                        entityData.addProperty("type", "other");
                        entityData.addProperty("name", entity.getType().getDescription().getString());
                        
                        Teras.getLogger().info(String.format("#%d Other Entity: %s - Distance: %.1f blocks, Coverage: %.1f%%, Position: (%.2f, %.2f, %.2f)",
                            i + 1,
                            entity.getType().getDescription().getString(),
                            distance,
                            coverage,
                            entityPos.x,
                            entityPos.y,
                            entityPos.z));
                    }
                    
                    entitiesData.add(entityData);
                }
                Teras.getLogger().info("========================================");
            }
            
            // Capture the screenshot
            java.awt.image.BufferedImage screenshot = ScreenshotCapture.captureMinecraftScreen(includeUI);
            
            // Convert to base64
            String base64Image = ImageConverter.imageToBase64(screenshot, format, quality);

            Teras.LOGGER.info("Screenshot captured and converted to Base64");
            Teras.LOGGER.info("Base64 Image String (truncated): " + base64Image.substring(0, Math.min(100, base64Image.length())) + "...");
            
            // Send back to webapp with data URL prefix as JSON
            String dataUrl = "data:image/" + format + ";base64," + base64Image;
            JsonObject response = new JsonObject();
            response.addProperty("status", "ok");
            response.add("location", locationData);
            response.add("entities", gson.toJsonTree(entitiesData));
            response.addProperty("image", dataUrl);
            callback.success(gson.toJson(response));
            
        } catch (Exception e) {
            Teras.LOGGER.error("Screenshot failed", e);
            callback.failure(0, "Screenshot failed: " + e.getMessage());
        }
    }

    /**
     * Detects all entities visible on the player's screen within a given range
     * Prioritizes entities that are centered in the player's view
     */
    private static List<LivingEntity> getEntitiesInView(World world, ClientPlayerEntity player, double range, double playerFov) {
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
            
            // Calculate the actual FOV cone from player's FOV setting
            // We use half the horizontal FOV as the cone angle
            double fovConeAngle = playerFov / 2.0;
            
            // Check if entity is within FOV
            if (angle <= fovConeAngle) {
                // Check if entity is visible (not blocked)
                if (hasLineOfSight(world, player, eyePos, entity)) {
                    double distance = eyePos.distanceTo(entityCenter);
                    
                    // Calculate screen coverage: how big will the entity appear?
                    double screenCoverage = calculateScreenCoverage(entity, distance, playerFov);
                    
                    // Only consider entities that are large enough on screen to be recognizable
                    if (screenCoverage >= MIN_SCREEN_COVERAGE_PERCENT) {
                        // Calculate score: lower angle = better (more centered)
                        // Also factor in distance (closer is slightly better for same angle)
                        double score = angle + (distance / range) * 5.0; // Angle is primary, distance is secondary
                        
                        entitiesWithScores.add(new EntityWithScore(livingEntity, score, screenCoverage, distance));
                    }
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
        final double screenCoverage;
        final double distance;
        
        EntityWithScore(LivingEntity entity, double score, double screenCoverage, double distance) {
            this.entity = entity;
            this.score = score;
            this.screenCoverage = screenCoverage;
            this.distance = distance;
        }
    }
    
    /**
     * Calculates what percentage of the screen height the entity will occupy
     * Based on entity height, distance from player, and player's actual FOV setting
     * 
     * @param entity The entity to calculate coverage for
     * @param distance Distance from player to entity
     * @param playerFov Player's FOV setting (from game options)
     * @return Approximate percentage of screen height the entity occupies
     */
    private static double calculateScreenCoverage(Entity entity, double distance, double playerFov) {
        // Get entity height in blocks
        double entityHeight = entity.getBbHeight();
        
        // Convert FOV setting to actual vertical FOV
        // Minecraft FOV slider goes from 30 to 110, with 70 being normal
        // The FOV value represents horizontal FOV, we need to calculate vertical FOV
        // Assuming 16:9 aspect ratio (most common), but we'll calculate it properly
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getWidth();
        int screenHeight = mc.getWindow().getHeight();
        double aspectRatio = (double) screenWidth / screenHeight;
        
        // Convert horizontal FOV to radians
        double horizontalFovRadians = Math.toRadians(playerFov);
        
        // Calculate vertical FOV from horizontal FOV and aspect ratio
        // tan(vFov/2) = tan(hFov/2) / aspectRatio
        double verticalFovRadians = 2.0 * Math.atan(Math.tan(horizontalFovRadians / 2.0) / aspectRatio);
        
        // Visible height at this distance (based on vertical FOV)
        double visibleHeight = 2.0 * distance * Math.tan(verticalFovRadians / 2.0);
        
        // Calculate what percentage of screen the entity occupies
        double screenCoveragePercent = (entityHeight / visibleHeight) * 100.0;
        
        return screenCoveragePercent;
    }
    
    /**
     * Checks if there's a clear line of sight from the player to the entity
     * Uses block raytrace to ensure no blocks are blocking the view
     */
    private static boolean hasLineOfSight(World world, ClientPlayerEntity player, Vector3d eyePos, Entity entity) {
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
}