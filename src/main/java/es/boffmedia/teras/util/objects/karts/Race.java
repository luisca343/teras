package es.boffmedia.teras.util.objects.karts;

import com.mrcrayfish.vehicle.entity.EngineTier;
import com.mrcrayfish.vehicle.entity.PoweredVehicleEntity;
import com.mrcrayfish.vehicle.entity.vehicle.ATVEntity;
import com.mrcrayfish.vehicle.init.ModEntities;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageRacePositionChange;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Race {
    private RaceTrack track;
    public int laps;
    private ArrayList<RaceParticipant> participants;
    private List<RaceParticipant> finishedParticipants = new ArrayList<>();
    private RaceStatus status;
    private ArrayList<UUID> startVotes;
    private long startTime;
    private long endTime;
    private SplineTrackPath trackPath;
    /** Position map updated by the ranking thread; read-only from the game thread. 1 = first place. */
    private final ConcurrentHashMap<UUID, Integer> positionMap = new ConcurrentHashMap<>();
    /** Single daemon thread for position recalculation, scoped to this race instance. */
    private final ExecutorService rankingExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Teras-Race-Ranking");
        t.setDaemon(true);
        return t;
    });

    public Race(RaceTrack track, int laps){
        this.track = track;
        this.laps = laps;
        this.participants = new ArrayList<>();
        this.status = RaceStatus.WAITING_PLAYERS;
        this.startVotes = new ArrayList<>();
        this.startTime = 0;
        this.endTime = 0;
        this.trackPath = new SplineTrackPath(track.getCheckpoints());
    }

    public void voteStart(UUID player){
        if (!startVotes.contains(player)){
            startVotes.add(player);
        }
        if (startVotes.size() == participants.size()){
            startCountdown();
        }
    }

    public void cancelVote(UUID player){
        startVotes.remove(player);
    }

    public void spawnVehicle(ServerPlayerEntity player, CoordinatePoint point, StartingDirection orientation) {
        World world = player.level;
        ATVEntity vehicleEntity = new ATVEntity(ModEntities.ATV.get(), world);
        vehicleEntity.setRequiresFuel(false);
        vehicleEntity.setColorRGB(1, 2, 3);
        vehicleEntity.setEngineTier(EngineTier.WOOD);
        vehicleEntity.setPos(point.getX() + 0.5f, point.getY() + 1, point.getZ() + 0.5f);
        vehicleEntity.disableFallDamage = true;

        Random random = new Random();
        int r = random.nextInt(255);
        int g = random.nextInt(255);
        int b = random.nextInt(255);
        vehicleEntity.setColorRGB(r,g,b);

        //vehicleEntity.setOwner(uuid);

        switch (orientation){
            case EAST:
                vehicleEntity.yRot = -90;
                break;
            case WEST:
                vehicleEntity.yRot = 90;
                break;
            case NORTH:
                vehicleEntity.yRot = 0;
                break;
            case SOUTH:
                vehicleEntity.yRot = 180;
                break;
        }

        world.addFreshEntity(vehicleEntity);
        vehicleEntity.getSeatTracker().setSeatIndex(0, player.getUUID());
        player.startRiding(vehicleEntity);
    }

    public void startCountdown() {
        this.status = RaceStatus.STARTING;
        int i = 0;
        for (RaceParticipant participant : participants) {
            CoordinatePoint startingPoint = track.getStartingPoints().get(i);
            StartingDirection orientacion = track.getStartingDirection() == null ? StartingDirection.NORTH : track.getStartingDirection();
            spawnVehicle(participant.getPlayer(), startingPoint, orientacion);
            i++;
        }

        rankingExecutor.execute(() -> {
            try {
                status = RaceStatus.STARTING;
                for (int j = 3; j > 0; j--) {
                    for (RaceParticipant participant : participants) {
                        ServerPlayerEntity player = participant.getPlayer();
                        MessageHelper.enviarTitulo(player, j + "");
                        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 1.0F, 2.0F);
                    }
                    Thread.sleep(1000);
                }

                for (RaceParticipant participant : participants) {
                    ServerPlayerEntity player = participant.getPlayer();
                    MessageHelper.enviarTitulo(player, "GO!");
                    player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 1.0F, 0.5F);
                    allowMove(participant.getPlayer().getUUID(), true);
                }
                start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Teras.LOGGER.warn("Race countdown interrupted");
            }
        });
    }

    public static void allowMove(UUID uuid, boolean move) {
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof PoweredVehicleEntity) {
            PoweredVehicleEntity powered = (PoweredVehicleEntity) vehicle;
            powered.setEngine(move);
        }
    }

    public void start() {
        this.status = RaceStatus.IN_PROGRESS;
        this.startTime = System.currentTimeMillis();

        participants.forEach(p -> p.setCurrentCheckpoint(0));

        calculatePositions();
    }

    public void calculatePositions() {
        // Run on the shared HTTP executor (daemon threads, bounded pool) rather than a raw new Thread.
        // Never sort the shared `participants` list in-place from a background thread — that races
        // with the game thread. Instead compute a sorted snapshot and push the results into positionMap.
        rankingExecutor.execute(() -> {
            try {
                while (status == RaceStatus.IN_PROGRESS) {
                    // Snapshot the list to avoid ConcurrentModificationException while sorting.
                    List<RaceParticipant> snapshot = new ArrayList<>(participants);

                    snapshot.sort((p1, p2) -> {
                        int lapCompare = Integer.compare(p2.getCurrentLap(), p1.getCurrentLap());
                        if (lapCompare != 0) return lapCompare;

                        Vector3d pos1 = getParticipantPosition(p1);
                        Vector3d pos2 = getParticipantPosition(p2);
                        double progress1 = trackPath.calculateProgress(pos1);
                        double progress2 = trackPath.calculateProgress(pos2);
                        return Double.compare(progress2, progress1);
                    });

                    // Write computed positions into the thread-safe map.
                    for (int i = 0; i < snapshot.size(); i++) {
                        positionMap.put(snapshot.get(i).getPlayer().getUUID(), i + 1);
                    }

                    // Send position update to each unfinished participant.
                    for (RaceParticipant participant : snapshot) {
                        if (participant.getFinishTime() == 0) {
                            int pos = positionMap.getOrDefault(participant.getPlayer().getUUID(), 0);
                            Messages.INSTANCE.send(
                                    PacketDistributor.PLAYER.with(() -> participant.getPlayer()),
                                    new CMessageRacePositionChange(pos)
                            );
                        }
                    }

                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Teras.LOGGER.warn("Race position calculator interrupted");
            }
        });
    }

    /** Returns this player's current race position (1 = first) from the computed ranking map. */
    public int getParticipantPosition(ServerPlayerEntity player) {
        return positionMap.getOrDefault(player.getUUID(), 0);
    }

    public Vector3d getParticipantPosition(RaceParticipant participant) {
        Entity vehicle = participant.getPlayer().getVehicle();
        if (vehicle == null) {
            return new Vector3d(0, 0, 0);
        }
        return new Vector3d(vehicle.getX(), vehicle.getY(), vehicle.getZ());
    }

    // Method to check if player is going the wrong way
    public boolean isGoingWrongWay(RaceParticipant participant) {
        if (participant.getCoords() == null) return false;

        Vector3d currentPos = getParticipantPosition(participant);
        Vector3d prevPos = new Vector3d(
                participant.getCoords().getX(),
                participant.getCoords().getY(),
                participant.getCoords().getZ()
        );

        double currentProgress = trackPath.calculateProgress(currentPos);
        double prevProgress = trackPath.calculateProgress(prevPos);

        // Consider wrap-around at finish line
        double progressDiff = currentProgress - prevProgress;
        if (progressDiff < -0.5) progressDiff += 1.0;
        if (progressDiff > 0.5) progressDiff -= 1.0;

        // If progress is decreasing significantly, player is going wrong way
        return progressDiff < -0.1;
    }

    public void checkPlayerInCheckpoint(ServerPlayerEntity player, CoordinatePoint point) {
        UUID uuid = player.getUUID();
        RaceParticipant participant = getParticipants().stream()
                .filter(p -> p.getPlayer().getUUID().equals(uuid))
                .findFirst()
                .orElse(null);
        if (participant == null) return;

        Checkpoint checkpoint = track.getCheckpoints().get(participant.getCurrentCheckpointIndex());
        if (checkpoint.isInCheckpoint(point)) {
            participant.nextCheckpoint();
            if (participant.getCurrentCheckpointIndex() == track.getCheckpoints().size()) {
                participant.setCurrentLap(participant.getCurrentLap() + 1);
                participant.setCurrentCheckpoint(0);
                if(participant.getCurrentLap() <= laps) MessageHelper.enviarMensaje(participant.getPlayer(), "Has completado una vuelta " + participant.getCurrentLap() + "/" + laps);
            }
            if (participant.getCurrentLap() > laps) {
                participant.setFinishTime(System.currentTimeMillis());
                finishedParticipants.add(participant);
                int position = finishedParticipants.size();

                String title;
                String subtitle;
                if (position == 1) {
                    title = "§e§lVICTORIA!";
                    subtitle = "§bHas quedado primero en " + MessageHelper.formatearTiempo(participant.getFinishTime() - startTime);
                    player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                } else {
                    title = "§b§lCARRERA TERMINADA";
                    subtitle = String.format("§fHas terminado §e%d%s §fen %s", position, "º", MessageHelper.formatearTiempo(participant.getFinishTime() - startTime));
                    player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
                }

                MessageHelper.enviarTitulo(player, title, 10, 70, 20);
                MessageHelper.enviarTitulo(player, subtitle, 10, 70, 20);

                player.getVehicle().remove();
                MessageHelper.enviarMensaje(player, "Has terminado la carrera en " + MessageHelper.formatearTiempo(participant.getFinishTime() - startTime));
                Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> participant.getPlayer()), new CMessageRacePositionChange(0));

                // Check if all players have finished
                if (finishedParticipants.size() == participants.size()) {
                    end();
                }
            }
        }
    }


    public ArrayList<Checkpoint> getCheckpoints() {
        return track.getCheckpoints();
    }

    public ArrayList<RaceParticipant> getParticipants() {
        return participants;
    }

    public void setParticipants(ArrayList<RaceParticipant> participants) {
        this.participants = participants;
    }

    public RaceTrack getTrack() {
        return track;
    }

    public void setTrack(RaceTrack track) {
        this.track = track;
    }

    public ArrayList<UUID> getStartVotes() {
        return startVotes;
    }

    public void setStartVotes(ArrayList<UUID> startVotes) {
        this.startVotes = startVotes;
    }

    public RaceStatus getStatus() {
        return status;
    }

    public void setStatus(RaceStatus status) {
        this.status = status;
    }

    public void end() {
        this.status = RaceStatus.FINISHED;
        this.endTime = System.currentTimeMillis();
        rankingExecutor.shutdownNow();
        Teras.raceManager.activeRaces.remove(this);


        if(participants.size() == 1) {
            RaceParticipant participant = participants.get(0);
            Teras.raceManager.leaveRace(participant.getPlayer());
            return;
        }

        // Announce overall results
        MessageHelper.enviarMensajeGlobal("§6§l=== §b§lResultados Finales §6§l===");
        for (int i = 0; i < finishedParticipants.size(); i++) {
            RaceParticipant participant = finishedParticipants.get(i);
            ServerPlayerEntity player = participant.getPlayer();
            long raceTime = participant.getFinishTime() - startTime;
            String formattedTime = MessageHelper.formatearTiempo(raceTime);
            Teras.raceManager.leaveRace(participant.getPlayer());


            String positionColor;
            if (i == 0) positionColor = "§e"; // Gold for 1st
            else if (i == 1) positionColor = "§7"; // Gray for 2nd
            else if (i == 2) positionColor = "§6"; // Dark Gold for 3rd
            else positionColor = "§f"; // White for others

            String message = String.format("%s%d. §r§b%s §7- §aTiempo: §f%s",
                    positionColor, i + 1, player.getName().getString(), formattedTime);

            MessageHelper.enviarMensajeGlobal(message);
        }
        MessageHelper.enviarMensajeGlobal("§6§l========================");

    }



    public long getRemainingTime() {
        if (status != RaceStatus.IN_PROGRESS) {
            return 0;
        }
        long maxRaceTime = 10 * 60 * 1000; // 10 minutes in milliseconds
        long elapsedTime = System.currentTimeMillis() - startTime;
        return Math.max(0, maxRaceTime - elapsedTime);
    }

    public long getStartTime() {
        return startTime;
    }
}

