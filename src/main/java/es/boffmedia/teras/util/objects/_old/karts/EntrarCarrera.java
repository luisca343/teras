package es.boffmedia.teras.util.objects._old.karts;

public class EntrarCarrera {
    private String query;
    private String uuid;
    private String circuito;
    private int vueltas;

    public EntrarCarrera(String query, String uuid, String circuito, int vueltas) {
        this.query = query;
        this.uuid = uuid;
        this.circuito = circuito;
        this.vueltas = vueltas;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getCircuito() {
        return circuito;
    }

    public void setCircuito(String circuito) {
        this.circuito = circuito;
    }

    public int getVueltas() {
        return vueltas;
    }

    public void setVueltas(int vueltas) {
        this.vueltas = vueltas;
    }
}
