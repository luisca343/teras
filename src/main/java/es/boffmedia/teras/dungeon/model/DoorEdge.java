package es.boffmedia.teras.dungeon.model;

/**
 * One shared wall segment between two distinct rooms: the doorway (or breakable wall) sits on the
 * {@code dir} face of {@code cell}. A large room sharing a two-cell boundary with a neighbor gets
 * two edges, as in Isaac. Computed once at generation time — the legacy paster re-derived
 * connections from adjacency at paste time and needed parent/child/sibling suppression to avoid
 * carving holes inside multi-cell rooms.
 */
public record DoorEdge(GridPos cell, GridDir dir, Room from, Room to, DoorKind kind) {

    public GridPos neighborCell() {
        return cell.step(dir);
    }
}
