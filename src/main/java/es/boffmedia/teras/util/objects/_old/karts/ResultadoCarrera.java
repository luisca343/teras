package es.boffmedia.teras.util.objects._old.karts;

import java.util.ArrayList;

public class ResultadoCarrera {
    private String circuito;
    private long fecha;

    private ArrayList<ParticipanteCarrera> participantes;

    public String getCircuito() {
        return circuito;
    }

    public void setCircuito(String circuito) {
        this.circuito = circuito;
    }

    public long getFecha() {
        return fecha;
    }

    public void setFecha(long fecha) {
        this.fecha = fecha;
    }

    public ArrayList<ParticipanteCarrera> getParticipantes() {
        return participantes;
    }

    public void setParticipantes(ArrayList<ParticipanteCarrera> participantes) {
        this.participantes = participantes;
    }
}
