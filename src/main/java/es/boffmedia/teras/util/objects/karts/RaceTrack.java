package es.boffmedia.teras.util.objects.karts;

import java.util.ArrayList;
import java.util.LinkedList;

public class RaceTrack {
    String name;
    StartingDirection startingDirection;
    LinkedList<CoordinatePoint>  startingPoints;
    ArrayList<Checkpoint> checkpoints;

    public RaceTrack(String name, StartingDirection startingDirection, LinkedList<CoordinatePoint> startingPoints, ArrayList<Checkpoint> checkpoints) {
        this.name = name;
        this.startingDirection = startingDirection;
        this.startingPoints = startingPoints;
        this.checkpoints = checkpoints;
    }

    public String getName() {
        return name;
    }

    public StartingDirection getStartingDirection() {
        return startingDirection;
    }

    public LinkedList<CoordinatePoint> getStartingPoints() {
        return startingPoints;
    }

    public ArrayList<Checkpoint> getCheckpoints() {
        return checkpoints;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStartingDirection(StartingDirection startingDirection) {
        this.startingDirection = startingDirection;
    }

    public void setStartingPoints(LinkedList<CoordinatePoint> startingPoints) {
        this.startingPoints = startingPoints;
    }

    public void setCheckpoints(ArrayList<Checkpoint> checkpoints) {
        this.checkpoints = checkpoints;
    }

    public void addCheckpoint(Checkpoint checkpoint) {
        checkpoints.add(checkpoint);
    }

    public void addStartingPoint(CoordinatePoint startingPoint) {
        startingPoints.add(startingPoint);
    }

    public void removeCheckpoint(Checkpoint checkpoint) {
        checkpoints.remove(checkpoint);
    }

    public void removeStartingPoint(CoordinatePoint startingPoint) {
        startingPoints.remove(startingPoint);
    }

    public void printCheckpoints() {
        int i = 0;
        for (Checkpoint checkpoint : checkpoints) {
            System.out.println("#"+ ++i + " " + checkpoint.toString());
        }
    }

    public void printStartingPoints() {
        int i = 0;
        for (CoordinatePoint startingPoint : startingPoints) {
            System.out.println("#"+ ++i + " " + startingPoint.toString());
        }
    }

    @Override
    public String toString() {
        return name;
    }


}
