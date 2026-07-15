package es.boffmedia.teras.battle.model;

/**
 * A single battle reward (item id + quantity, optional NBT), from the config's {@code recompensas} array.
 */
public class Recompensa {
    private String objeto;
    private int cantidad;
    private String nbt;

    public Recompensa(String objeto, int cantidad) {
        this.objeto = objeto;
        this.cantidad = cantidad;
    }

    public Recompensa(String objeto, int cantidad, String nbt) {
        this.objeto = objeto;
        this.cantidad = cantidad;
        this.nbt = nbt;
    }

    public String getObjeto() {
        return objeto;
    }

    public void setObjeto(String objeto) {
        this.objeto = objeto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public String getNbt() {
        return nbt;
    }

    public void setNbt(String nbt) {
        this.nbt = nbt;
    }
}
