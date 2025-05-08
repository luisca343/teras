package es.boffmedia.teras.util.objects._old.unused;

public class QueryUser {
    private String query;
    private String uuid;
    private String nombre;

    public QueryUser(String query, String uuid, String nombre) {
        this.query = query;
        this.uuid = uuid;
        this.nombre = nombre;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
