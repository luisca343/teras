package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.ShapeFamily;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <b>piso</b>: one place a floor can be — Cuevas, Cuevas Infestadas. It owns everything a player
 * sees or fights, which is what separates this from the reverted "themes" idea, where a place was a
 * block palette laid over rooms shared with every other place.
 *
 * <p>What it deliberately does <i>not</i> own is difficulty. The enemy table here is <b>relative</b>
 * — what fights in this place and in what proportion — while the tramo scales the absolute numbers
 * by depth ({@link TierDef#dificultad}). That is what lets one piso be referenced by two dungeons at
 * different depths without being mistuned in one of them.</p>
 *
 * <p>Pure data, no Minecraft: block and sound ids are carried as strings and resolved by the build
 * layer, so the whole model can be validated in tests.</p>
 *
 * @param id          catalog key, and the namespace its room templates live under
 * @param nombre      what the title card shows — "Cuevas"
 * @param subtitulo   the flavour line under it, or "" for none
 * @param formas      the shape families this piso builds. A family it declines is never generated
 *                    and its template is never required — the one lever that cuts authoring cost
 *                    without letting a piso borrow another's rooms
 * @param luz         flat light level used when authoring and enforced at build
 * @param musica      sound event id, or "" — a resource location either way, so swapping a vanilla
 *                    track for a custom one later needs no code change
 * @param ambiente    ambient loop id, or ""
 * @param mecanica    signature mechanic key, or "" for none. Cuevas is the baseline and has none
 * @param maldiciones the curses this piso accepts. A piso restricted to few shapes must refuse
 *                    LABYRINTH or it sprawls to the room cap in one repeated footprint
 * @param jefes       boss pool override; empty inherits the tramo's
 * @param minijefes   mini-boss pool override; empty inherits the tramo's
 * @param salas       authored variants per room key. A key absent here resolves to the single
 *                    conventional template {@code teras:dungeon/<id>/<key>}, so a piso needs no
 *                    entry at all until it has more than one room for a key
 * @param enemigos    what fights here, relative — the tramo supplies the depth
 * @param decoracion  what its {@code decoracion:*} markers become, per surface
 */
public record FloorDef(String id,
                       String nombre,
                       String subtitulo,
                       Set<ShapeFamily> formas,
                       int luz,
                       String musica,
                       String ambiente,
                       String mecanica,
                       Set<Curse> maldiciones,
                       List<String> jefes,
                       List<String> minijefes,
                       Map<String, List<RoomVariant>> salas,
                       EnemyTable enemigos,
                       DecorTables decoracion) {

    /**
     * A piso with no tables of its own. Kept as a constructor rather than pushed onto every caller
     * because a piso is legitimately definable without them — the tables arrived after the model
     * did, and a piso that declares none simply never fights and never decorates.
     */
    public FloorDef(String id, String nombre, String subtitulo, Set<ShapeFamily> formas, int luz,
                    String musica, String ambiente, String mecanica, Set<Curse> maldiciones,
                    List<String> jefes, List<String> minijefes,
                    Map<String, List<RoomVariant>> salas) {
        this(id, nombre, subtitulo, formas, luz, musica, ambiente, mecanica, maldiciones, jefes,
                minijefes, salas, EnemyTable.EMPTY, DecorTables.EMPTY);
    }

    public FloorDef {
        enemigos = enemigos == null ? EnemyTable.EMPTY : enemigos;
        decoracion = decoracion == null ? DecorTables.EMPTY : decoracion;
    }

    /** The concrete shapes the generator may produce for this piso. */
    public Set<es.boffmedia.teras.dungeon.model.RoomShape> shapes() {
        return ShapeFamily.shapesOf(formas);
    }

    /** Every room key this piso is obliged to supply, given the families it declared. */
    public Set<String> requiredRooms() {
        return RoomKeys.requiredFor(formas);
    }

    /**
     * The templates {@code roomKey} may resolve to: the authored variants, or the one conventional
     * template when none are declared. Never empty, and never another piso's.
     */
    public List<RoomVariant> variants(String roomKey) {
        List<RoomVariant> declared = salas == null ? null : salas.get(roomKey);
        if (declared == null || declared.isEmpty()) {
            return List.of(RoomVariant.conventional(id, roomKey));
        }
        return List.copyOf(declared);
    }

    /** Every template this piso can ever place, for load-time existence checks. */
    public List<RoomVariant> allVariants() {
        List<RoomVariant> all = new ArrayList<>();
        for (String key : requiredRooms()) {
            all.addAll(variants(key));
        }
        return all;
    }

    public boolean overridesBosses() {
        return !jefes.isEmpty();
    }

    public boolean overridesMiniBosses() {
        return !minijefes.isEmpty();
    }

    public boolean accepts(Curse curse) {
        return maldiciones.contains(curse);
    }

    /**
     * Why this piso cannot be used, or an empty list when it can. Structural checks only — that a
     * template actually exists on disk is the build layer's business, since it needs the game's
     * structure manager.
     *
     * <p>Returning reasons rather than throwing is deliberate: a broken piso must be dropped from
     * selection with a log line naming what is wrong, never surfaced mid-build. The materializer's
     * job loop swallows exceptions, so a failure discovered there leaves a run waiting on a floor
     * that never lands.</p>
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (id == null || id.isBlank()) {
            problems.add("piso has no id");
        }
        if (nombre == null || nombre.isBlank()) {
            problems.add("piso '" + id + "' has no nombre — the title card would be blank");
        }
        if (formas == null || formas.isEmpty()) {
            problems.add("piso '" + id + "' declares no formas");
        } else if (!formas.contains(RoomKeys.MANDATORY_FAMILY)) {
            problems.add("piso '" + id + "' does not declare SINGLE, which every layout needs");
        }
        if (luz < 0 || luz > 15) {
            problems.add("piso '" + id + "' has luz " + luz + ", outside 0..15");
        }
        problems.addAll(enemigos.problems(id));
        problems.addAll(decoracion.problems(id));
        return problems;
    }
}
