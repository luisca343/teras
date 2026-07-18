package es.boffmedia.teras.karts.mode;

import es.boffmedia.teras.karts.engine.RaceResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A championship across several circuits: points per finish, and a champion at the end.
 *
 * <p>Deliberately <i>not</i> a {@link RaceMode} — a Grand Prix is not a way of racing, it is a
 * sequence of races with a points table on top. Each round is an ordinary race, and its result is
 * handed here when it finishes.</p>
 *
 * <p>Pure state: no Minecraft, so the points and standings rules are unit-testable and the whole
 * series can be written to disk and picked up after a restart.</p>
 */
public final class GrandPrixSeries {

    public enum Status { PENDING, IN_PROGRESS, FINISHED }

    private final String name;
    private final List<String> tracks;
    private final int laps;
    private final int[] pointsTable;

    private int currentRound;
    private Status status = Status.PENDING;
    private final Map<UUID, Integer> points = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Integer> wins = new LinkedHashMap<>();

    public GrandPrixSeries(String name, List<String> tracks, int laps, int[] pointsTable) {
        this.name = name;
        this.tracks = List.copyOf(tracks);
        this.laps = Math.max(1, laps);
        this.pointsTable = pointsTable == null || pointsTable.length == 0
                ? new int[]{10, 8, 6, 4, 2, 1}
                : pointsTable.clone();
    }

    public String name() {
        return name;
    }

    public List<String> tracks() {
        return tracks;
    }

    public int laps() {
        return laps;
    }

    public int[] pointsTable() {
        return pointsTable.clone();
    }

    public Status status() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int currentRound() {
        return currentRound;
    }

    /** The circuit for the round about to be run, or null when the series is over. */
    public String currentTrack() {
        return currentRound < tracks.size() ? tracks.get(currentRound) : null;
    }

    public boolean hasNextRound() {
        return currentRound < tracks.size();
    }

    public void start() {
        status = Status.IN_PROGRESS;
    }

    /**
     * Records a finished round and moves to the next circuit.
     *
     * <p>Only finishers score. A retirement earns nothing, which is what makes finishing a race
     * you cannot win still worth doing.</p>
     */
    public void recordRound(RaceResult result) {
        for (RaceResult.Placement placement : result.placements()) {
            names.put(placement.playerId(), placement.playerName());
            if (placement.dnf()) {
                continue;
            }
            int index = placement.position() - 1;
            int scored = index >= 0 && index < pointsTable.length ? pointsTable[index] : 0;
            points.merge(placement.playerId(), scored, Integer::sum);
            if (placement.position() == 1) {
                wins.merge(placement.playerId(), 1, Integer::sum);
            }
        }
        currentRound++;
        if (!hasNextRound()) {
            status = Status.FINISHED;
        }
    }

    public int pointsFor(UUID player) {
        return points.getOrDefault(player, 0);
    }

    public int winsFor(UUID player) {
        return wins.getOrDefault(player, 0);
    }

    /** @param position 1-based standing in the championship. */
    public record Standing(UUID playerId, String playerName, int points, int wins, int position) {}

    /**
     * The championship table, best first. Ties are broken by race wins — the usual motorsport rule,
     * and better than leaving two drivers indistinguishable at the top.
     */
    public List<Standing> standings() {
        List<Map.Entry<UUID, Integer>> ordered = new ArrayList<>(points.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<UUID, Integer> entry) -> entry.getValue()).reversed()
                .thenComparing(Comparator.comparingInt(
                        (Map.Entry<UUID, Integer> entry) -> winsFor(entry.getKey())).reversed()));

        List<Standing> table = new ArrayList<>(ordered.size());
        int position = 1;
        for (Map.Entry<UUID, Integer> entry : ordered) {
            table.add(new Standing(entry.getKey(),
                    names.getOrDefault(entry.getKey(), "?"),
                    entry.getValue(),
                    winsFor(entry.getKey()),
                    position++));
        }
        return table;
    }

    /** Who is leading, or has won once the series is finished. */
    public Standing leader() {
        List<Standing> table = standings();
        return table.isEmpty() ? null : table.get(0);
    }

    // --- persistence support ----------------------------------------------------------------

    /** Restores a series mid-championship after a restart. */
    public void restore(int round, Status status, Map<UUID, Integer> points,
                        Map<UUID, String> names, Map<UUID, Integer> wins) {
        this.currentRound = round;
        this.status = status;
        this.points.clear();
        this.points.putAll(points);
        this.names.clear();
        this.names.putAll(names);
        this.wins.clear();
        this.wins.putAll(wins);
    }

    public Map<UUID, Integer> pointsSnapshot() {
        return Map.copyOf(points);
    }

    public Map<UUID, String> namesSnapshot() {
        return Map.copyOf(names);
    }

    public Map<UUID, Integer> winsSnapshot() {
        return Map.copyOf(wins);
    }
}
