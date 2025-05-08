package es.boffmedia.teras.util.objects._old.unused;

import java.util.ArrayList;

public class DarObjetos {
    private String query;
    private ArrayList<String> objetos;

    public DarObjetos(String query, ArrayList objetos) {
        this.query = query;
        this.objetos = objetos;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public ArrayList<String> getObjetos() {
        return objetos;
    }

    public void setObjetos(ArrayList<String> objetos) {
        this.objetos = objetos;
    }
}
