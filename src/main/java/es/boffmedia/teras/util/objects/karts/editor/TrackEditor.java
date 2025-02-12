package es.boffmedia.teras.util.objects.karts.editor;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import es.boffmedia.teras.util.objects.karts.*;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.*;

public class TrackEditor {
    private final RaceTrack track;
    private final Map<UUID, TrackEditorSession> editorSessions = new HashMap<>();

    public TrackEditor(RaceTrack track) {
        this.track = track;
    }

    public void startEditing(ServerPlayerEntity player) {
        UUID playerUUID = player.getUUID();
        TrackEditorSession session = new TrackEditorSession(player, track);
        editorSessions.put(playerUUID, session);

        // Send initial editor info
        sendEditorInfo(player);

        // Show initial visualization
        visualizeTrack(player);
    }

    private void sendEditorInfo(ServerPlayerEntity player) {
        player.sendMessage(new StringTextComponent(TextFormatting.GREEN + "=== Track Editor Mode ==="), player.getUUID());
        player.sendMessage(new StringTextComponent(TextFormatting.YELLOW + "Track: " + track.getName()), player.getUUID());
        player.sendMessage(new StringTextComponent(TextFormatting.AQUA + "Checkpoints: " + track.getCheckpoints().size()), player.getUUID());
        player.sendMessage(new StringTextComponent(TextFormatting.GOLD + "Starting Points: " + track.getStartingPoints().size()), player.getUUID());
    }

    public void stopEditing(ServerPlayerEntity player) {
        UUID playerUUID = player.getUUID();
        editorSessions.remove(playerUUID);
    }

    public void visualizeTrack(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.level;

        // Visualize checkpoints
        for (Checkpoint checkpoint : track.getCheckpoints()) {
            visualizeCheckpoint(world, checkpoint);
        }

        // Visualize starting points
        for (CoordinatePoint startPoint : track.getStartingPoints()) {
            visualizeStartingPoint(world, startPoint);
        }
    }

    public void visualizeCheckpoint(ServerWorld world, Checkpoint checkpoint) {
        CoordinatePoint start = checkpoint.getStart();
        CoordinatePoint end = checkpoint.getEnd();

        // Get min and max for each coordinate to handle any orientation
        double minX = Math.min(start.getX(), end.getX());
        double maxX = Math.max(start.getX(), end.getX());
        double minY = Math.min(start.getY(), end.getY()) + 1; // Add 1 block to Y
        double maxY = Math.max(start.getY(), end.getY()) + 1; // Add 1 block to Y
        double minZ = Math.min(start.getZ(), end.getZ());
        double maxZ = Math.max(start.getZ(), end.getZ());

        // Bottom and top edges
        for(double x = minX; x <= maxX; x += 0.5) {
            for(double z = minZ; z <= maxZ; z += 0.5) {
                spawnParticle(world, x, minY, z); // Bottom
                spawnParticle(world, x, maxY, z); // Top
            }
        }

        // Vertical edges
        for(double y = minY; y <= maxY; y += 0.5) {
            spawnParticle(world, minX, y, minZ);
            spawnParticle(world, minX, y, maxZ);
            spawnParticle(world, maxX, y, minZ);
            spawnParticle(world, maxX, y, maxZ);
        }
    }

    public void visualizeStartingPoint(ServerWorld world, CoordinatePoint point) {
        double x = point.getX() + 0.5;
        double y = point.getY() + 1; // Add 1 block to Y
        double z = point.getZ() + 0.5;

        // Create a column of particles
        for(double yOffset = 0; yOffset <= 2; yOffset += 0.2) {
            spawnParticle(world, x, y + yOffset, z);
        }

        // Create a cross pattern at the base
        for(double offset = -0.5; offset <= 0.5; offset += 0.2) {
            spawnParticle(world, x + offset, y, z);
            spawnParticle(world, x, y, z + offset);
        }
    }

    private void spawnParticle(ServerWorld world, double x, double y, double z) {
        world.sendParticles(ParticleTypes.END_ROD,
                x, y, z,
                1, // particle count
                0, // xSpeed
                0, // ySpeed
                0, // zSpeed
                0 // speed
        );
    }

    public void validateTrack(ServerPlayerEntity player) {
        List<String> issues = new ArrayList<>();

        // Check minimum checkpoints
        if (track.getCheckpoints().size() < 2) {
            issues.add(TextFormatting.RED + "Track must have at least 2 checkpoints");
        }

        // Check minimum starting points
        if (track.getStartingPoints().size() < 1) {
            issues.add(TextFormatting.RED + "Track must have at least 1 starting point");
        }

        // Check checkpoint connectivity
        validateCheckpointConnectivity(issues);

        // Send validation results
        if (issues.isEmpty()) {
            player.sendMessage(new StringTextComponent(TextFormatting.GREEN + "Track validation passed!"), player.getUUID());
        } else {
            player.sendMessage(new StringTextComponent(TextFormatting.RED + "Track validation failed:"), player.getUUID());
            for (String issue : issues) {
                player.sendMessage(new StringTextComponent(issue), player.getUUID());
            }
        }
    }

    private void validateCheckpointConnectivity(List<String> issues) {
        List<Checkpoint> checkpoints = track.getCheckpoints();
        for (int i = 0; i < checkpoints.size() - 1; i++) {
            Checkpoint current = checkpoints.get(i);
            Checkpoint next = checkpoints.get(i + 1);

            double distance = current.getEnd().distance(next.getStart());
            if (distance > 50) { // Maximum reasonable distance between checkpoints
                issues.add(TextFormatting.YELLOW + "Warning: Large gap between checkpoints " +
                        (i + 1) + " and " + (i + 2) + " (" + Math.round(distance) + " blocks)");
            }
        }
    }

    public void previewRace(ServerPlayerEntity player) {
        // Teleport player to first starting point
        if (!track.getStartingPoints().isEmpty()) {
            CoordinatePoint start = track.getStartingPoints().get(0);
            player.teleportTo(start.getX(), start.getY(), start.getZ());

            // Give temporary speed effect for preview
            player.addEffect(new net.minecraft.potion.EffectInstance(
                    net.minecraft.potion.Effects.MOVEMENT_SPEED, 600, 1));
        }
    }
}