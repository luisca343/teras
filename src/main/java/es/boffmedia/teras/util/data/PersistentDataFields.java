package es.boffmedia.teras.util.data;

public enum PersistentDataFields {
    FB_ACTIVO("FB_ACTIVO"),
    EQUIPO_ACTIVO("EQUIPO_ACTIVO");

    public final String label;

    private PersistentDataFields(String label) {
        this.label = label;
    }
}

