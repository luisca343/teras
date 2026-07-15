package es.boffmedia.teras.items;

import net.minecraft.item.Food;
import net.minecraft.item.Item;

public class ComidaTeras extends Item {
    private String nombre;
    public ComidaTeras(String nombre, int nutricion, int saturacion) {
        super(new Properties().tab(TerasItemGroup.LIZARDON_GROUP).food(new Food.Builder()
                .nutrition(nutricion)
                .saturationMod(saturacion)
                .build()));
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
}
