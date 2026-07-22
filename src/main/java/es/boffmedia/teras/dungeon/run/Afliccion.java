package es.boffmedia.teras.dungeon.run;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A run-long drawback the party <b>chose</b> to accept — the third currency, beside coins and
 * maximum health.
 *
 * <h2>Why a third one at all</h2>
 *
 * <p>Coins mean you have less and health means you are weaker; both are <i>you</i> shrinking. An
 * affliction makes the run itself harsher, so a party can be rich, healthy and in deep trouble. It
 * is free at the moment it is taken and expensive for every floor after, which is what makes it a
 * price rather than a punishment.</p>
 *
 * <h2>The rule every one of these has to pass</h2>
 *
 * <p><b>It must change what you do, not how long it takes.</b> A drafted {@code cerrojos} — doors
 * stay sealed longer — was cut for failing it: rooms open when the last enemy dies, so holding the
 * doors shut afterwards taxes time and decides nothing. A drafted {@code miseria} (coins worth less)
 * was cut for being the same economic axis as {@link #AVARICIA} and blunter.</p>
 *
 * <p>Not called {@code Curse}. That enum already means floor-generation modifiers picked at entry
 * (LABYRINTH, LOST), and one word for two systems would confuse both forever.</p>
 *
 * <p>Pure — no Minecraft — so the catalog and its rules are testable without a server. The effects
 * are applied by whoever owns each seam.</p>
 *
 * @param id          config and wire key
 * @param scope       whether it lands on the whole party or only the player who took it
 * @param nombre      what the HUD shows
 * @param descripcion one line saying what it does, shown where it is offered
 */
public record Afliccion(String id, Scope scope, String nombre, String descripcion) {

    /**
     * Who lives with it.
     *
     * <p>Mixed on purpose. A party-wide affliction is a group decision and creates the argument
     * before anyone clicks, which is where the drama is; a personal one is a price a single player
     * can choose to pay for the party's benefit, the same shape the devil deal's hearts already
     * have.</p>
     */
    public enum Scope { PARTY, PERSONAL }

    // --- the catalog ------------------------------------------------------------------------
    //
    // Each is on a distinct axis: navigation, the shape of a fight, what you can buy, sustain,
    // mobility, body. Two on the same axis means one of them is redundant.

    /** Navigation. The minimap goes dark, which is what makes the shop's map and compass worth it. */
    public static final Afliccion NIEBLA = new Afliccion("niebla", Scope.PARTY,
            "Niebla", "El mapa de la planta se apaga.");

    /** The shape of a fight: more of them, each frailer. Crowd control over single-target. */
    public static final Afliccion ENJAMBRE = new Afliccion("enjambre", Scope.PARTY,
            "Enjambre", "Las oleadas traen la mitad más de enemigos, pero más débiles.");

    /** What you can afford. The shop is where a run's decisions are made. */
    public static final Afliccion AVARICIA = new Afliccion("avaricia", Scope.PARTY,
            "Avaricia", "La tienda cobra el doble.");

    /** Sustain, against a lockdown that already makes a bought potion the only healing there is. */
    public static final Afliccion SANGRIA = new Afliccion("sangria", Scope.PARTY,
            "Sangría", "Las pociones curan la mitad.");

    /** Mobility, and yours alone. */
    public static final Afliccion PLOMO = new Afliccion("plomo", Scope.PERSONAL,
            "Plomo", "No puedes esprintar.");

    /** Body. Rides the same hp debt a devil deal takes, so a respawn re-applies it. */
    public static final Afliccion PULSO_DEBIL = new Afliccion("pulso_debil", Scope.PERSONAL,
            "Pulso débil", "Dos corazones menos de vida máxima.");

    private static final Map<String, Afliccion> ALL = new LinkedHashMap<>();

    static {
        for (Afliccion afliccion : List.of(NIEBLA, ENJAMBRE, AVARICIA, SANGRIA, PLOMO, PULSO_DEBIL)) {
            ALL.put(afliccion.id(), afliccion);
        }
    }

    /** Every affliction that exists, in offer order. */
    public static List<Afliccion> all() {
        return List.copyOf(ALL.values());
    }

    /** By id, or null — so a caller can use it as the test. */
    public static Afliccion byId(String id) {
        return id == null ? null : ALL.get(id);
    }

    public static boolean exists(String id) {
        return byId(id) != null;
    }

    public boolean isParty() {
        return scope == Scope.PARTY;
    }

    /**
     * The ids in {@code names} that are not afflictions, as lines to log.
     *
     * <p>Pure and taking the collection in, for the same reason {@code MechanicDef.problems} does:
     * a config naming something that does not exist should be a line at boot, not a silence, and the
     * rule should be testable without a game.</p>
     */
    public static List<String> problems(Collection<String> names) {
        return names.stream()
                .filter(name -> !exists(name))
                .map(name -> "unknown afliccion '" + name + "'. Known: " + String.join(", ", ALL.keySet()))
                .toList();
    }
}
