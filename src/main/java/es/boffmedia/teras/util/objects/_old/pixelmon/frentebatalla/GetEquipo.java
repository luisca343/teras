package es.boffmedia.teras.util.objects._old.pixelmon.frentebatalla;

import java.util.List;

public class GetEquipo {
    String query;
    List<PkmSlot> equipo;
    String tipo;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<PkmSlot> getEquipo() {
        return equipo;
    }

    public void setEquipo(List<PkmSlot> equipo) {
        this.equipo = equipo;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}