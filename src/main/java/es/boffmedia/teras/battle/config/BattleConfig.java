package es.boffmedia.teras.battle.config;

import com.google.gson.annotations.SerializedName;
import es.boffmedia.teras.battle.model.Recompensa;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Engine-neutral combat/event config, deserialized from the SmartRotom JSON (Gson). Accessors return
 * Teras enums ({@link BattleMode}, {@link AiMode}) and plain strings; each {@code BattleProvider} maps
 * them to its engine. Serialized field names are Spanish.
 */
public class BattleConfig {
    private String nombre;          // Trainer / event name
    private String nivel;           // Rival team level spec: "50", "+5", "-3", "0"/"="/"IGUALADO"
    private int dinero;             // Money awarded on defeat
    private String modalidad;       // Battle mode: doble, triple, rotatorio, horda, raid, (single)
    private String tamanoEquipos;   // Team sizes "AvsB" (player vs rival), e.g. "3vs6"
    private String frecuencia;      // Combat frequency (DIA, DIA_MC, SEMANA, MES)
    private ArrayList<Recompensa> recompensas; // Items awarded on defeat
    private ArrayList<String> normas;   // Clause names (engine maps to its clause registry)
    @SerializedName(value = "gimmick", alternate = {"mecanica"}) // both keys accepted
    private ArrayList<String> gimmick;  // Allowed gimmicks (Mega, Z, Dynamax, Tera)
    private String IA;              // AI difficulty label
    private boolean curar;          // Heal player's team before the battle
    private boolean preview;        // Show the rival team preview
    private String logro;           // Achievement id awarded on victory
    private boolean exp;            // Whether the battle grants experience
    private int[] equipos;          // Available team ids to pick from

    // Non-serialized (populated by the loader, not written back).
    private transient String nombreArchivo; // Config id (the folder name)
    private transient String carpeta;       // "entrenadores" or "eventos"
    private transient String teamPaste;     // Raw PokePaste/Showdown team text for the chosen team

    public BattleConfig() {
        this.dinero = 0;
        this.nivel = "0";
        this.modalidad = "doble";
        this.IA = "TACTICA";
        this.exp = false;
        this.curar = false;
        this.preview = false;
        this.frecuencia = "DIA";
        this.recompensas = new ArrayList<>();
        this.normas = new ArrayList<>();
        this.gimmick = new ArrayList<>();
        this.equipos = new int[]{1};
    }

    /* ---- Battle mode / team sizes ---- */

    public BattleMode getBattleMode() {
        return BattleMode.fromLabel(modalidad);
    }

    /** Active Pokémon per side the player controls at once. */
    public int getPlayerActiveCount() {
        return getBattleMode().playerActive();
    }

    /** Active Pokémon per side the rival controls at once. */
    public int getRivalActiveCount() {
        return getBattleMode().rivalActive();
    }

    /** Total Pokémon in the player's selectable team (left side of "AvsB"). */
    public int getPlayerTeamSize() {
        return teamSizePart(0);
    }

    /** Total Pokémon in the rival's team (right side of "AvsB"). */
    public int getRivalTeamSize() {
        return teamSizePart(1);
    }

    /** Parses one side of "AvsB" defensively; falls back to a full party (6) on missing/garbled data. */
    private int teamSizePart(int side) {
        if (tamanoEquipos == null) return 6;
        String[] parts = tamanoEquipos.split("vs");
        if (parts.length <= side) return 6;
        try {
            return Integer.parseInt(parts[side].trim());
        } catch (NumberFormatException e) {
            return 6;
        }
    }

    /* ---- Level ---- */

    /** Resolves the rival team level given the player's highest level (handles "+N"/"-N"/"="/fixed). */
    public int calculateTeamLevel(int playerLevel) {
        if (nivel == null) return playerLevel;
        if (nivel.contains("+")) return playerLevel + Integer.parseInt(nivel.split("\\+")[1]);
        if (nivel.contains("-")) return playerLevel - Integer.parseInt(nivel.split("-")[1]);
        switch (nivel) {
            case "0":
            case "=":
            case "IGUALADO":
            case "EQUAL":
                return playerLevel;
            default:
                return Integer.parseInt(nivel);
        }
    }

