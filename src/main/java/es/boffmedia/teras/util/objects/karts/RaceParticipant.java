package es.boffmedia.teras.util.objects.karts;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;

public class RaceParticipant {
    private ServerPlayerEntity player;
    private Race raceIn;
    private int currentCheckpoint;
    private int currentLap;
    private long finishTime;
    private CoordinatePoint coords;

    public RaceParticipant(ServerPlayerEntity player, Race race){
        this.player = player;
        this.currentCheckpoint = 0;
        this.currentLap = 1;
        this.finishTime = 0;
        this.raceIn = race;
    }


    public void tick() {
        if(raceIn == null || raceIn.getStatus().equals(RaceStatus.FINISHED)) return;
        if(raceIn.getStatus().equals(RaceStatus.IN_PROGRESS)){
            //Punto nuevaPosicion = new Punto(jugador.getX(), jugador.getY(), jugador.getZ());
            Entity vehicle = player.getVehicle();
            if(vehicle == null) {

                return;
            };
            CoordinatePoint position = new CoordinatePoint(vehicle.getX(), vehicle.getY(), vehicle.getZ());

            if(position.equals(coords)) return;
            coords = position;
            raceIn.checkPlayerInCheckpoint(player, position);
        }
    }


    public Checkpoint getCurrentCheckpoint() {
        return raceIn.getCheckpoints().get(currentCheckpoint);
    }

    public int getCurrentCheckpointIndex(){
        return currentCheckpoint;
    }

    public int getNextCheckpointIndex(){
        return (getCurrentCheckpointIndex() + 1) % raceIn.getCheckpoints().size();
    }

    public void nextCheckpoint(){
        currentCheckpoint = (currentCheckpoint + 1);
    }

    public void setCurrentCheckpoint(int currentCheckpoint) {
        this.currentCheckpoint = currentCheckpoint;
    }

    public long getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(long finishTime) {
        this.finishTime = finishTime;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(ServerPlayerEntity player) {
        this.player = player;
    }

    public int getCurrentLap() {
        return currentLap;
    }

    public void setCurrentLap(int currentLap) {
        this.currentLap = currentLap;
    }

    public CoordinatePoint getCoords() {
        return coords;
    }

    public void setCoords(CoordinatePoint coords) {
        this.coords = coords;
    }

    public Race getRaceIn() {
        return raceIn;
    }

    public void setRaceIn(Race raceIn) {
        this.raceIn = raceIn;
    }
}
