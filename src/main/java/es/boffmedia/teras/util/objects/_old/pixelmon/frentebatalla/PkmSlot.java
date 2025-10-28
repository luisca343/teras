package es.boffmedia.teras.util.objects._old.pixelmon.frentebatalla;

public class PkmSlot {
    int caja;
    int slot;

    public PkmSlot(int caja, int slot) {
        this.caja = caja;
        this.slot = slot;
    }

    public int getCaja() {
        return caja;
    }

    public void setCaja(int caja) {
        this.caja = caja;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }
}