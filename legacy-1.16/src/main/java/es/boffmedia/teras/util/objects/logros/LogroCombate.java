package es.boffmedia.teras.util.objects.logros;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import es.boffmedia.teras.model.battle.PokemonData;
import es.boffmedia.teras.util.objects.post.SmartRotomPost;

import java.util.ArrayList;
import java.util.List;

public class LogroCombate extends SmartRotomPost {
    String logro;
    boolean victoria;

    String name1;
    String name2;

    ArrayList<PokemonData> team1;
    ArrayList<PokemonData> team2;

    String replay;

    public LogroCombate() {

    }

    public String getName1() {
        return name1;
    }

    public void setName1(String side1Name) {
        this.name1 = side1Name;
    }

    public String getName2() {
        return name2;
    }

    public void setName2(String side2Name) {
        this.name2 = side2Name;
    }

    public boolean isVictoria() {
        return victoria;
    }

    public void setVictoria(boolean victoria) {
        this.victoria = victoria;
    }

    public ArrayList<PokemonData> getEquipo() {
        return team1;
    }

    public void setEquipo(List<Pokemon> equipo) {
        this.team1 = new ArrayList<>();
        for (Pokemon pokemon : equipo) {
            PokemonData pokemonData = new PokemonData(pokemon);
            this.team1.add(pokemonData);
        }
    }

    public String getLogro() {
        return logro;
    }

    public void setLogro(String logro) {
        this.logro = logro;
    }

    public String getReplay() {
        return replay;
    }

    public void setReplay(String replay) {
        this.replay = replay;
    }

    public ArrayList<PokemonData> getTeam2() {
        return team2;
    }

    public void setTeam2(List<Pokemon> equipo) {
        this.team2 = new ArrayList<>();
        for (Pokemon pokemon : equipo) {
            PokemonData pokemonData = new PokemonData(pokemon);
            this.team2.add(pokemonData);
        }
    }
}

