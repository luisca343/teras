package es.boffmedia.teras.battle.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One parsed Pokémon from a PokePaste/Pokémon-Showdown export block. Engine-neutral: the Pixelmon
 * provider ignores it (Pixelmon parses the paste with its own importer), while the Cobblemon
 * provider maps these fields onto a {@code PokemonProperties}. Absent lines leave fields null/empty.
 */
public class ShowdownSet {
    public String nickname;
    public String species;
    public String gender;   // "M" | "F" | null
    public boolean shiny;
    public String item;
    public String ability;
    public Integer level;
    public String teraType;
    public String nature;
    public final List<String> moves = new ArrayList<>();
    public final Map<String, Integer> evs = new LinkedHashMap<>();
    public final Map<String, Integer> ivs = new LinkedHashMap<>();

    public boolean hasSpecies() {
        return species != null && !species.isBlank();
    }
}