    /* ---- Rules / AI / gimmicks ---- */

    /** Clause names, lower-cased; each provider maps them to its own clause registry. */
    public List<String> getClauses() {
        List<String> out = new ArrayList<>();
        if (normas == null) return out;
        for (String n : normas) {
            if (n != null) out.add(n.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public AiMode getAiMode() {
        return AiMode.fromLabel(IA);
    }

    public List<String> getGimmicks() {
        return gimmick == null ? new ArrayList<>() : gimmick;
    }

    public boolean allowsMega() {
        return hasGimmick("MEGA");
    }

    public boolean allowsDynamax() {
        return hasGimmick("DYNAMAX", "DYNA", "GIGANTAMAX", "GMAX", "MAX");
    }

    public boolean allowsTera() {
        return hasGimmick("TERA", "TERASTAL");
    }

    public boolean allowsZ() {
        return hasGimmick("Z", "ZMOVE", "Z-MOVE");
    }

    private boolean hasGimmick(String... names) {
        if (gimmick == null) {
            return false;
        }
        for (String g : gimmick) {
            if (g == null) {
                continue;
            }
            String upper = g.trim().toUpperCase(Locale.ROOT);
            for (String name : names) {
                if (upper.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /* ---- Flags / misc ---- */

    public boolean healBeforeStart() {
        return curar;
    }

    public boolean hasPreview() {
        return preview;
    }

    public boolean isExp() {
        return exp;
    }

    public String getFrecuencia() {
        return frecuencia == null ? "DIARIA" : frecuencia;
    }

    /** Events live under the "eventos" folder (wild encounters); everything else is a trainer. */
    public boolean esEntrenador() {
        return !"eventos".equals(carpeta);
    }

    /* ---- Plain getters/setters ---- */

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }

    public int getDinero() { return dinero; }
    public void setDinero(int dinero) { this.dinero = dinero; }

    public void setModalidad(String modalidad) { this.modalidad = modalidad; }

    public String getTamanoEquipos() { return tamanoEquipos; }
    public void setTamanoEquipos(String tamanoEquipos) { this.tamanoEquipos = tamanoEquipos; }

    public void setFrecuencia(String frecuencia) { this.frecuencia = frecuencia; }

    public ArrayList<Recompensa> getRecompensas() { return recompensas; }
    public void setRecompensas(ArrayList<Recompensa> recompensas) { this.recompensas = recompensas; }

    public void setNormas(ArrayList<String> normas) { this.normas = normas; }
    public void setGimmick(ArrayList<String> gimmick) { this.gimmick = gimmick; }

    public void setIA(String ia) { this.IA = ia; }

    public void setCurar(boolean curar) { this.curar = curar; }
    public void setPreview(boolean preview) { this.preview = preview; }

    public String getLogro() { return logro; }
    public void setLogro(String logro) { this.logro = logro; }

    public void setExp(boolean exp) { this.exp = exp; }

    public int[] getEquipos() { return equipos; }
    public void setEquipos(int[] equipos) { this.equipos = equipos; }

    /* ---- Transient (loader-populated) ---- */

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    /** Scoreboard-safe objective name (the id without slashes). */
    public String getNombreObjetivo() {
        return nombreArchivo == null ? null : nombreArchivo.replace("/", "");
    }

    public String getCarpeta() { return carpeta; }
    public void setCarpeta(String carpeta) { this.carpeta = carpeta; }

    /** Raw PokePaste/Showdown team text; each provider parses it into its own Pokémon type. */
    public String getTeamPaste() { return teamPaste; }
    public void setTeamPaste(String teamPaste) { this.teamPaste = teamPaste; }
}
