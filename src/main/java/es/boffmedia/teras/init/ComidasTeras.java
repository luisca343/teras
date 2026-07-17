package es.boffmedia.teras.init;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** The placeable food/drink definitions. Registered by {@link BlockInit}. */
public final class ComidasTeras {
    private ComidasTeras() {}

    public record Comida(String nombre, VoxelShape hitbox, FoodProperties food, UseAnim animacion) {}

    public static final List<Comida> COMIDAS = List.of(
            comida("caca_de_waifu", 2, 2, Block.box(3, 0, 4, 12, 4.25, 13)),
            bebida("sake", 1, 0, Block.box(7, 0, 7, 9, 1, 9)),
            bebida("itamilk", 1, 0, Block.box(5, 0, 5, 11, 12, 10)));

    private static Comida comida(String nombre, int nutricion, float saturacion, VoxelShape hitbox) {
        return new Comida(nombre, hitbox, food(nutricion, saturacion), UseAnim.EAT);
    }

    private static Comida bebida(String nombre, int nutricion, float saturacion, VoxelShape hitbox) {
        return new Comida(nombre, hitbox, food(nutricion, saturacion), UseAnim.DRINK);
    }

    private static FoodProperties food(int nutricion, float saturacion) {
        return new FoodProperties.Builder().nutrition(nutricion).saturationModifier(saturacion).fast().build();
    }
}
