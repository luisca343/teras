package es.boffmedia.teras.pixelmon.battle;

public enum TiposCombate {
    INDIVIDUAL("INDIVIDUAL"),
    DOBLE("DOBLE"),
    TRIPLE("TRIPLE"),
    MULTIPLE("MULTIPLE"),
    TB_INDIVIDUAL("TB_INDIVIDUAL"),
    TB_DOBLE("TB_DOBLE"),
    TB_TRIPLE("TB_TRIPLE"),
    TB_MULTIPLE("TB_MULTIPLE");

    public final String label;

    private TiposCombate(String label) {
        this.label = label;
    }
}
