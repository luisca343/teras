package es.boffmedia.teras.dex;

/**
 * The {@code status} value {@code /smartrotom/pokemon/register} expects: {@code 0} = seen,
 * {@code 1} = caught.
 */
public enum DexStatus {
    SEEN(0),
    CAUGHT(1);

    private final int wireValue;

    DexStatus(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return wireValue;
    }
}
