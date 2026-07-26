package es.boffmedia.teras.dungeon.model;

/**
 * How one doorway is dressed: the frame around it and, where a door is held shut for the whole
 * floor, the gate that fills it.
 *
 * <p>A door used to be a hole. Three blocks by three, air, and iron bars over it while a room was
 * in combat — so a boss door, a treasure door and a corridor were the same opening, and a room the
 * boss's death would open looked exactly like a room this fight would open. The frame is what makes
 * a doorway <i>say</i> something from across the room, and it costs no floor space to say it: the
 * ring is drawn in the wall plane, which {@code RoomAudit} exempts, and the relief stands in the two
 * columns beside the opening, which are outside the walkable band.</p>
 *
 * <p>Who a door belongs to is the {@link #winner} of its two rooms — the more special of them —
 * applied to <b>both</b> faces, so the frame is a sign read from the corridor and a place-marker
 * read from inside. Secret walls are excluded before this is ever asked: a frame on a secret is a
 * frame that gives it away.</p>
 *
 * <p>Block ids are strings, resolved by the build layer, so the whole thing stays testable.</p>
 *
 * @param marco  the frame: the ring's sides and top, the relief jambs and the lintel beam
 * @param acento the keystone and the ring's corners
 * @param luz    the lamp on each jamb top, or "" for an unlit door
 * @param umbral the threshold course under the opening
 * @param porton the panel of a gate held shut for the floor (devil, Orden), or "" if none
 * @param marca  that gate's centre block — the mark on it, or ""
 * @param alta   a taller frame with a stepped lintel: the boss, and nothing else
 */
public record DoorStyle(String marco, String acento, String luz, String umbral,
                        String porton, String marca, boolean alta) {

    public DoorStyle {
        marco = marco == null ? "" : marco;
        acento = acento == null || acento.isBlank() ? marco : acento;
        luz = luz == null ? "" : luz;
        umbral = umbral == null || umbral.isBlank() ? marco : umbral;
        porton = porton == null ? "" : porton;
        marca = marca == null ? "" : marca;
    }

    /** A frame with no gate of its own — every door that is opened by winning a fight. */
    public static DoorStyle frame(String marco, String acento, String luz, String umbral) {
        return new DoorStyle(marco, acento, luz, umbral, "", "", false);
    }

    /** The boss's frame: the same parts, taller, with a stepped lintel. */
    public static DoorStyle tall(String marco, String acento, String luz, String umbral) {
        return new DoorStyle(marco, acento, luz, umbral, "", "", true);
    }

    /** A frame around a gate that stays shut until the floor's boss falls. */
    public static DoorStyle gate(String marco, String acento, String luz, String umbral,
                                 String porton, String marca) {
        return new DoorStyle(marco, acento, luz, umbral, porton, marca, false);
    }

    /** Whether this style closes its doorway at build time rather than only during a fight. */
    public boolean hasGate() {
        return !porton.isBlank();
    }

    public boolean lit() {
        return !luz.isBlank();
    }

    /**
     * The room whose style a door between {@code a} and {@code b} wears — the more special of the
     * two, by {@link #rank}. Ties cannot happen: a door joins two distinct rooms, and two rooms of
     * the same type simply agree.
     */
    public static RoomType winner(RoomType a, RoomType b) {
        return rank(a) <= rank(b) ? a : b;
    }

    /**
     * How special a room is, lowest first. The order is the sign language a player learns: the
     * three promises outrank the fights, the fights outrank the markets, and NORMAL loses to
     * everything, which is what makes a frame worth looking at.
     *
     * <p><b>A host loses to what hangs off it.</b> ORDEN and DEVIL_DEAL are satellites of the sala
     * del sello — every door they have is a door they share with EXIT — so ranking the sello above
     * them made both flanks of that chamber wear the sello's own basalt and amethyst, identical to
     * each other and to the room the party is standing in. That is the exact confusion
     * {@link DoorKind#GRACIA} exists to prevent. The sello does not lose anything by it: its own
     * grand door never comes through here, because {@code unbarSealDoors} names {@link
     * RoomType#EXIT} outright at the reveal and a SELLO edge is not dressed at build at all. So this
     * rank is only ever consulted for the two satellites, and there the satellite is the answer.</p>
     *
     * <p>The secrets sit at the bottom rather than being absent because the ordering has to be
     * total — but nothing ever reads their style: a secret's doorway is a cracked wall, and a
     * dressed one would be a secret with a sign over it.</p>
     */
    public static int rank(RoomType type) {
        return switch (type) {
            case ORDEN -> 0;
            case DEVIL_DEAL -> 1;
            case EXIT -> 2;
            case BOSS -> 3;
            case MINI_BOSS -> 4;
            case CURSE -> 5;
            case SACRIFICE -> 6;
            case CHALLENGE -> 7;
            case ARCADE -> 8;
            case SHOP -> 9;
            case TREASURE -> 10;
            case SUPER_SECRET -> 11;
            case SECRET -> 12;
            case START -> 13;
            case NORMAL -> 14;
        };
    }
}
