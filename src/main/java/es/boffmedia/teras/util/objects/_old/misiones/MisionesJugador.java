package es.boffmedia.teras.util.objects._old.misiones;

import java.util.ArrayList;
import java.util.HashMap;

public class MisionesJugador {
    ArrayList<QuestData> misiones;
    HashMap<String, Integer> categorias;

    public MisionesJugador(){
        this.misiones = new ArrayList<>();
        this.categorias = new HashMap<>();
    }

    public ArrayList<QuestData> getMisiones() {
        return misiones;
    }

    public void setMisiones(ArrayList<QuestData> misiones) {
        this.misiones = misiones;
    }

    public HashMap<String, Integer> getCategorias() {
        return categorias;
    }

    public void setCategorias(HashMap<String, Integer> categorias) {
        this.categorias = categorias;
    }
}
