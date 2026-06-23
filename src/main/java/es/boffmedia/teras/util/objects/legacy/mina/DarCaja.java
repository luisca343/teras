package es.boffmedia.teras.util.objects.legacy.mina;

import es.boffmedia.teras.util.objects.legacy.ObjetoMC;

import java.util.ArrayList;

public class DarCaja {
    private String query;
    private ArrayList<ObjetoMC> objetos;

    public DarCaja(String query, ArrayList<ObjetoMC> objetos) {
        this.query = query;
        this.objetos = objetos;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public ArrayList<ObjetoMC> getObjetos() {
        return objetos;
    }

    public void setObjetos(ArrayList<ObjetoMC> objetos) {
        this.objetos = objetos;
    }
}
